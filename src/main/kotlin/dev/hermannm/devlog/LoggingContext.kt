package dev.hermannm.devlog

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger {}
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * The field from `withLoggingContext` will then be attached to every log, like:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 *
 * ### Note on coroutines
 *
 * `withLoggingContext` uses a thread-local, so it won't work with Kotlin coroutines and `suspend`
 * functions (though it does work with Java virtual threads). An alternative that supports
 * coroutines may be added in a future version of the library.
 */
inline fun <ReturnT> withLoggingContext(vararg logFields: LogField, block: () -> ReturnT): ReturnT {
  return withLoggingContextInternal(logFields, block)
}

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * This overload of the function takes a list instead of varargs, for when you already have a list
 * of log fields available. This can be used together with [getLoggingContext] to pass context
 * fields between threads (see example in [getLoggingContext] docstring).
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger {}
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * The field from `withLoggingContext` will then be attached to every log, like:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 *
 * ### Note on coroutines
 *
 * `withLoggingContext` uses a thread-local, so it won't work with Kotlin coroutines and `suspend`
 * functions (though it does work with Java virtual threads). An alternative that supports
 * coroutines may be added in a future version of the library.
 */
inline fun <ReturnT> withLoggingContext(logFields: List<LogField>, block: () -> ReturnT): ReturnT {
  return withLoggingContextInternal(logFields.toTypedArray(), block)
}

/**
 * Shared implementation for the `vararg` and `List` versions of [withLoggingContext].
 *
 * This function must be kept internal, since [LoggingContext] assumes that the given array is not
 * modified from the outside. We uphold this invariant in both versions of [withLoggingContext]:
 * - For the `vararg` version: Varargs always give a new array to the called function, even when
 *   called with an existing array:
 *   https://discuss.kotlinlang.org/t/hidden-allocations-when-using-vararg-and-spread-operator/1640/2
 * - For the `List` version: Here we call [Collection.toTypedArray], which creates a new array.
 *
 * We could just call the `vararg` version of [withLoggingContext] from the `List` overload, since
 * you can pass an array to a function taking varargs. But this actually copies the array twice:
 * once in [Collection.toTypedArray], and again in the defensive copy that varargs make in Kotlin.
 */
@PublishedApi
internal inline fun <ReturnT> withLoggingContextInternal(
    logFields: Array<out LogField>,
    block: () -> ReturnT
): ReturnT {
  // Get field count here, so we don't keep the logFields array around longer than necessary
  val fieldCount = logFields.size

  LoggingContext.addFields(logFields)

  try {
    return block()
  } finally {
    LoggingContext.popFields(fieldCount)
  }
}

/**
 * Returns a copy of the log fields in the current thread's logging context (from
 * [withLoggingContext]). This can be used to pass logging context between threads (see example
 * below).
 *
 * If you spawn threads using an [ExecutorService], you may instead use [inheritLoggingContext],
 * which does the logging context copying from parent to child for you.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.getLoggingContext
 * import dev.hermannm.devlog.withLoggingContext
 * import kotlin.concurrent.thread
 *
 * private val log = getLogger {}
 *
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val emailService: EmailService,
 * ) {
 *   fun registerUser(user: User) {
 *     withLoggingContext(field("user", user)) {
 *       userRepository.create(user)
 *       sendWelcomeEmail(user)
 *     }
 *   }
 *
 *   // In this hypothetical, we don't want sendWelcomeEmail to block registerUser, so we spawn a
 *   // thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   // This is where getLoggingContext comes in.
 *   private fun sendWelcomeEmail(user: User) {
 *     // We call getLoggingContext here, to copy the context fields from the parent thread
 *     val loggingContext = getLoggingContext()
 *
 *     thread {
 *       // We then pass the parent context to withLoggingContext here in the child thread
 *       withLoggingContext(loggingContext) {
 *         try {
 *           emailService.sendEmail(to = user.email, content = makeWelcomeEmailContent(user))
 *         } catch (e: Exception) {
 *           // This log will get the "user" field from the parent logging context
 *           log.error {
 *             cause = e
 *             "Failed to send welcome email to user"
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
fun getLoggingContext(): List<LogField> {
  val contextFields = LoggingContext.getFields()

  // If the logging context is empty, we can avoid a list allocation by returning emptyList, which
  // uses a singleton
  if (contextFields.isEmpty()) {
    return emptyList()
  }

  // Create a new list to copy the context fields
  val fieldsCopy = ArrayList<LogField>(contextFields.size)
  contextFields.forEach { fieldsCopy.add(it) }
  return fieldsCopy
}

/**
 * Wraps an [ExecutorService] in a new implementation that copies logging context fields (from
 * [withLoggingContext]) from the parent thread to child threads when spawning new tasks. This is
 * useful when you use an `ExecutorService` in the scope of a logging context, and you want the
 * fields from the logging context to also be included on the logs in the child tasks.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.inheritLoggingContext
 * import dev.hermannm.devlog.withLoggingContext
 * import java.util.concurrent.Executors
 *
 * private val log = getLogger {}
 *
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val emailService: EmailService,
 * ) {
 *   // Call inheritLoggingContext on the executor
 *   private val executor = Executors.newSingleThreadExecutor().inheritLoggingContext()
 *
 *   fun registerUser(user: User) {
 *     withLoggingContext(field("user", user)) {
 *       userRepository.create(user)
 *       sendWelcomeEmail(user)
 *     }
 *   }
 *
 *   // In this hypothetical, we don't want sendWelcomeEmail to block registerUser, so we use an
 *   // ExecutorService to spawn a thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   // This is where inheritLoggingContext comes in.
 *   private fun sendWelcomeEmail(user: User) {
 *     executor.execute {
 *       try {
 *         emailService.sendEmail(to = user.email, content = makeWelcomeEmailContent(user))
 *       } catch (e: Exception) {
 *         // This log will get the "user" field from the parent logging context
 *         log.error {
 *           cause = e
 *           "Failed to send welcome email to user"
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
fun ExecutorService.inheritLoggingContext(): ExecutorService {
  return ExecutorServiceWithInheritedLoggingContext(this)
}

/**
 * Things we want from our logging context:
 * - To store context fields in order from newest to oldest, in an efficient manner. This precludes
 *   the use of [ArrayList], since adding to the beginning of an `ArrayList` involves shifting all
 *   existing elements further back.
 * - To initialize the logging context with an existing array. We want this because
 *   [withLoggingContext] uses a `vararg`, which gives us an [Array]. In the common case of the
 *   logging context being empty, we would like to use that array directly to avoid copying it. This
 *   precludes the use of [ArrayDeque], since that can't be constructed from an existing array
 *   without copying.
 *
 * To achieve these goals, we manage our own array in [LoggingContext.contextFields], and expose
 * [addFields] and [popFields] methods to add/remove fields. See [popFields] docstring for why the
 * elements of the array are nullable.
 */
@PublishedApi
internal object LoggingContext {
  private val contextFields = ThreadLocal<Array<LogField?>>()

  /**
   * Adds the given fields to the thread-local logging context.
   *
   * We have 3 scenarios here:
   * - The logging context is empty: Set the logging context to the given array. See
   *   [withLoggingContextInternal] for why this is safe.
   * - The logging context has available capacity (`null` elements) left from a previous call to
   *   [popFields]: Copy the new fields into the available space.
   * - The logging context is full: Create a new array, copy both the exisiting and new fields into
   *   it, and set the thread-local to it.
   *
   * We use [Array.copyInto] (which uses [System.arraycopy] on JVM) for efficient copying.
   */
  @PublishedApi
  internal fun addFields(newFields: Array<out LogField>) {
    if (newFields.isEmpty()) {
      return
    }

    val currentFields = getFieldArray()
    if (currentFields == null) {
      // This cast is safe, because:
      // - Kotlin's varargs give an Array<out T>, to support passing in subclasses of the type
      // - But LogField is not extensible, so we know there are no subclasses in our case
      // - We also know it is safe to cast an array of LogField to an array of LogField?, since it's
      //   then just an Array<LogField?> where every element happens to not be null
      @Suppress("UNCHECKED_CAST")
      contextFields.set(
          newFields as Array<LogField?>,
      )
      return
    }

    val currentFieldsStartIndex = currentFields.startIndexOfNonNullFields()
    // If there is room available for the new fields at the start of the current array, we use it
    if (newFields.size <= currentFieldsStartIndex) {
      newFields.copyInto(
          currentFields,
          destinationOffset = currentFieldsStartIndex - newFields.size,
      )
      return
    }

    val nonNullCurrentFieldCount = currentFields.size - currentFieldsStartIndex
    val mergedFields: Array<LogField?> = arrayOfNulls(nonNullCurrentFieldCount + newFields.size)
    newFields.copyInto(mergedFields)
    currentFields.copyInto(
        mergedFields,
        destinationOffset = newFields.size,
        startIndex = currentFieldsStartIndex,
    )
    contextFields.set(mergedFields)
  }

  /**
   * Removes the given number of fields from the logging context, starting with the newest ones.
   *
   * If the removed fields were the last ones remaining the logging context, we call
   * [ThreadLocal.remove] to free the array, to avoid memory leaks.
   *
   * If the removed fields were _not_ the last ones remaining, that means we are in a nested
   * [withLoggingContext]:
   * - In this case, we don't want to shrink the [contextFields] array, since that requires a
   *   re-allocation. We'd rather just wait for the outer context to exit and free the array.
   * - So instead, we just set the removed fields to `null` (hence why the elements of
   *   [contextFields] are nullable).
   * - This has the added benefit that [addFields] can re-use these `null` elements if we enter
   *   another [withLoggingContext] before the outer context exits.
   * - Since [popFields] removes elements from the front of the array, only the first elements in
   *   the array will ever be `null` - so when we hit a non-null element in the array, we can assume
   *   that the elements after it are also non-null.
   */
  @PublishedApi
  internal fun popFields(count: Int) {
    if (count == 0) {
      return
    }

    val fields = getFieldArray() ?: return

    val startIndex = fields.startIndexOfNonNullFields()
    // Exclusive index - so we should remove up to, but not including, this index
    val endIndex = startIndex + count

    if (endIndex == fields.size) {
      contextFields.remove()
      return
    }

    for (i in startIndex until endIndex) {
      fields[i] = null
    }
  }

  /**
   * Returns the thread-local context field array. Will be null if we're not currently inside any
   * logging context.
   *
   * This method is only meant for internal use by [LoggingContext] and in tests. To get a more
   * friendly API for working with context fields that deals with nulls for you, call [getFields].
   */
  internal fun getFieldArray(): Array<LogField?>? {
    return contextFields.get()
  }

  internal fun getFields(): ContextFields {
    return ContextFields(getFieldArray())
  }

  /**
   * Utility wrapper around the [LoggingContext.contextFields] array, providing a more ergonomic API
   * that deals with nulls for you.
   */
  @JvmInline
  internal value class ContextFields(private val fields: Array<LogField?>?) {
    internal fun isEmpty(): Boolean = fields.isNullOrEmpty()

    internal val size: Int
      get() {
        if (fields == null) {
          return 0
        }
        return fields.size - fields.startIndexOfNonNullFields()
      }

    internal inline fun forEach(action: (LogField) -> Unit) {
      if (fields == null) {
        return
      }

      for (field in fields) {
        if (field != null) {
          action(field)
        }
      }
    }
  }

  /** Returns null if the logging context fields have not been initialized yet in this thread. */
  internal fun copyFieldArray(): Array<LogField>? {
    val fields = getFieldArray() ?: return null

    val fieldsCopy =
        fields.copyOfRange(
            fromIndex = fields.startIndexOfNonNullFields(),
            toIndex = fields.size,
        )

    /**
     * This cast is safe, because:
     * - As explained in the docstring for [popFields], only the first elements in the field array
     *   will be null. So after we find our first non-null field, all subsequent fields will be
     *   non-null.
     * - Since we use `fromIndex = fields.startIndexOfNonNullFields()` above, we know that
     *   `fieldsCopy` has only non-null fields.
     * - There can never be _only_ null fields in the array, since we reset the array itself to null
     *   when removing the last field in [popFields]. So if the array is not null (which we check
     *   above), there will be at least 1 non-null field.
     */
    @Suppress("UNCHECKED_CAST") return fieldsCopy as Array<LogField>
  }

  private fun Array<LogField?>.startIndexOfNonNullFields(): Int {
    var startIndex = 0
    for (field in this) {
      if (field == null) {
        startIndex++
      } else {
        break
      }
    }
    return startIndex
  }
}

/**
 * Implementation for [inheritLoggingContext]. Wraps the methods on the given [ExecutorService] that
 * take a [Callable]/[Runnable] with [wrapCallable]/[wrapRunnable], which copy the logging context
 * fields from the spawning thread to the spawned tasks.
 *
 * In the implementations of [wrapCallable]/[wrapRunnable], we use [LoggingContext.copyFieldArray]
 * and pass the array directly to [withLoggingContextInternal], instead of using
 * [getLoggingContext]. This is because `getLoggingContext` requires 2 array copies: one for the
 * list returned by that function, and a second defensive copy done by `withLoggingContext`. So we
 * can save an allocation by just making a single array copy.
 */
@JvmInline // Inline value class, since we just wrap another ExecutorService
internal value class ExecutorServiceWithInheritedLoggingContext(
    private val wrappedExecutor: ExecutorService,
) :
    // Use interface delegation here, so we only override the methods we're interested in.
    ExecutorService by wrappedExecutor {

  private fun <T> wrapCallable(callable: Callable<T>): Callable<T> {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Callable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    return when (val contextFields = LoggingContext.copyFieldArray()) {
      // If there are no context fields, we can return the Callable as-is
      null -> callable
      else -> Callable { withLoggingContextInternal(contextFields) { callable.call() } }
    }
  }

  private fun wrapRunnable(runnable: Runnable): Runnable {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Runnable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    return when (val contextFields = LoggingContext.copyFieldArray()) {
      // If there are no context fields, we can return the Runnable as-is
      null -> runnable
      else -> Runnable { withLoggingContextInternal(contextFields) { runnable.run() } }
    }
  }

  override fun execute(command: Runnable) {
    wrappedExecutor.execute(wrapRunnable(command))
  }

  override fun <T : Any?> submit(task: Callable<T>): Future<T> {
    return wrappedExecutor.submit(wrapCallable(task))
  }

  override fun submit(task: Runnable): Future<*> {
    return wrappedExecutor.submit(wrapRunnable(task))
  }

  override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
    return wrappedExecutor.submit(wrapRunnable(task), result)
  }

  override fun <T : Any?> invokeAll(
      tasks: MutableCollection<out Callable<T>>
  ): MutableList<Future<T>> {
    return wrappedExecutor.invokeAll(tasks.map { wrapCallable(it) })
  }

  override fun <T : Any?> invokeAll(
      tasks: MutableCollection<out Callable<T>>,
      timeout: Long,
      unit: TimeUnit
  ): MutableList<Future<T>> {
    return wrappedExecutor.invokeAll(tasks.map { wrapCallable(it) }, timeout, unit)
  }

  override fun <T : Any> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
    return wrappedExecutor.invokeAny(tasks.map { wrapCallable(it) })
  }

  override fun <T : Any?> invokeAny(
      tasks: MutableCollection<out Callable<T>>,
      timeout: Long,
      unit: TimeUnit
  ): T {
    return wrappedExecutor.invokeAny(tasks.map { wrapCallable(it) }, timeout, unit)
  }
}

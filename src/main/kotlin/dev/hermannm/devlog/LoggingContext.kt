package dev.hermannm.devlog

import dev.hermannm.devlog.LoggingContext.JSON_FIELD_VALUE_PREFIX
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.slf4j.MDC

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
  val overwrittenFields = LoggingContext.addFields(logFields)

  try {
    return block()
  } finally {
    LoggingContext.removeFields(logFields, overwrittenFields)
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
  return LoggingContext.getFieldList()
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
 * Thread-local log fields that will be included on every log within a given context.
 *
 * This object encapsulates SLF4J's [MDC] (Mapped Diagnostic Context), allowing the rest of our code
 * to not concern itself with SLF4J-specific APIs.
 */
@PublishedApi
internal object LoggingContext {
  /**
   * SLF4J's MDC only supports String values. This works fine for our [StringLogField] - but we also
   * want the ability to include JSON-serialized objects in our logging context. This is useful when
   * for example processing an event, and you want that event to be included on all logs in the
   * scope of processing it. If we were to just include it as a string, the JSON would be escaped,
   * which prevents log analysis platforms from parsing fields from the event and letting us query
   * on them. What we want is for the [JsonLogField] to be included as actual JSON on the log
   * output, unescaped, to get the benefits of structured logging.
   *
   * To achieve this, we add the raw JSON string from [JsonLogField] to the MDC, but with this
   * prefix. Then, users can configure our [LoggingContextJsonFieldWriter] to strip this prefix and
   * write the field value as raw JSON in the log output. This only works when using Logback with
   * `logstash-logback-encoder`, but that's what this library is primarily designed for anyway.
   */
  internal const val JSON_FIELD_VALUE_PREFIX = "json: "

  /**
   * Returns an array of overwritten field values, with indices matching the given field array.
   * Returns null if no fields were overwritten, so we don't allocate an array for the common case.
   */
  @PublishedApi
  internal fun addFields(fields: Array<out LogField>): Array<String?>? {
    var overwrittenFields: Array<String?>? = null

    for (index in fields.indices) {
      val field = fields[index]

      // Skip duplicate keys in the field array
      if (isDuplicateField(field, index, fields)) {
        continue
      }

      when (val existingValue: String? = MDC.get(field.key)) {
        // If there is no existing entry for our key, we continue down to MDC.put
        null -> {}
        // If the existing value matches the value we're about to insert, we can skip inserting it
        field.value -> continue
        // If there is an existing entry that does not match our field, we have to add it to a list
        // so we can restore it after our withLoggingContext scope
        else -> {
          if (overwrittenFields == null) {
            overwrittenFields = arrayOfNulls(fields.size)
          }
          overwrittenFields[index] = existingValue
        }
      }

      MDC.put(field.key, field.value)
    }

    return overwrittenFields
  }

  /**
   * Takes the array of overwritten field values returned by [addFields], to restore the previous
   * context values after the current context exits.
   */
  @PublishedApi
  internal fun removeFields(fields: Array<out LogField>, overwrittenFields: Array<String?>?) {
    for (index in fields.indices) {
      val field = fields[index]

      // Skip duplicate keys, like we do in addFields
      if (isDuplicateField(field, index, fields)) {
        continue
      }

      if (overwrittenFields != null) {
        val previousValue = overwrittenFields[index]

        if (previousValue != null) {
          MDC.put(field.key, previousValue)
          continue
        }
      }

      MDC.remove(field.key)
    }
  }

  private fun isDuplicateField(field: LogField, index: Int, fields: Array<out LogField>): Boolean {
    for (previousFieldIndex in 0 until index) {
      if (fields[previousFieldIndex].key == field.key) {
        return true
      }
    }
    return false
  }

  internal fun hasKey(key: String): Boolean {
    val existingValue: String? = MDC.get(key)
    return existingValue != null
  }

  internal fun getFieldMap(): Map<String, String?>? {
    return MDC.getCopyOfContextMap()
  }

  internal fun getFieldList(): List<LogField> {
    val fieldMap = getFieldMap()
    if (fieldMap.isNullOrEmpty()) {
      return emptyList()
    }

    val fieldList = ArrayList<LogField>(getNonNullFieldCount(fieldMap))
    mapFieldMapToList(fieldMap, fieldList)
    return fieldList
  }

  internal fun mapFieldMapToList(fieldMap: Map<String, String?>, target: ArrayList<LogField>) {
    for ((key, value) in fieldMap) {
      if (value == null) {
        continue
      }

      target.add(createLogFieldFromContext(key, value))
    }
  }

  internal fun getNonNullFieldCount(fieldMap: Map<String, String?>): Int {
    return fieldMap.count { field -> field.value != null }
  }

  /** Adds the given map of log fields to the logging context for the scope of the given [block]. */
  internal inline fun <ReturnT> withFieldMap(
      fieldMap: Map<String, String?>,
      block: () -> ReturnT
  ): ReturnT {
    val previousFieldMap = getFieldMap()
    if (previousFieldMap != null) {
      MDC.setContextMap(previousFieldMap + fieldMap)
    } else {
      MDC.setContextMap(fieldMap)
    }

    try {
      return block()
    } finally {
      if (previousFieldMap != null) {
        MDC.setContextMap(previousFieldMap)
      } else {
        MDC.clear()
      }
    }
  }
}

internal fun createLogFieldFromContext(key: String, value: String): LogField {
  return if (value.startsWith(JSON_FIELD_VALUE_PREFIX)) {
    JsonLogFieldFromContext(key, value)
  } else {
    StringLogFieldFromContext(key, value)
  }
}

@PublishedApi
internal class StringLogFieldFromContext(
    override val key: String,
    override val value: String,
) : LogField() {
  override fun getValueForLog(): String? {
    // We only want to include fields from the logging context if it's not already in the context
    // (in which case the logger implementation will add the fields from SLF4J's MDC)
    return if (LoggingContext.hasKey(key)) {
      null
    } else {
      value
    }
  }
}

@PublishedApi
internal class JsonLogFieldFromContext(
    override val key: String,
    /**
     * We don't have to add the [LoggingContext.JSON_FIELD_VALUE_PREFIX] here like we do for
     * [JsonLogField], since this is constructed from values that already have the prefix (see
     * [createLogFieldFromContext]).
     */
    override val value: String,
) : LogField() {
  override fun getValueForLog(): PrefixedRawJson? {
    // We only want to include fields from the logging context if it's not already in the context
    // (in which case the logger implementation will add the fields from SLF4J's MDC)
    return if (LoggingContext.hasKey(key)) {
      null
    } else {
      PrefixedRawJson(value)
    }
  }
}

/**
 * Implementation for [inheritLoggingContext]. Wraps the methods on the given [ExecutorService] that
 * take a [Callable]/[Runnable] with [wrapCallable]/[wrapRunnable], which copy the logging context
 * fields from the spawning thread to the spawned tasks.
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
    val contextFields = LoggingContext.getFieldMap()

    if (contextFields.isNullOrEmpty()) {
      return callable
    }

    return Callable { LoggingContext.withFieldMap(contextFields) { callable.call() } }
  }

  private fun wrapRunnable(runnable: Runnable): Runnable {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Runnable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val contextFields = LoggingContext.getFieldMap()

    if (contextFields.isNullOrEmpty()) {
      return runnable
    }

    return Runnable { LoggingContext.withFieldMap(contextFields) { runnable.run() } }
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

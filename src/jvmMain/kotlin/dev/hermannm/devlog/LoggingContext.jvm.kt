@file:JvmName("LoggingContextJvm")

package dev.hermannm.devlog

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.slf4j.MDC

public actual fun getLoggingContext(): LoggingContext {
  return LoggingContext(MDC.getCopyOfContextMap())
}

@JvmInline
public actual value class LoggingContext private constructor(private val platformType: Any?) {
  internal constructor(contextMap: Map<String, String?>?) : this(platformType = contextMap)

  internal val fields: Map<String, String?>?
    get() {
      /**
       * This cast is safe, because:
       * - The primary constructor taking `Any?` is private, so it can only be called in this class
       * - The only thing that invokes the primary constructor is our secondary constructor that
       *   takes `Map<String, String?>?`, so we know that `platformType` is always set to that
       *
       * See the [platformType] docs under `commonMain` for why we have to do this.
       */
      @Suppress("UNCHECKED_CAST")
      return platformType as Map<String, String?>?
    }

  internal fun isEmpty(): Boolean {
    return fields.isNullOrEmpty()
  }
}

internal actual val EMPTY_LOGGING_CONTEXT = LoggingContext(null)

@PublishedApi
internal actual fun addFieldsToLoggingContext(
    fields: Array<out LogField>
): OverwrittenContextFields {
  var overwrittenFields = OverwrittenContextFields(null)

  for (index in fields.indices) {
    val field = fields[index]
    val keyForLoggingContext = field.getKeyForLoggingContext()

    // Skip duplicate keys in the field array
    if (isDuplicateField(field, index, fields)) {
      continue
    }

    val previousValue: String? = MDC.get(keyForLoggingContext)

    MDC.put(keyForLoggingContext, field.value)

    // If there is a previous value for this key, we add it to overwrittenFields so we can restore
    // the previous value after our withLoggingContext scope
    if (previousValue != null) {
      overwrittenFields =
          overwrittenFields.set(index, keyForLoggingContext, previousValue, fields.size)
    } else {
      /**
       * If there was no previous value for the normal context key, then there may still be an entry
       * for an alternate variant of the same key. For example, [JsonLogField] adds a suffix
       * ([LOGGING_CONTEXT_JSON_KEY_SUFFIX]) to identify the value as JSON. But since the suffixed
       * key does not match the non-suffixed key, `MDC.put` above will not overwrite the previous
       * entry if the new field is a different field type. So we have to manually remove the entry
       * for the alternate key in that case.
       */
      val alternateKey =
          // Compare the field key to the logging context key:
          // - If they're the same, then the logging context key does not have a JSON suffix, and we
          //   must check if there's a previous value for the suffixed key
          // - If they differ, then the logging context key has a JSON suffix, and we must check if
          //   there's a previous value for the non-suffixed key
          //
          // We compare by reference here, since `keyForLoggingContext` will return the same String
          // instance if it matches `field.key`.
          if (field.key === keyForLoggingContext) {
            field.key + LOGGING_CONTEXT_JSON_KEY_SUFFIX
          } else {
            field.key
          }

      val previousValue = MDC.get(alternateKey)
      if (previousValue != null) {
        MDC.remove(alternateKey)
        overwrittenFields = overwrittenFields.set(index, alternateKey, previousValue, fields.size)
      }
    }
  }

  return overwrittenFields
}

/**
 * Takes the array of overwritten field values returned by [addFieldsToLoggingContext], to restore
 * the previous context values after the current context exits.
 */
@PublishedApi
internal actual fun removeFieldsFromLoggingContext(
    fields: Array<out LogField>,
    overwrittenFields: OverwrittenContextFields
) {
  fieldLoop@ for (index in fields.indices) {
    val field = fields[index]
    val keyForLoggingContext = field.getKeyForLoggingContext()

    // Skip duplicate keys, like we do in addFields
    if (isDuplicateField(field, index, fields)) {
      continue@fieldLoop
    }

    val overwrittenKey = overwrittenFields.getKey(index)
    if (overwrittenKey != null) {
      MDC.put(overwrittenKey, overwrittenFields.getValue(index))
      /**
       * If the overwritten key matched the current key in the logging context, then we don't want
       * to call `MDC.remove` below (these may not always match - see docstring on `alternateKey` in
       * [addFieldsToLoggingContext] for more on this).
       *
       * We compare by reference here, since `overwrittenKey` will be the same instance as
       * `keyForLoggingContext` if they match.
       */
      if (overwrittenKey === keyForLoggingContext) {
        continue@fieldLoop
      }
    }

    MDC.remove(keyForLoggingContext)
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

@PublishedApi
internal actual fun addExistingContextFieldsToLoggingContext(
    existingContext: LoggingContext
): OverwrittenContextFields {
  var overwrittenFields = OverwrittenContextFields(null)

  val contextFields = existingContext.fields
  if (contextFields == null) {
    return overwrittenFields
  }

  val contextSize = contextFields.size
  if (contextSize == 0) {
    return overwrittenFields
  }

  var index = 0
  for ((key, value) in contextFields) {
    val previousValue = MDC.get(key)

    MDC.put(key, value)

    if (previousValue != null) {
      overwrittenFields = overwrittenFields.set(index, key, previousValue, contextSize)
    } else {
      /**
       * If there was no previous value for the normal context key, then there may still be an entry
       * for an alternate variant of the same key. For example, [JsonLogField] adds a suffix
       * ([LOGGING_CONTEXT_JSON_KEY_SUFFIX]) to identify the value as JSON. But since the suffixed
       * key does not match the non-suffixed key, `MDC.put` above will not overwrite the previous
       * entry if the new field is a different field type. So we have to manually remove the entry
       * for the alternate key in that case.
       */
      val alternateKey =
          // If key has a JSON suffix, then we must check the non-suffixed key.
          // If key does not have a JSON suffix, then we must check the suffixed key.
          if (key.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX)) {
            removeJsonKeySuffix(key)
          } else {
            key + LOGGING_CONTEXT_JSON_KEY_SUFFIX
          }

      val previousValue = MDC.get(alternateKey)
      if (previousValue != null) {
        MDC.remove(alternateKey)
        overwrittenFields = overwrittenFields.set(index, alternateKey, previousValue, contextSize)
      }
    }

    index++
  }

  return overwrittenFields
}

@PublishedApi
internal actual fun removeExistingContextFieldsFromLoggingContext(
    existingContext: LoggingContext,
    overwrittenFields: OverwrittenContextFields
) {
  val contextFields = existingContext.fields
  if (contextFields.isNullOrEmpty()) {
    return
  }

  for ((key, _) in contextFields) {
    val overwrittenValue = overwrittenFields.getValueForKey(key)
    if (overwrittenValue != null) {
      MDC.put(key, overwrittenValue)
    } else {
      MDC.remove(key)
    }
  }
}

/**
 * Gets the value of the overwritten context field with the given key, if any.
 *
 * Matches keys with/without [LOGGING_CONTEXT_JSON_KEY_SUFFIX], to handle the case where a JSON
 * context field overwrote a non-JSON context field, or vice versa. See the docstring on
 * `alternateKey` in [addExistingContextFieldsToLoggingContext] for more on this.
 */
private fun OverwrittenContextFields.getValueForKey(key: String): String? {
  if (this.isEmpty()) {
    return null
  }

  val lengthWithSuffix: Int
  val lengthWithoutSuffix: Int
  if (key.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX)) {
    lengthWithSuffix = key.length
    lengthWithoutSuffix = key.length - LOGGING_CONTEXT_JSON_KEY_SUFFIX.length
  } else {
    lengthWithSuffix = key.length + LOGGING_CONTEXT_JSON_KEY_SUFFIX.length
    lengthWithoutSuffix = key.length
  }

  this.forEachKey { index, otherKey ->
    // Check if the keys match before the JSON key suffix
    if (otherKey.regionMatches(
        thisOffset = 0,
        other = otherKey,
        otherOffset = 0,
        length = lengthWithoutSuffix,
    )) {
      // If the keys matched before the JSON key suffix, then we have a full match if either:
      // - Other key's length is our key's length _without_ suffix (-> other key had no suffix)
      // - Other key's length is our key's length _with_ suffix, and that suffix is what we expect
      if (otherKey.length == lengthWithoutSuffix ||
          (otherKey.length == lengthWithSuffix &&
              otherKey.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX))) {
        return this.getValue(index)
      }
    }
  }

  return null
}

@PublishedApi
internal actual fun addLoggingContextToException(exception: Throwable) {
  if (hasContextForException(exception)) {
    return
  }

  val loggingContext = getLoggingContext()
  if (loggingContext.isEmpty()) {
    return
  }

  val loggingContextProvider = LoggingContextProvider(loggingContext)
  exception.addSuppressed(loggingContextProvider)
}

internal actual fun hasContextForException(exception: Throwable): Boolean {
  traverseExceptionTree(root = exception) { exception ->
    when (exception) {
      is ExceptionWithLoggingContext,
      is LoggingContextProvider -> return true
    }
  }
  return false
}

internal actual fun addContextFieldsToLogEvent(loggingContext: LoggingContext, logEvent: LogEvent) {
  val contextFields = loggingContext.fields
  if (contextFields == null) {
    return
  }

  for ((key, value) in contextFields) {
    if (value == null) {
      continue
    }
    // If we already have an entry in MDC with the same value, then that will be included when the
    // log event is logged, so we don't need to add it as a field here
    if (MDC.get(key) == value) {
      continue
    }

    if (key.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX)) {
      val keyWithoutSuffix = removeJsonKeySuffix(key)
      if (!logEvent.isFieldKeyAdded(keyWithoutSuffix)) {
        logEvent.addJsonField(keyWithoutSuffix, value)
      }
    } else {
      if (!logEvent.isFieldKeyAdded(key)) {
        logEvent.addStringField(key, value)
      }
    }
  }
}

/**
 * Wraps an [ExecutorService] in a new implementation that copies logging context fields (from
 * [withLoggingContext]) from the parent thread to child threads when spawning new tasks. This is
 * useful when you use an `ExecutorService` in the scope of a logging context, and you want the
 * fields from the logging context to also be included on the logs in the child tasks.
 *
 * ### Example
 *
 * Scenario: We store an updated order in a database, and then want to asynchronously update
 * statistics for the order.
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.inheritLoggingContext
 * import dev.hermannm.devlog.withLoggingContext
 * import java.util.concurrent.Executors
 *
 * private val log = getLogger()
 *
 * class OrderService(
 *     private val orderRepository: OrderRepository,
 *     private val statisticsService: StatisticsService,
 * ) {
 *   // Call inheritLoggingContext on the executor
 *   private val executor = Executors.newSingleThreadExecutor().inheritLoggingContext()
 *
 *   fun updateOrder(order: Order) {
 *     withLoggingContext(field("order", order)) {
 *       orderRepository.update(order)
 *       updateStatistics(order)
 *     }
 *   }
 *
 *   // In this scenario, we don't want updateStatistics to block updateOrder, so we use an
 *   // ExecutorService to spawn a thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   // This is where inheritLoggingContext comes in.
 *   private fun updateStatistics(order: Order) {
 *     executor.execute {
 *       try {
 *         statisticsService.orderUpdated(order)
 *       } catch (e: Exception) {
 *         // This log will get the "order" field from the parent logging context
 *         log.error(e) { "Failed to update order statistics" }
 *       }
 *     }
 *   }
 * }
 * ```
 */
public fun ExecutorService.inheritLoggingContext(): ExecutorService {
  return ExecutorServiceWithInheritedLoggingContext(this)
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
    val parentLoggingContext = getLoggingContext()

    if (parentLoggingContext.isEmpty()) {
      return callable
    }

    return Callable { withLoggingContext(parentLoggingContext) { callable.call() } }
  }

  private fun wrapRunnable(runnable: Runnable): Runnable {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Runnable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val parentLoggingContext = getLoggingContext()

    if (parentLoggingContext.isEmpty()) {
      return runnable
    }

    return Runnable { withLoggingContext(parentLoggingContext) { runnable.run() } }
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

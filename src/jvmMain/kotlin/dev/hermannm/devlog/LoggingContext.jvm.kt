@file:JvmName("LoggingContextJvm")

package dev.hermannm.devlog

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.slf4j.MDC
import org.slf4j.event.KeyValuePair

public actual fun getCopyOfLoggingContext(): LoggingContext {
  val contextMap = MDC.getCopyOfContextMap()
  if (contextMap.isNullOrEmpty()) {
    return EMPTY_LOGGING_CONTEXT
  }

  val contextState = getLoggingContextState().copy()

  return LoggingContext(contextMap, contextState)
}

public actual class LoggingContext
internal constructor(
    internal val map: Map<String, String?>?,
    internal val state: LoggingContextState,
) {
  internal fun isEmpty(): Boolean {
    return map.isNullOrEmpty()
  }

  internal actual fun getFields(): Array<out LogField>? {
    if (map.isNullOrEmpty()) {
      return null
    }

    val contextFields: Array<LogField?> = arrayOfNulls(map.size)
    var index = 0
    for ((key, value) in map) {
      val field: LogField =
          if (value == null) {
            LogField(key, JSON_NULL_VALUE, isJson = true)
          } else {
            val isJson = state.isJsonField(key, value)
            LogField(key, value, isJson)
          }

      contextFields[index] = field

      index++
    }

    // Safe to cast here: We initialize `logFields` with `map.size`, and then add a log field for
    // every entry in the map, so all elements in the array will be initialized
    @Suppress("UNCHECKED_CAST")
    return contextFields as Array<LogField>
  }
}

internal actual val EMPTY_LOGGING_CONTEXT =
    LoggingContext(map = null, state = getEmptyLoggingContextState())

@PublishedApi
internal actual fun addFieldsToLoggingContext(fields: Array<out LogField>) {
  var contextState = getLoggingContextState()
  val newFieldCount = fields.size

  for (index in fields.indices) {
    val field = fields[index]
    val key = field.key
    val value = field.value

    // Skip duplicate keys in the field array
    if (isDuplicateField(key, index, fields)) {
      continue
    }

    val previousValue: String? = MDC.get(key)

    MDC.put(key, value)

    contextState = contextState.add(key, value, field.isJson, previousValue, newFieldCount)
  }

  contextState.saveAfterAddingFields()
}

@PublishedApi
internal actual fun removeFieldsFromLoggingContext(fields: Array<out LogField>) {
  val contextState = getLoggingContextState()

  for (index in fields.indices) {
    val field = fields[index]
    val key = field.key

    // Skip duplicate keys, like we do in addFields
    if (isDuplicateField(key, index, fields)) {
      continue
    }

    val overwrittenValue = contextState.popKey(key)
    if (overwrittenValue != null) {
      MDC.put(key, overwrittenValue)
    } else {
      MDC.remove(key)
    }
  }

  contextState.saveAfterRemovingFields()
}

private fun isDuplicateField(key: String, index: Int, fields: Array<out LogField>): Boolean {
  for (previousFieldIndex in 0 until index) {
    if (fields[previousFieldIndex].key == key) {
      return true
    }
  }
  return false
}

@PublishedApi
internal actual fun addExistingContextFieldsToLoggingContext(existingContext: LoggingContext) {
  val existingContextMap = existingContext.map ?: return
  val existingContextSize = existingContextMap.size

  var currentState = getLoggingContextState()

  for ((key, value) in existingContextMap) {
    if (value == null) {
      continue
    }

    val previousValue: String? = MDC.get(key)

    MDC.put(key, value)

    val isJson = existingContext.state.isJsonField(key, value)
    currentState =
        currentState.add(key, value, isJson, previousValue, newFieldCount = existingContextSize)
  }

  currentState.saveAfterAddingFields()
}

@PublishedApi
internal actual fun removeExistingContextFieldsFromLoggingContext(existingContext: LoggingContext) {
  val existingContextMap = existingContext.map ?: return

  val currentContextState = getLoggingContextState()

  for ((key, value) in existingContextMap) {
    // To match `addExistingContextFieldsToLoggingContext`
    if (value == null) {
      continue
    }

    val overwrittenValue = currentContextState.popKey(key)
    if (overwrittenValue != null) {
      MDC.put(key, overwrittenValue)
    } else {
      MDC.remove(key)
    }
  }

  currentContextState.saveAfterRemovingFields()
}

/**
 * We want to avoid duplicate field keys in the log output, and for newer fields to overwrite older
 * fields if they have the same key. We already do this for context fields and for log fields, but
 * separately - so if a log field and a context field have the same key, then they would both appear
 * in the log output. A log field is always more recent than a context field, so in this case, we
 * want the log field to overwrite the context field.
 *
 * This function handles this case: it goes through the given fields on a log event, and checks if
 * any of their keys are already in the logging context. If there's a match, we remove that context
 * field, saving it in [LoggingContextState] to be restored after the log with
 * [restoreContextFieldsOverwrittenForLog].
 *
 * Users of this function should call this before the log, and wrap the log in a try/finally block,
 * calling [restoreContextFieldsOverwrittenForLog] in the `finally` block.
 */
internal fun overwriteDuplicateContextFieldsForLog(logFields: MutableList<KeyValuePair>?) {
  if (logFields == null) {
    return
  }

  val totalFieldCount = logFields.size
  if (totalFieldCount == 0) {
    return
  }

  var contextState = getLoggingContextState()

  var removedFieldCount = 0
  for (index in 0..<totalFieldCount) {
    val field = logFields[index - removedFieldCount]
    val key = field.key

    val contextValue: String = MDC.get(key) ?: continue

    // We use `toString()` here, since the field value when using this library will either be a
    // `String` or a `RawJson` (whose `toString` returns the serialized JSON)
    val fieldValue = field.value.toString()
    if (fieldValue == contextValue) {
      logFields.removeAt(index)
      removedFieldCount++
    } else {
      MDC.remove(key)
      contextState = contextState.storeFieldOverwrittenForLog(key, overwrittenValue = contextValue)
    }
  }

  contextState.saveAfterAddingFields()
}

/** See [overwriteDuplicateContextFieldsForLog]. */
internal fun restoreContextFieldsOverwrittenForLog() {
  val contextState = getLoggingContextState()

  contextState.restoreFieldsOverwrittenForLog { key, overwrittenValue ->
    MDC.put(key, overwrittenValue)
  }

  contextState.saveAfterRemovingFields()
}

/**
 * See [LoggingContextState].
 *
 * We store the array directly here instead of storing a [LoggingContextState] object, because value
 * classes are boxed when used as generics, and we want to avoid such redundant allocations (see
 * [Kotlin docs](https://kotlinlang.org/docs/inline-classes.html#representation) for more on value
 * class boxing).
 *
 * We have to define this in platform-specific modules, because `ThreadLocal` is not multi-platform.
 */
private val THREAD_LOGGING_CONTEXT_STATE = ThreadLocal<Array<String?>?>()

internal actual fun getLoggingContextState(): LoggingContextState {
  return LoggingContextState(
      THREAD_LOGGING_CONTEXT_STATE.get() ?: EMPTY_LOGGING_CONTEXT_STATE_ARRAY,
  )
}

internal actual fun saveLoggingContextStateArray(stateArray: Array<String?>) {
  THREAD_LOGGING_CONTEXT_STATE.set(stateArray)
}

internal actual fun clearLoggingContextState() {
  THREAD_LOGGING_CONTEXT_STATE.remove()
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
    ExecutorService by wrappedExecutor,
    AutoCloseable {

  private fun <T> wrapCallable(callable: Callable<T>): Callable<T> {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Callable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val parentLoggingContext = getCopyOfLoggingContext()

    if (parentLoggingContext.isEmpty()) {
      return callable
    }

    return Callable { withLoggingContext(parentLoggingContext) { callable.call() } }
  }

  private fun wrapRunnable(runnable: Runnable): Runnable {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Runnable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val parentLoggingContext = getCopyOfLoggingContext()

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
      unit: TimeUnit,
  ): MutableList<Future<T>> {
    return wrappedExecutor.invokeAll(tasks.map { wrapCallable(it) }, timeout, unit)
  }

  override fun <T : Any> invokeAny(tasks: MutableCollection<out Callable<T>>): T {
    return wrappedExecutor.invokeAny(tasks.map { wrapCallable(it) })
  }

  override fun <T : Any?> invokeAny(
      tasks: MutableCollection<out Callable<T>>,
      timeout: Long,
      unit: TimeUnit,
  ): T {
    return wrappedExecutor.invokeAny(tasks.map { wrapCallable(it) }, timeout, unit)
  }

  /**
   * As of Java 19, [ExecutorService] implements [AutoCloseable] with a default method. Kotlin's
   * interface delegation can't delegate to Java default methods, so we have to manually override it
   * here. And since we want this library to be compatible with Java versions prior to 19 (for now),
   * we manually implement `AutoCloseable` here and delegate to the wrapped executor if that
   * implements `AutoCloseable`.
   */
  override fun close() {
    (wrappedExecutor as? AutoCloseable)?.close()
  }
}

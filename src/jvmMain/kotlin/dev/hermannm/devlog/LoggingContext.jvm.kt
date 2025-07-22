// `kotlin.jvm` is auto-imported on JVM, but for multiplatform we need to use fully-qualified name
@file:Suppress("RemoveRedundantQualifierName")

package dev.hermannm.devlog

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.slf4j.MDC

/**
 * Thread-local log fields that will be included on every log within a given context.
 *
 * This object encapsulates SLF4J's [MDC] (Mapped Diagnostic Context), allowing the rest of our code
 * to not concern itself with SLF4J-specific APIs.
 */
@PublishedApi
internal actual object LoggingContext {
  @PublishedApi
  @kotlin.jvm.JvmStatic
  internal actual fun addFields(fields: Array<out LogField>): OverwrittenContextFields {
    var overwrittenFields = OverwrittenContextFields(null)

    for (index in fields.indices) {
      val field = fields[index]
      val keyForLoggingContext = field.getKeyForLoggingContext()

      // Skip duplicate keys in the field array
      if (isDuplicateField(field, index, fields)) {
        continue
      }

      var existingValue: String? = MDC.get(field.key)
      when (existingValue) {
        // If there is no existing entry for our key, we continue down to MDC.put
        null -> {}
        // If the existing value matches the value we're about to insert, we can skip inserting it
        field.value -> continue
        // If there is an existing entry that does not match our new field value, we add it to
        // overwrittenFields so we can restore the previous value after our withLoggingContext scope
        else -> {
          overwrittenFields = overwrittenFields.set(index, field.key, existingValue, fields.size)
          /**
           * If we get a [JsonLogField] whose key matches a non-JSON field in the context, then we
           * want to overwrite "key" with "key (json)" (adding [LOGGING_CONTEXT_JSON_KEY_SUFFIX] to
           * identify the JSON value). But since "key (json)" does not match "key", calling
           * `MDC.put` below will not overwrite the previous field, so we have to manually remove it
           * here. The previous field will then be restored by [LoggingContext.removeFields] after
           * the context exits.
           */
          if (field.key != keyForLoggingContext) {
            MDC.remove(field.key)
          }
        }
      }

      /**
       * [JsonLogField] adds a suffix to `keyForLoggingContext`, i.e. it will be different from
       * [LogField.key]. In this case, we want to check existing context field values for both `key`
       * _and_ `keyForLoggingContext`.
       */
      if (field.key != keyForLoggingContext && existingValue == null) {
        existingValue = MDC.get(keyForLoggingContext)
        when (existingValue) {
          null -> {}
          field.value -> continue
          else -> {
            overwrittenFields =
                overwrittenFields.set(index, keyForLoggingContext, existingValue, fields.size)
          }
        }
      }

      MDC.put(keyForLoggingContext, field.value)
    }

    return overwrittenFields
  }

  /**
   * Takes the array of overwritten field values returned by [LoggingContext.addFields], to restore
   * the previous context values after the current context exits.
   */
  @PublishedApi
  @kotlin.jvm.JvmStatic
  internal actual fun removeFields(
      fields: Array<out LogField>,
      overwrittenFields: OverwrittenContextFields
  ) {
    for (index in fields.indices) {
      val field = fields[index]
      val keyForLoggingContext = field.getKeyForLoggingContext()

      // Skip duplicate keys, like we do in addFields
      if (isDuplicateField(field, index, fields)) {
        continue
      }

      val overwrittenKey = overwrittenFields.getKey(index)
      if (overwrittenKey != null) {
        MDC.put(overwrittenKey, overwrittenFields.getValue(index))
        /**
         * If the overwritten key matched the current key in the logging context, then we don't want
         * to call `MDC.remove` below (these may not always match for [JsonLogField] - see docstring
         * over `MDC.remove` in [LoggingContext.addFields]).
         */
        if (overwrittenKey == keyForLoggingContext) {
          continue
        }
      }

      MDC.remove(keyForLoggingContext)
    }
  }

  @kotlin.jvm.JvmStatic
  private fun isDuplicateField(field: LogField, index: Int, fields: Array<out LogField>): Boolean {
    for (previousFieldIndex in 0 until index) {
      if (fields[previousFieldIndex].key == field.key) {
        return true
      }
    }
    return false
  }

  @kotlin.jvm.JvmStatic
  internal actual fun contains(field: LogField): Boolean {
    val existingValue: String? = MDC.get(field.getKeyForLoggingContext())
    return existingValue == field.value
  }

  @kotlin.jvm.JvmStatic
  internal actual fun getFields(): Collection<LogField>? {
    val contextMap = getContextMap()
    if (contextMap == null) {
      return null
    }

    val fieldCount = getNonNullFieldCount(contextMap)
    if (fieldCount == 0) {
      return null
    }

    val fieldList = ArrayList<LogField>(fieldCount)
    addContextMapToList(contextMap, target = fieldList)
    return fieldList
  }

  @kotlin.jvm.JvmStatic
  internal actual fun combineFieldsWithContext(fields: Collection<LogField>): Collection<LogField> {
    val contextMap = getContextMap()
    // If logging context is empty, we just return the given fields, to avoid allocating an
    // additional list
    if (contextMap == null) {
      return fields
    }

    val contextFieldCount = getNonNullFieldCount(contextMap)
    if (contextFieldCount == 0) {
      return fields
    }

    val combinedFields = ArrayList<LogField>(fields.size + contextFieldCount)
    // Add exception log fields first, so they show first in the log output
    combinedFields.addAll(fields)
    addContextMapToList(contextMap, target = combinedFields)
    return combinedFields
  }

  @kotlin.jvm.JvmStatic
  internal fun getContextMap(): Map<String, String?>? {
    return MDC.getCopyOfContextMap()
  }

  @kotlin.jvm.JvmStatic
  private fun addContextMapToList(contextMap: Map<String, String?>, target: ArrayList<LogField>) {
    for ((key, value) in contextMap) {
      if (value == null) {
        continue
      }

      target.add(createLogFieldFromContext(key, value))
    }
  }

  @kotlin.jvm.JvmStatic
  private fun getNonNullFieldCount(contextMap: Map<String, String?>): Int {
    return contextMap.count { field -> field.value != null }
  }

  val exceptionContext = ConcurrentHashMap<WeakExceptionReference, Collection<LogField>>()

  /** See [ExceptionLookupKey]. */
  private val exceptionContextLookup: ConcurrentHashMap<*, Collection<LogField>>
    inline get() = exceptionContext

  private val exceptionReferenceQueue = ReferenceQueue<Throwable>()

  @kotlin.jvm.JvmStatic
  internal actual fun addContextToException(exception: Throwable) {
    cleanupExceptionContext()

    if (hasContextForException(exception)) {
      return
    }

    val loggingContext = LoggingContext.getFields()
    if (loggingContext == null) {
      return
    }

    val exceptionRef = WeakExceptionReference(exception, exceptionReferenceQueue)
    exceptionContext.put(exceptionRef, loggingContext)
  }

  @kotlin.jvm.JvmStatic
  internal actual fun addContextToException(
      exception: Throwable,
      extraFields: Collection<LogField>
  ) {
    cleanupExceptionContext()

    val exceptionRef = WeakExceptionReference(exception, exceptionReferenceQueue)

    val added = exceptionContext.computeIfPresent(exceptionRef) { _, value -> value + extraFields }
    if (added != null) {
      return
    }

    val allLogFields =
        if (hasContextForException(exception)) {
          extraFields
        } else {
          combineFieldsWithContext(extraFields)
        }

    exceptionContext.put(exceptionRef, allLogFields)
  }

  @kotlin.jvm.JvmStatic
  internal actual fun getExceptionContext(exception: Throwable): Collection<LogField>? {
    return exceptionContextLookup[ExceptionLookupKey(exception)]
  }

  @kotlin.jvm.JvmStatic
  internal actual fun cleanupExceptionContext() {
    var exceptionReference: Reference<out Throwable>? = exceptionReferenceQueue.poll()
    while (exceptionReference != null) {
      exceptionContext.remove(exceptionReference)

      exceptionReference = exceptionReferenceQueue.poll()
    }
  }

  private fun hasContextForException(exception: Throwable): Boolean {
    traverseExceptionChain(exception) { exception ->
      if (exception is ExceptionWithLoggingContext ||
          exceptionContextLookup.containsKey(ExceptionLookupKey(exception))) {
        return true
      }
    }
    return false
  }

  class WeakExceptionReference(
      exception: Throwable,
      queue: ReferenceQueue<Throwable>?,
  ) : WeakReference<Throwable>(exception, queue) {
    val exceptionHashCode = System.identityHashCode(exception)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return when (other) {
        is WeakExceptionReference -> this.exceptionHashCode == other.exceptionHashCode
        is ExceptionLookupKey -> this.exceptionHashCode == other.exceptionHashCode
        else -> return false
      }
    }

    override fun hashCode(): Int = exceptionHashCode
  }

  private class ExceptionLookupKey(exception: Throwable) {
    val exceptionHashCode = System.identityHashCode(exception)

    override fun equals(other: Any?): Boolean {
      if (other is WeakExceptionReference) {
        return this.exceptionHashCode == other.exceptionHashCode
      } else {
        return false
      }
    }

    override fun hashCode(): Int = exceptionHashCode
  }
}

/** Adds the given map of log fields to the logging context for the scope of the given [block]. */
internal inline fun <ReturnT> withLoggingContextMap(
    contextMap: Map<String, String?>,
    block: () -> ReturnT
): ReturnT {
  val previousContextMap = LoggingContext.getContextMap()
  if (previousContextMap != null) {
    MDC.setContextMap(previousContextMap + contextMap)
  } else {
    MDC.setContextMap(contextMap)
  }

  try {
    return block()
  } finally {
    if (previousContextMap != null) {
      MDC.setContextMap(previousContextMap)
    } else {
      MDC.clear()
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
@kotlin.jvm.JvmInline // Inline value class, since we just wrap another ExecutorService
internal value class ExecutorServiceWithInheritedLoggingContext(
    private val wrappedExecutor: ExecutorService,
) :
    // Use interface delegation here, so we only override the methods we're interested in.
    ExecutorService by wrappedExecutor {

  private fun <T> wrapCallable(callable: Callable<T>): Callable<T> {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Callable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val contextFields = LoggingContext.getContextMap()

    if (contextFields.isNullOrEmpty()) {
      return callable
    }

    return Callable { withLoggingContextMap(contextFields) { callable.call() } }
  }

  private fun wrapRunnable(runnable: Runnable): Runnable {
    // Copy context fields here, to get the logging context of the parent thread.
    // We then pass this to withLoggingContext in the returned Runnable below, which will be invoked
    // in the child thread, thus inheriting the parent's context fields.
    val contextFields = LoggingContext.getContextMap()

    if (contextFields.isNullOrEmpty()) {
      return runnable
    }

    return Runnable { withLoggingContextMap(contextFields) { runnable.run() } }
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

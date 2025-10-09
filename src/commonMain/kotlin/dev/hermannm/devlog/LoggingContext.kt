// `kotlin.jvm` is auto-imported on JVM, but for multiplatform we need to use fully-qualified name
@file:Suppress("RemoveRedundantQualifierName")
// We use Kotlin Contracts in `withLoggingContext`, for ergonomic use with lambdas. Contracts are
// an experimental feature, but they guarantee binary compatibility, so we can safely use them here
@file:OptIn(ExperimentalContracts::class)

package dev.hermannm.devlog

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * If an exception is thrown from [block], then the given log fields are attached to the exception,
 * and included in the log output when the exception is logged. That way, we don't lose logging
 * context when an exception escapes the context scope.
 *
 * An example of when this is useful is when processing an event, and you want to trace all the logs
 * made in the context of the event. Instead of manually attaching the event ID to each log, you can
 * wrap the event processing in `withLoggingContext`, with the event ID as a log field. All the logs
 * inside that context will then include the event ID as a structured log field, that you can filter
 * on in your log analysis tool.
 *
 * ### Field value encoding with SLF4J
 *
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. But
 * we want to encode object values in the logging context as actual JSON (not escaped strings), so
 * that log analysis tools can parse the fields. If you're using Logback and
 * [`logstash-logback-encoder`](https://github.com/logfellow/logstash-logback-encoder) for JSON
 * output, you can add support for this by configuring
 * `dev.hermannm.devlog.output.logback.JsonContextFieldWriter` as an `mdcEntryWriter`:
 * ```xml
 * <!-- Example Logback config (in src/main/resources/logback.xml) -->
 * <?xml version="1.0" encoding="UTF-8"?>
 * <configuration>
 *   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *       <!-- Writes object values from logging context as actual JSON (not escaped) -->
 *       <mdcEntryWriter class="dev.hermannm.devlog.output.logback.JsonContextFieldWriter"/>
 *     </encoder>
 *   </appender>
 *
 *   <root level="INFO">
 *     <appender-ref ref="STDOUT"/>
 *   </root>
 * </configuration
 * ```
 *
 * This requires that you have added `ch.qos.logback:logback-classic` and
 * `net.logstash.logback:logstash-logback-encoder` as dependencies.
 *
 * ### Note on coroutines
 *
 * SLF4J's `MDC` uses a thread-local, so it won't work by default with Kotlin coroutines and
 * `suspend` functions. If you use coroutines, you can solve this with
 * [`MDCContext` from `kotlinx-coroutines-slf4j`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/).
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger()
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("eventId", event.id)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * If you have configured `dev.hermannm.devlog.output.logback.JsonContextFieldWriter`, the field
 * from `withLoggingContext` will then be added to every log, like this:
 * ```json
 * { "message": "Started processing event", "eventId": "..." }
 * { "message": "Finished processing event", "eventId": "..." }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    vararg logFields: LogField,
    block: () -> ReturnT,
): ReturnT {
  // Allows callers to use `block` as if it were in-place
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  addFieldsToLoggingContext(logFields)
  try {
    return block()
  } catch (e: Exception) {
    addLoggingContextToException(e, logFields)
    throw e
  } finally {
    removeFieldsFromLoggingContext(logFields)
  }
}

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * If an exception is thrown from [block], then the given log fields are attached to the exception,
 * and included in the log output when the exception is logged. That way, we don't lose logging
 * context when an exception escapes the context scope.
 *
 * An example of when this is useful is when processing an event, and you want to trace all the logs
 * made in the context of the event. Instead of manually attaching the event ID to each log, you can
 * wrap the event processing in `withLoggingContext`, with the event ID as a log field. All the logs
 * inside that context will then include the event ID as a structured log field, that you can filter
 * on in your log analysis tool.
 *
 * ### Field value encoding with SLF4J
 *
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. But
 * we want to encode object values in the logging context as actual JSON (not escaped strings), so
 * that log analysis tools can parse the fields. If you're using Logback and
 * [`logstash-logback-encoder`](https://github.com/logfellow/logstash-logback-encoder) for JSON
 * output, you can add support for this by configuring
 * `dev.hermannm.devlog.output.logback.JsonContextFieldWriter` as an `mdcEntryWriter`:
 * ```xml
 * <!-- Example Logback config (in src/main/resources/logback.xml) -->
 * <?xml version="1.0" encoding="UTF-8"?>
 * <configuration>
 *   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder class="net.logstash.logback.encoder.LogstashEncoder">
 *       <!-- Writes object values from logging context as actual JSON (not escaped) -->
 *       <mdcEntryWriter class="dev.hermannm.devlog.output.logback.JsonContextFieldWriter"/>
 *     </encoder>
 *   </appender>
 *
 *   <root level="INFO">
 *     <appender-ref ref="STDOUT"/>
 *   </root>
 * </configuration
 * ```
 *
 * This requires that you have added `ch.qos.logback:logback-classic` and
 * `net.logstash.logback:logstash-logback-encoder` as dependencies.
 *
 * ### Note on coroutines
 *
 * SLF4J's `MDC` uses a thread-local, so it won't work by default with Kotlin coroutines and
 * `suspend` functions. If you use coroutines, you can solve this with
 * [`MDCContext` from `kotlinx-coroutines-slf4j`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/).
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = getLogger()
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("eventId", event.id)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * If you have configured `dev.hermannm.devlog.output.logback.JsonContextFieldWriter`, the field
 * from `withLoggingContext` will then be added to every log, like this:
 * ```json
 * { "message": "Started processing event", "eventId": "..." }
 * { "message": "Finished processing event", "eventId": "..." }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    logFields: Collection<LogField>,
    block: () -> ReturnT,
): ReturnT {
  // Allows callers to use `block` as if it were in-place
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  // The logging context implementation assumes that the field collection isn't mutated. We can't
  // guarantee this for `Collection`, so we must copy it to an array here. We don't need to do this
  // defensive copy for the `vararg` overload, since Kotlin always creates a new array for varargs:
  // https://discuss.kotlinlang.org/t/hidden-allocations-when-using-vararg-and-spread-operator/1640/2
  val fieldArray = logFields.toTypedArray()

  addFieldsToLoggingContext(fieldArray)
  try {
    return block()
  } catch (e: Exception) {
    addLoggingContextToException(e, fieldArray)
    throw e
  } finally {
    removeFieldsFromLoggingContext(fieldArray)
  }
}

/**
 * Adds the fields from an existing logging context to all logs made by a [Logger] in the context of
 * the given [block]. This overload is designed to be used with [getCopyOfLoggingContext], to pass
 * logging context between threads. If you want to add fields to the current thread's logging
 * context, you should instead construct log fields with the [field]/[rawJsonField] functions, and
 * pass them to one of the [withLoggingContext][dev.hermannm.devlog.withLoggingContext] overloads
 * that take [LogField]s.
 *
 * If you spawn threads using a `java.util.concurrent.ExecutorService`, you may instead use the
 * `ExecutorService.inheritLoggingContext` extension function from this library, which passes
 * logging context from parent to child for you.
 *
 * If an exception is thrown from [block], then the given logging context is attached to the
 * exception, and included in the log output when the exception is logged. That way, we don't lose
 * logging context when an exception escapes the context scope.
 *
 * ### Example
 *
 * Scenario: We store an updated order in a database, and then want to asynchronously update
 * statistics for the order.
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getCopyOfLoggingContext
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 * import kotlin.concurrent.thread
 *
 * private val log = getLogger()
 *
 * class OrderService(
 *     private val orderRepository: OrderRepository,
 *     private val statisticsService: StatisticsService,
 * ) {
 *   fun updateOrder(order: Order) {
 *     // This is the default withLoggingContext overload, adding context to the current thread
 *     withLoggingContext(field("order", order)) {
 *       orderRepository.update(order)
 *       updateStatistics(order)
 *     }
 *   }
 *
 *   // In this scenario, we don't want updateStatistics to block updateOrder, so we spawn a thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   private fun updateStatistics(order: Order) {
 *     // We call getCopyOfLoggingContext here, to copy the context fields from the parent thread
 *     val parentContext = getCopyOfLoggingContext()
 *
 *     thread {
 *       // We then pass the parent context to withLoggingContext here in the child thread.
 *       // This uses the overload that takes an existing logging context
 *       withLoggingContext(parentContext) {
 *         try {
 *           statisticsService.orderUpdated(order)
 *         } catch (e: Exception) {
 *           // This log will get the "order" field from the parent logging context
 *           log.error(e) { "Failed to update order statistics" }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    context: LoggingContext,
    block: () -> ReturnT,
): ReturnT {
  // Allows callers to use `block` as if it were in-place
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  addExistingContextFieldsToLoggingContext(context)
  try {
    return block()
  } catch (e: Exception) {
    addExistingLoggingContextToException(e, context)
    throw e
  } finally {
    removeExistingContextFieldsFromLoggingContext(context)
  }
}

/**
 * Returns a copy of the log fields in the current thread's logging context (from
 * [withLoggingContext]). This can be used to pass logging context between threads (see example
 * below).
 *
 * If you spawn threads using a `java.util.concurrent.ExecutorService`, you may instead use the
 * `dev.hermannm.devlog.inheritLoggingContext` extension function, which does the logging context
 * copying from parent to child for you.
 *
 * ### Example
 *
 * Scenario: We store an updated order in a database, and then want to asynchronously update
 * statistics for the order.
 *
 * ```
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getCopyOfLoggingContext
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.withLoggingContext
 * import kotlin.concurrent.thread
 *
 * private val log = getLogger()
 *
 * class OrderService(
 *     private val orderRepository: OrderRepository,
 *     private val statisticsService: StatisticsService,
 * ) {
 *   fun updateOrder(order: Order) {
 *     withLoggingContext(field("order", order)) {
 *       orderRepository.update(order)
 *       updateStatistics(order)
 *     }
 *   }
 *
 *   // In this scenario, we don't want updateStatistics to block updateOrder, so we spawn a thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   // This is where getCopyOfLoggingContext comes in.
 *   private fun updateStatistics(order: Order) {
 *     // We call getCopyOfLoggingContext here, to copy the context fields from the parent thread
 *     val parentContext = getCopyOfLoggingContext()
 *
 *     thread {
 *       // We then pass the parent context to withLoggingContext here in the child thread
 *       withLoggingContext(parentContext) {
 *         try {
 *           statisticsService.orderUpdated(order)
 *         } catch (e: Exception) {
 *           // This log will get the "order" field from the parent logging context
 *           log.error(e) { "Failed to update order statistics" }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
public expect fun getCopyOfLoggingContext(): LoggingContext

/**
 * Wraps a platform-specific representation of the thread-local logging context fields. On the JVM,
 * this uses SLF4J's MDC context map.
 *
 * This type is returned by [getCopyOfLoggingContext], and can be passed to one of the
 * [withLoggingContext] overloads in order to copy logging context between threads.
 */
public expect class LoggingContext {
  internal fun getFields(): Array<out LogField>?
}

/** Static field for the empty logging context, to avoid redundant re-instantiations. */
internal expect val EMPTY_LOGGING_CONTEXT: LoggingContext

@PublishedApi internal expect fun addFieldsToLoggingContext(fields: Array<out LogField>)

@PublishedApi internal expect fun removeFieldsFromLoggingContext(fields: Array<out LogField>)

@PublishedApi
internal expect fun addExistingContextFieldsToLoggingContext(existingContext: LoggingContext)

/** Like [removeFieldsFromLoggingContext], but for [addExistingContextFieldsToLoggingContext]. */
@PublishedApi
internal expect fun removeExistingContextFieldsFromLoggingContext(existingContext: LoggingContext)

@PublishedApi
internal fun addLoggingContextToException(
    exception: Throwable,
    contextFields: Array<out LogField>,
) {
  if (contextFields.isEmpty()) {
    return
  }

  /**
   * If there's already an [ExceptionWithLoggingContext] or [LoggingContextProvider] in the
   * exception tree, we can add these log fields to their existing logging context, instead of
   * creating a new `LoggingContextProvider`.
   */
  traverseExceptionTree(root = exception) { exception ->
    when (exception) {
      is ExceptionWithLoggingContext -> {
        exception.addLoggingContext(contextFields)
        return
      }
      is LoggingContextProvider -> {
        exception.addLoggingContext(contextFields)
        return
      }
    }
  }

  /**
   * If there were no eligible exception to add the context to, we add a suppressed
   * [LoggingContextProvider] - see its docstring for more on how this mechanism works.
   */
  exception.addSuppressed(LoggingContextProvider(contextFields))
}

@PublishedApi
internal fun addExistingLoggingContextToException(
    exception: Throwable,
    existingContext: LoggingContext,
) {
  val contextFields = existingContext.getFields()
  if (contextFields != null) {
    addLoggingContextToException(exception, contextFields)
  }
}

/**
 * In the JVM implementation, we use SLF4J's MDC for logging context. We want to use that instead of
 * managing our own logging context, so that our logging context can be included in logs made by
 * other libraries that use SLF4J but not `devlog-kotlin`.
 *
 * MDC is implemented as a thread-local `HashMap<String, String?>`. This limits what we can put in
 * the logging context to just String values. But in this library, we want to be able to put
 * arbitrary JSON in the logging context, so that the [field] function can have the same interface
 * as [LogBuilder.field]. Serialized JSON is a string, so we can put it in MDC, but the issue then
 * is that the JSON will be escaped in the log output, since our log encoder does not expect MDC
 * values to be JSON.
 *
 * So in order to support JSON field values in the logging context using MDC, we need 2 things:
 * - A way to intercept writing of MDC entries in our log encoder, to write JSON values unescaped.
 *     - `logstash-logback-encoder` (JSON encoder library for Logback) lets us do this, by
 *       implementing the `MdcEntryWriter` interface. We do this in our `JsonContextFieldWriter`
 *       under `jvmMain`.
 *     - The downside is that users must explicitly add `JsonContextFieldWriter` to their Logback
 *       config, but that's not too bad when we document it clearly in the README.
 * - A way to track which logging context values are JSON.
 *     - In a previous version of this library, we did this by adding a suffix to MDC keys that had
 *       JSON values, and then we would check for the suffix in our `JsonContextFieldWriter`. If the
 *       suffix was present, we could then write the value as unescaped JSON, and write the key
 *       without the suffix. There were 3 downsides to this approach:
 *         - We did not want to add this suffix if our users are not using `JsonContextFieldWriter`.
 *           The logic for ensuring that was tricky, and led to the key suffix leaking out into the
 *           logs in certain cases, which was not good.
 *         - Having some MDC keys _with_ a suffix and some without greatly complicated our logic for
 *           overwriting existing logging context entries with the same key, since there were 2
 *           variants of the same context key (suffixed and non-suffixed).
 *         - We had to add this suffix both when adding fields to the context, but also when
 *           checking for fields. This caused quite a lot of allocations from runtime string
 *           concatenation. A goal of this library is to make as few memory allocations as possible,
 *           so this was not ideal.
 *     - This class, `LoggingContextState`, is our new solution to this problem of tracking which
 *       context values are JSON.
 *         - It consists of a thread-local that lives side-by-side with SLF4J's MDC.
 *         - Whenever we add fields to the logging context, we also add to this context state, where
 *           we can track more state than what MDC allows us:
 *             - Firstly, we have a field to mark whether a context value is JSON.
 *             - Secondly, we can track _overwritten_ context values:
 *                 - When a new key is put into MDC, any previous value associated with that key is
 *                   overwritten, and gone.
 *                 - But in our `withLoggingContext` function, we want such overwritten context
 *                   values to be restored when the context scope exits, so that we don't lose outer
 *                   context just because we entered an inner context.
 *                 - To achieve this, we check for previous context values before adding a new field
 *                   in `withLoggingContext`, and we find a previous value, we add it to this
 *                   context state, so that we can restore it after the context scope.
 *
 * Because a logging context scope can live for a while, we want this context state to be as
 * memory-efficient as possible. To do this, we implement it as an array of strings, instead of as
 * an array of objects where we would have to allocate wrapper classes. Each field in the logging
 * context takes up 4 elements in the array, as follows:
 * 1. Key
 * 2. Value
 * 3. Marker for whether the field is JSON (see [LOGGING_CONTEXT_STATE_JSON_FIELD_SENTINEL])
 * 4. Overwritten value from previous logging context, to be restored (`null` if there was none)
 *
 * This lets us store the context state as compactly as possible.
 */
@kotlin.jvm.JvmInline
internal value class LoggingContextState
internal constructor(private val stateArray: Array<String?>) {
  /**
   * Adds a field to the logging context state.
   *
   * If the context field array has not been initialized yet, we initialize it before setting the
   * key/value, and return the new array. It is an error not to use the return value (unfortunately,
   * [Kotlin can't disallow unused return values yet](https://youtrack.jetbrains.com/issue/KT-12719)).
   *
   * Remember to call [saveAfterAddingFields] after this to persist the changes.
   */
  internal fun add(
      key: String,
      value: String?,
      isJson: Boolean,
      overwrittenValue: String?,
      newFieldCount: Int,
  ): LoggingContextState {
    var state = this
    if (newFieldCount <= 0) {
      return state
    }

    var index = state.getAvailableIndex()
    if (index == -1) {
      // When we resize, the next available key index will be the size of the array before resizing
      index = state.stateArray.size
      state = state.resize(newFieldCount)
    }

    state.set(index, key, value, isJson, overwrittenValue)

    return LoggingContextState(state.stateArray)
  }

  /**
   * Removes the latest logging context field with the given key from the context state.
   *
   * Remember to call [saveAfterRemovingFields] after this to persist the changes.
   *
   * @return Previous overwritten value for the given key, if any (i.e., the same value passed as
   *   `overwrittenValue` to [add]).
   */
  internal fun popKey(key: String): String? {
    this.forEachKeyReversed { index, stateKey ->
      if (stateKey == key) {
        val overwrittenValue = this.getOverwrittenValue(index)
        this.remove(index)
        return overwrittenValue
      }
    }

    return null
  }

  /**
   * Saves added fields to the current thread context state.
   *
   * This method must always be called after adding fields to the context, which include:
   * - [add]
   * - [storeFieldOverwrittenForLog]
   *
   * We don't automatically call this at the end of each of these methods, as we typically want to
   * add multiple fields in a row, so we don't need to save to the thread-local every time.
   *
   * See [saveAfterRemovingFields] for why we have two separate save methods.
   */
  internal fun saveAfterAddingFields() {
    saveLoggingContextStateArray(stateArray)
  }

  /**
   * Saves this context state, with fields removed, to the current thread context.
   *
   * This method must always be called after removing fields from the context, which include:
   * - [popKey]
   * - [restoreFieldsOverwrittenForLog]
   *
   * We don't automatically call this at the end of each of these methods, as we typically want to
   * remove multiple fields in a row, so we don't need to save to the thread-local every time.
   *
   * The reason for having a separate method for saving after _removing_ fields, is that we want to
   * check if the state array is empty after removing fields. If it is, we want to allow that memory
   * to be reclaimed, so we clear the thread-local.
   */
  internal fun saveAfterRemovingFields() {
    if (this.isEmpty()) {
      clearLoggingContextState()
    } else {
      saveLoggingContextStateArray(stateArray)
    }
  }

  internal fun isJsonField(key: String, value: String): Boolean {
    this.forEachKeyReversed { index, stateKey ->
      if (stateKey == key) {
        val stateValue = this.getValue(index)
        val isJson = this.isJson(index)
        return isJson && stateValue == value
      }
    }

    return false
  }

  /**
   * See `overwriteDuplicateContextFieldsForLog` in `LoggingContext.jvm.kt` under `jvmMain` for why
   * we use this.
   *
   * If the context field array has not been initialized yet, we initialize it before setting the
   * key/value, and return the new array. It is an error not to use the return value (unfortunately,
   * [Kotlin can't disallow unused return values yet](https://youtrack.jetbrains.com/issue/KT-12719)).
   *
   * Remember to call [saveAfterAddingFields] after this to persist changes.
   */
  internal fun storeFieldOverwrittenForLog(
      key: String,
      overwrittenValue: String,
  ): LoggingContextState {
    return add(
        key = key,
        value = null,
        isJson = false,
        overwrittenValue = overwrittenValue,
        newFieldCount = 1,
    )
  }

  /**
   * See `restoreContextFieldsOverwrittenForLog` in `LoggingContext.jvm.kt` under `jvmMain` for why
   * we use this.
   *
   * Remember to call [saveAfterRemovingFields] after this to persist changes.
   */
  internal inline fun restoreFieldsOverwrittenForLog(
      // `crossinline` because we don't want non-local returns in this lambda, to ensure that we
      // call `state.remove` after the action
      crossinline action: (key: String, overwrittenValue: String) -> Unit
  ) {
    this.forEachKeyReversed { index, key ->
      val value = this.getValue(index)
      // Fields overwritten for log will always be at the end of the array, and since we iterate
      // in reverse, we can stop iterating once we get to a field with non-null value
      if (value != null) {
        return
      }

      val overwrittenValue = this.getOverwrittenValue(index)
      if (overwrittenValue != null) {
        action(key, overwrittenValue)
        this.remove(index)
      }
    }
  }

  internal fun copy(): LoggingContextState {
    if (this.stateArray.isEmpty()) {
      return this
    }

    val emptyFields = this.countEmptyFields()

    val newSize = stateArray.size - emptyFields * LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD
    return LoggingContextState(stateArray.copyOf(newSize = newSize))
  }

  private fun set(
      index: StateKeyIndex,
      key: String?,
      value: String?,
      isJson: Boolean,
      overwrittenValue: String?,
  ) {
    val isJsonValue = if (isJson) LOGGING_CONTEXT_STATE_JSON_FIELD_SENTINEL else null
    stateArray[index] = key
    stateArray[index + 1] = value
    stateArray[index + 2] = isJsonValue
    stateArray[index + 3] = overwrittenValue
  }

  private fun getKey(index: StateKeyIndex): String? {
    return stateArray[index]
  }

  private fun getValue(index: StateKeyIndex): String? {
    return stateArray[index + 1]
  }

  private fun isJson(index: StateKeyIndex): Boolean {
    /**
     * See [LOGGING_CONTEXT_STATE_JSON_FIELD_SENTINEL] for why we use referential equality (`===`)
     * here.
     */
    return stateArray[index + 2] === LOGGING_CONTEXT_STATE_JSON_FIELD_SENTINEL
  }

  private fun getOverwrittenValue(index: StateKeyIndex): String? {
    return stateArray[index + 3]
  }

  private fun remove(index: StateKeyIndex) {
    stateArray.fill(element = null, fromIndex = index, toIndex = index + 3)
  }

  /** Returns -1 if no available index. */
  private fun getAvailableIndex(): StateKeyIndex {
    var availableIndex = -1
    forEachKeyIndexReversed { index ->
      if (getKey(index) == null) {
        availableIndex = index
      } else {
        return availableIndex
      }
    }
    return availableIndex
  }

  private fun resize(newFieldCount: Int): LoggingContextState {
    val newState =
        arrayOfNulls<String?>(
            stateArray.size + newFieldCount * LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD
        )

    stateArray.copyInto(newState)

    return LoggingContextState(newState)
  }

  private fun isEmpty(): Boolean {
    forEachKeyReversed { _, _ ->
      // This function iterates over non-null keys, so if we get a single hit, then we know we're
      // not empty
      return false
    }
    return true
  }

  private fun countEmptyFields(): Int {
    var emptyFields = 0
    forEachKeyIndexReversed { index ->
      if (getKey(index) == null) {
        emptyFields++
      } else {
        return emptyFields
      }
    }
    return emptyFields
  }

  private inline fun forEachKeyReversed(action: (index: StateKeyIndex, key: String) -> Unit) {
    forEachKeyIndexReversed { index ->
      val key = getKey(index)
      if (key != null) {
        action(index, key)
      }
    }
  }

  private inline fun forEachKeyIndexReversed(action: (index: StateKeyIndex) -> Unit) {
    val size = stateArray.size
    if (size < LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD) {
      return
    }

    var index = size - LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD
    while (index >= 0) {
      action(index)

      index -= LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD
    }
  }
}

/**
 * Unfortunately, Kotlin does not at the moment offer a multi-platform `ThreadLocal` abstraction. So
 * we have to define `expect` functions for getting the thread-local [LoggingContextState].
 */
internal expect fun getLoggingContextState(): LoggingContextState

internal expect fun saveLoggingContextStateArray(stateArray: Array<String?>)

internal expect fun clearLoggingContextState()

@kotlin.jvm.JvmField internal val EMPTY_LOGGING_CONTEXT_STATE_ARRAY: Array<String?> = emptyArray()

internal fun getEmptyLoggingContextState(): LoggingContextState {
  return LoggingContextState(EMPTY_LOGGING_CONTEXT_STATE_ARRAY)
}

/** See [LoggingContextState]. */
private const val LOGGING_CONTEXT_STATE_ELEMENTS_PER_FIELD = 4

/**
 * A sentinel value to mark whether a logging context field is JSON. We use a sentinel String object
 * for this instead of a boolean, because we want the [LoggingContextState] array to be an array of
 * Strings (since all the other values in the context state are Strings, and we don't want to do
 * casting).
 *
 * We don't use `const` here, since we want to be 100% sure that we use the same string reference
 * when inserting into the state array as when we check for this value, so we can use faster
 * reference equality (`===`) instead of structural equality (`==`).
 */
@Suppress("MayBeConstant") private val LOGGING_CONTEXT_STATE_JSON_FIELD_SENTINEL: String = "JSON"

/** A valid index for a key in the [LoggingContextState] array. */
private typealias StateKeyIndex = Int

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
 * [block]. Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * An example of when this is useful is when processing an event, and you want to trace all the logs
 * made in the context of the event. Instead of manually attaching the event ID to each log, you can
 * wrap the event processing in `withLoggingContext`, with the event ID as a log field. All the logs
 * inside that context will then include the event ID as a structured log field, that you can filter
 * on in your log analysis tool.
 *
 * ### Field value encoding with SLF4J
 *
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. We
 * want to encode object values in the logging context as actual JSON (not escaped strings), so that
 * log analysis tools can parse the fields. If you're using Logback and
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
 * from `withLoggingContext` will then be attached to every log as follows:
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
 * [block]. Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * An example of when this is useful is when processing an event, and you want to trace all the logs
 * made in the context of the event. Instead of manually attaching the event ID to each log, you can
 * wrap the event processing in `withLoggingContext`, with the event ID as a log field. All the logs
 * inside that context will then include the event ID as a structured log field, that you can filter
 * on in your log analysis tool.
 *
 * ### Field value encoding with SLF4J
 *
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. We
 * want to encode object values in the logging context as actual JSON (not escaped strings), so that
 * log analysis tools can parse the fields. If you're using Logback and
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
 * from `withLoggingContext` will then be attached to every log as follows:
 * ```json
 * { "message": "Started processing event", "eventId": "..." }
 * { "message": "Finished processing event", "eventId": "..." }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    logFields: Collection<LogField>,
    block: () -> ReturnT
): ReturnT {
  // Allows callers to use `block` as if it were in-place
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  // The logging context implementation assumes that the field collection isn't mutated. We can't
  // guarantee for `Collection`, so we must copy it to an array here. We don't need to do this
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
 * Applies the fields from the given logging context to all logs made by a [Logger] in the context
 * of the given [block].
 *
 * This overload of [withLoggingContext][dev.hermannm.devlog.withLoggingContext] is designed to be
 * used with [getCopyOfLoggingContext], to pass logging context between threads. If you want to add
 * fields to the current thread's logging context, you should instead construct log fields with the
 * [field]/[rawJsonField] functions, and pass them to one of the
 * [withLoggingContext][dev.hermannm.devlog.withLoggingContext] overloads that take [LogField]s.
 *
 * If you spawn threads using a `java.util.concurrent.ExecutorService`, you may instead use the
 * `dev.hermannm.devlog.inheritLoggingContext` extension function, which passes logging context from
 * parent to child for you.
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
    existingContext: LoggingContext,
    block: () -> ReturnT
): ReturnT {
  // Allows callers to use `block` as if it were in-place
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

  addExistingContextFieldsToLoggingContext(existingContext)
  try {
    return block()
  } catch (e: Exception) {
    addExistingLoggingContextToException(e, existingContext)
    throw e
  } finally {
    removeExistingContextFieldsFromLoggingContext(existingContext)
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
  internal fun toLogFields(): Array<out LogField>?
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
internal fun addLoggingContextToException(exception: Throwable, logFields: Array<out LogField>) {
  traverseExceptionTree(root = exception) { exception ->
    when (exception) {
      is ExceptionWithLoggingContext -> {
        exception.addLoggingContext(logFields)
        return
      }
      is LoggingContextProvider -> {
        exception.addLoggingContext(logFields)
        return
      }
    }
  }

  exception.addSuppressed(LoggingContextProvider(logFields))
}

@PublishedApi
internal fun addExistingLoggingContextToException(
    exception: Throwable,
    existingContext: LoggingContext
) {
  val logFields = existingContext.toLogFields()
  if (logFields != null) {
    addLoggingContextToException(exception, logFields)
  }
}

@JvmInline
internal value class LoggingContextState(private val stateArray: Array<String?>?) {
  internal fun add(
      key: String,
      value: String?,
      isJson: Boolean,
      overwrittenValue: String?,
      newFieldCount: Int,
  ): LoggingContextState {
    if (newFieldCount <= 0) {
      return this
    }

    var state = getOrInitializeState(newFieldCount)
    var index = state.getAvailableIndex()
    if (index == -1) {
      index = state.stateArray.size
      state = state.resize(newFieldCount)
    }

    state.set(index, key, value, isJson, overwrittenValue)

    return LoggingContextState(state.stateArray)
  }

  /**
   * @return Previous overwritten value for the given key, if any (i.e., the same value passed as
   *   `overwrittenValue` to [add]).
   */
  internal fun popKey(key: String): String? {
    if (stateArray == null) {
      return null
    }

    val state = InitializedState(stateArray)
    state.forEachKeyReversed { index, stateKey ->
      if (stateKey == key) {
        val overwrittenValue = state.getOverwrittenValue(index)
        state.remove(index)
        return overwrittenValue
      }
    }

    return null
  }

  internal fun isJsonField(key: String, value: String): Boolean {
    if (stateArray == null) {
      return false
    }

    val state = InitializedState(stateArray)
    state.forEachKeyReversed { index, stateKey ->
      if (stateKey == key) {
        val stateValue = state.getValue(index)
        val isJson = state.isJson(index)
        return isJson && stateValue == value
      }
    }

    return false
  }

  internal fun storeFieldOverwrittenForLog(
      key: String,
      overwrittenValue: String
  ): LoggingContextState {
    return add(
        key = key,
        value = null,
        isJson = false,
        overwrittenValue = overwrittenValue,
        newFieldCount = 1,
    )
  }

  internal inline fun restoreFieldsOverwrittenForLog(
      crossinline action: (key: String, overwrittenValue: String) -> Unit
  ) {
    if (stateArray == null) {
      return
    }

    val state = InitializedState(stateArray)
    state.forEachKeyReversed { index, key ->
      val value = state.getValue(index)
      // Fields overwritten for log will always be at the end of the array, and since we iterate
      // in reverse, we can stop iterating once we get to a field with non-null value
      if (value != null) {
        return
      }

      val overwrittenValue = state.getOverwrittenValue(index)
      if (overwrittenValue != null) {
        action(key, overwrittenValue)
        state.remove(index)
      }
    }
  }

  private fun getOrInitializeState(newFieldCount: Int): InitializedState {
    return if (stateArray != null) {
      InitializedState(stateArray)
    } else {
      InitializedState(arrayOfNulls(size = newFieldCount * ELEMENTS_PER_FIELD))
    }
  }

  @JvmInline
  internal value class InitializedState(val stateArray: Array<String?>) {
    internal fun set(
        index: StateKeyIndex,
        key: String?,
        value: String?,
        isJson: Boolean,
        overwrittenValue: String?,
    ) {
      stateArray[index] = key
      stateArray[index + 1] = value
      if (isJson) {
        stateArray[index + 2] = JSON_FIELD_SENTINEL
      }
      stateArray[index + 3] = overwrittenValue
    }

    internal fun getKey(index: StateKeyIndex): String? {
      return stateArray[index]
    }

    internal fun getValue(index: StateKeyIndex): String? {
      return stateArray[index + 1]
    }

    internal fun isJson(index: StateKeyIndex): Boolean {
      // We use referential equality here (===), because we use `JSON_FIELD_SENTINEL` as a unique
      // object to mark JSON context fields.
      return stateArray[index + 2] === JSON_FIELD_SENTINEL
    }

    internal fun getOverwrittenValue(index: StateKeyIndex): String? {
      return stateArray[index + 3]
    }

    internal fun remove(index: StateKeyIndex) {
      stateArray.fill(element = null, fromIndex = index, toIndex = index + 3)
    }

    /** Returns -1 if no available index. */
    internal fun getAvailableIndex(): StateKeyIndex {
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

    internal fun resize(newFieldCount: Int): InitializedState {
      val newState = arrayOfNulls<String?>(stateArray.size + newFieldCount * ELEMENTS_PER_FIELD)

      stateArray.copyInto(newState)

      return InitializedState(newState)
    }

    internal fun isEmpty(): Boolean {
      forEachKeyReversed { _, _ ->
        return false
      }
      return true
    }

    internal fun countEmptyFields(): Int {
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

    internal inline fun forEachKeyReversed(action: (index: StateKeyIndex, key: String) -> Unit) {
      forEachKeyIndexReversed { index ->
        val key = getKey(index)
        if (key != null) {
          action(index, key)
        }
      }
    }

    internal inline fun forEachKeyIndexReversed(action: (index: StateKeyIndex) -> Unit) {
      val size = stateArray.size
      if (size < ELEMENTS_PER_FIELD) {
        return
      }

      var index = size - ELEMENTS_PER_FIELD
      while (index >= 0) {
        action(index)

        index -= ELEMENTS_PER_FIELD
      }
    }
  }

  internal fun copy(): LoggingContextState {
    if (stateArray == null) {
      return this
    }

    val emptyFields = InitializedState(stateArray).countEmptyFields()

    val newSize = stateArray.size - emptyFields * ELEMENTS_PER_FIELD
    return LoggingContextState(stateArray.copyOf(newSize = newSize))
  }

  internal fun save() {
    if (stateArray == null || InitializedState(stateArray).isEmpty()) {
      STATE.set(null)
    } else {
      STATE.set(stateArray)
    }
  }

  internal companion object {
    private val STATE = ThreadLocal<Array<String?>?>()

    internal fun get(): LoggingContextState {
      return LoggingContextState(STATE.get())
    }

    private const val ELEMENTS_PER_FIELD = 4

    /**
     * A sentinel value (unique object) to mark whether a logging context field value is JSON.
     *
     * We call [kotlin.String] explicitly here, because we want to make sure that we create an
     * actual unique instance of the `String` class for the sentinel value, using the zero-parameter
     * `String` constructor. If we just called `String()`, then that may in the future call a
     * pseudo-constructor from [kotlin.text.String] (which are also in the global scope). Since
     * those pseudo-constructors are just functions, they don't guarantee that they return a unique
     * instance.
     */
    @Suppress("RemoveRedundantQualifierName") private val JSON_FIELD_SENTINEL = kotlin.String()
  }
}

internal typealias StateKeyIndex = Int

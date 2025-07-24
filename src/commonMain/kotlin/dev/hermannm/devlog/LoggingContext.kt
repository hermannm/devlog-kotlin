// `kotlin.jvm` is auto-imported on JVM, but for multiplatform we need to use fully-qualified name
@file:Suppress("RemoveRedundantQualifierName")

package dev.hermannm.devlog

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
  return withLoggingContextInternal(logFields, block)
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
  return withLoggingContextInternal(logFields.toTypedArray(), block)
}

/**
 * Shared implementation for the `vararg` and `Collection` versions of [withLoggingContext].
 *
 * This function must be kept internal, since our logging context functions assumes that the given
 * array is not modified from the outside. We uphold this invariant in both versions of
 * [withLoggingContext]:
 * - For the `vararg` version: Varargs always give a new array to the called function, even when
 *   called with an existing array:
 *   https://discuss.kotlinlang.org/t/hidden-allocations-when-using-vararg-and-spread-operator/1640/2
 * - For the `Collection` version: Here we call [Collection.toTypedArray], which creates a new
 *   array.
 *
 * We could just call the `vararg` version of [withLoggingContext] from the `Collection` overload,
 * since you can pass an array to a function taking varargs. But this actually copies the array
 * twice: once in [Collection.toTypedArray], and again in the defensive copy that varargs make in
 * Kotlin.
 */
@PublishedApi
internal inline fun <ReturnT> withLoggingContextInternal(
    logFields: Array<out LogField>,
    block: () -> ReturnT,
): ReturnT {
  val overwrittenFields = addFieldsToLoggingContext(logFields)
  try {
    return block()
  } catch (e: Exception) {
    addLoggingContextToException(e)
    throw e
  } finally {
    removeFieldsFromLoggingContext(logFields, overwrittenFields)
  }
}

public inline fun <ReturnT> withLoggingContext(
    existingContext: LoggingContext,
    block: () -> ReturnT
): ReturnT {
  val overwrittenFields = addExistingContextFieldsToLoggingContext(existingContext)
  try {
    return block()
  } catch (e: Exception) {
    addLoggingContextToException(e)
    throw e
  } finally {
    removeExistingContextFieldsFromLoggingContext(existingContext, overwrittenFields)
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
 * import dev.hermannm.devlog.getLogger
 * import dev.hermannm.devlog.getLoggingContext
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
 *   // This is where getLoggingContext comes in.
 *   private fun updateStatistics(order: Order) {
 *     // We call getLoggingContext here, to copy the context fields from the parent thread
 *     val loggingContext = getLoggingContext()
 *
 *     thread {
 *       // We then pass the parent context to withLoggingContext here in the child thread
 *       withLoggingContext(loggingContext) {
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
public expect fun getLoggingContext(): LoggingContext

/**
 * Wraps a platform-specific representation of the thread-local logging context. On the JVM, this
 * uses SLF4J's MDC context map.
 *
 * This type is returned by [getLoggingContext], and can be passed to one of the
 * `withLoggingContext` overloads in order to copy logging context between threads.
 */
@kotlin.jvm.JvmInline
public expect value class LoggingContext
private constructor(
    /**
     * The platform-specific representation of the logging context. On the JVM, this is a
     * `Map<String, String?>?`, which is the type used by SLF4J's MDC.
     *
     * We would prefer to use a per-platform typealias for this, but unfortunately Kotlin doesn't
     * allow `actual` typealiases for generic types. So we get around this by marking the
     * constructor `private`, and providing type-safe constructors and accessors in the platform
     * implementation.
     */
    private val platformType: Any?,
)

/** Static field for the empty logging context, to avoid redundant re-instantiations. */
internal expect val EMPTY_LOGGING_CONTEXT: LoggingContext

@PublishedApi
internal expect fun addFieldsToLoggingContext(fields: Array<out LogField>): OverwrittenContextFields

/**
 * Takes the array of overwritten field values returned by [addFieldsToLoggingContext], to restore
 * the previous context values after the current context exits.
 */
@PublishedApi
internal expect fun removeFieldsFromLoggingContext(
    fields: Array<out LogField>,
    overwrittenFields: OverwrittenContextFields
)

@PublishedApi
internal expect fun addExistingContextFieldsToLoggingContext(
    existingContext: LoggingContext
): OverwrittenContextFields

/** Like [removeFieldsFromLoggingContext], but for [addExistingContextFieldsToLoggingContext]. */
@PublishedApi
internal expect fun removeExistingContextFieldsFromLoggingContext(
    existingContext: LoggingContext,
    overwrittenFields: OverwrittenContextFields
)

@PublishedApi internal expect fun addLoggingContextToException(exception: Throwable)

internal expect fun getExceptionLoggingContext(exception: Throwable): LoggingContext

internal expect fun hasContextForException(exception: Throwable): Boolean

internal expect fun addContextFieldsToLogEvent(loggingContext: LoggingContext, logEvent: LogEvent)

internal expect fun cleanupExceptionLoggingContext()

/**
 * Fields (key/value pairs) that were overwritten by [addFieldsToLoggingContext], passed to
 * [removeFieldsFromLoggingContext] so we can restore the previous field values after the current
 * logging context exits.
 *
 * We want this object to be as efficient as possible, since it will be kept around for the whole
 * span of a [withLoggingContext] scope, which may last a while. To support this goal, we:
 * - Use an array, to store the fields as compactly as possible
 *     - We store key/values inline, alternating - so an initialized array will look like:
 *       `key1-value1-key2-value2`
 *     - We initialize the array to twice the size of the current logging context fields, since we
 *       store 2 elements (key/value) for every field in the current context. This is not a concern,
 *       since there will typically be few elements in the context, and storing `null`s in the array
 *       does not take up much space.
 * - Initialize the array to `null`, so we don't allocate anything for the common case of there
 *   being no overwritten fields
 * - Use an inline value class, so we don't allocate a redundant wrapper object
 *     - To avoid the array being boxed, we always use this object as its concrete type. We also
 *       make the `fields` array nullable instead of the outer object, as making the outer object
 *       nullable boxes it (like `Int` and `Int?`). See
 *       [Kotlin docs](https://kotlinlang.org/docs/inline-classes.html#representation) for more on
 *       when inline value classes are boxed.
 */
@kotlin.jvm.JvmInline
internal value class OverwrittenContextFields(private val fields: Array<String?>?) {
  /**
   * If the overwritten context field array has not been initialized yet, we initialize it before
   * setting the key/value, and return the new array. It is an error not to use the return value
   * (unfortunately,
   * [Kotlin can't disallow unused return values yet](https://youtrack.jetbrains.com/issue/KT-12719)).
   */
  internal fun set(
      index: Int,
      key: String,
      value: String,
      totalFields: Int
  ): OverwrittenContextFields {
    val fields = this.fields ?: arrayOfNulls(totalFields * 2)
    fields[index * 2] = key
    fields[index * 2 + 1] = value
    return OverwrittenContextFields(fields)
  }

  internal fun getKey(index: Int): String? {
    return fields?.get(index * 2)
  }

  internal fun getValue(index: Int): String? {
    return fields?.get(index * 2 + 1)
  }

  internal fun isEmpty(): Boolean {
    return fields == null
  }

  internal inline fun forEachKey(action: (index: Int, key: String) -> Unit) {
    if (fields == null) {
      return
    }

    var index = 0
    while (index < fields.size) {
      val key = fields[index]
      if (key != null) {
        // Divide the index by 2, since the fact that we store keys and values contiguously here is
        // an implementation detail
        action(index / 2, key)
      }

      index += 2
    }
  }
}

package dev.hermannm.devlog

import kotlin.concurrent.Volatile
import kotlin.jvm.JvmInline

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. To
 * encode object values as actual JSON (not escaped strings), you can use
 * `dev.hermannm.devlog.LoggingContextJsonFieldWriter` with Logback.
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
 * If you have configured `dev.hermannm.devlog.LoggingContextJsonFieldWriter`, the field from
 * `withLoggingContext` will then be attached to every log as follows:
 * ```json
 * { "message": "Started processing event", "event": { ... } }
 * { "message": "Finished processing event", "event": { ... } }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    vararg logFields: LogField,
    block: () -> ReturnT
): ReturnT {
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
 * The JVM implementation uses `MDC` from SLF4J, which only supports String values by default. To
 * encode object values as actual JSON (not escaped strings), you can configure
 * `dev.hermannm.devlog.LoggingContextJsonFieldWriter`.
 *
 * This overload of the function takes a list instead of varargs, for when you already have a list
 * of log fields available. This can be used together with [getLoggingContext] to pass context
 * fields between threads ([see example][getLoggingContext]).
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
 * If you have configured `dev.hermannm.devlog.LoggingContextJsonFieldWriter`, the field from
 * `withLoggingContext` will then be attached to every log as follows:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 */
public inline fun <ReturnT> withLoggingContext(
    logFields: List<LogField>,
    block: () -> ReturnT
): ReturnT {
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
 * If you spawn threads using an `java.util.concurrent.ExecutorService`, you may instead use the
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
 * private val log = getLogger {}
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
public fun getLoggingContext(): List<LogField> {
  return LoggingContext.getFieldList()
}

/**
 * Thread-local log fields that will be included on every log within a given context.
 *
 * On the JVM, this object encapsulates SLF4J's `MDC` (Mapped Diagnostic Context), allowing the rest
 * of our code to not concern itself with SLF4J-specific APIs.
 */
@PublishedApi
internal expect object LoggingContext {
  @PublishedApi internal fun addFields(fields: Array<out LogField>): OverwrittenContextFields

  /**
   * Takes the array of overwritten field values returned by [LoggingContext.addFields], to restore
   * the previous context values after the current context exits.
   */
  @PublishedApi
  internal fun removeFields(
      fields: Array<out LogField>,
      overwrittenFields: OverwrittenContextFields
  )

  internal fun hasKey(@Suppress("unused") key: String): Boolean

  internal fun getFieldList(): List<LogField>

  /** Combines the given log fields with any fields from [withLoggingContext]. */
  internal fun combineFieldListWithContextFields(
      @Suppress("unused") fields: List<LogField>
  ): List<LogField>
}

/**
 * Fields (key/value pairs) that were overwritten by [LoggingContext.addFields], passed to
 * [LoggingContext.removeFields] so we can restore the previous field values after the current
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
@JvmInline
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
}

internal fun createLogFieldFromContext(key: String, value: String): LogField {
  return if (ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS &&
      key.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX)) {
    JsonLogFieldFromContext(key, value)
  } else {
    StringLogFieldFromContext(key, value)
  }
}

@PublishedApi
internal class StringLogFieldFromContext(key: String, value: String) : StringLogField(key, value) {
  /**
   * We only want to include fields from the logging context if it's not already in the context (in
   * which case the logger implementation will add the fields from SLF4J's MDC).
   */
  override fun includeInLog(): Boolean = !LoggingContext.hasKey(key)
}

@PublishedApi
internal class JsonLogFieldFromContext(
    /**
     * We construct this log field with keys that already have the JSON key suffix (see
     * [createLogFieldFromContext]). So we set [keyForLoggingContext] to the key with the suffix,
     * and remove the suffix for [key] below.
     */
    keyWithJsonSuffix: String,
    value: String,
) :
    JsonLogField(
        key = keyWithJsonSuffix.removeSuffix(LOGGING_CONTEXT_JSON_KEY_SUFFIX),
        value = value,
        keyForLoggingContext = keyWithJsonSuffix,
    ) {
  /**
   * We only want to include fields from the logging context if it's not already in the context (in
   * which case the logger implementation will add the fields from SLF4J's MDC).
   */
  override fun includeInLog(): Boolean = !LoggingContext.hasKey(key)
}

/**
 * SLF4J's MDC only supports String values. This works fine for our [StringLogField] - but we also
 * want the ability to include JSON-serialized objects in our logging context. This is useful when
 * for example processing an event, and you want that event to be included on all logs in the scope
 * of processing it. If we were to just include it as a string, the JSON would be escaped, which
 * prevents log analysis platforms from parsing fields from the event and letting us query on them.
 * What we want is for the [JsonLogField] to be included as actual JSON on the log output,
 * unescaped, to get the benefits of structured logging.
 *
 * To achieve this, we add the raw JSON string from [JsonLogField] to the MDC, but with this suffix
 * added to the key. Then, users can configure our
 * `dev.hermannm.devlog.LoggingContextJsonFieldWriter` to strip this suffix from the key and write
 * the field value as raw JSON in the log output. This only works when using Logback with
 * `logstash-logback-encoder`, but that's what this library is primarily designed for anyway.
 *
 * We add a suffix to the field key instead of the field value, since the field value may be
 * external input, which would open us up to malicious actors breaking our logs by passing invalid
 * JSON strings with the appropriate prefix/suffix.
 *
 * This specific suffix was chosen to reduce the chance of clashing with other keys - MDC keys
 * typically don't have spaces/parentheses.
 */
internal const val LOGGING_CONTEXT_JSON_KEY_SUFFIX = " (json)"

/**
 * We only want to add [LOGGING_CONTEXT_JSON_KEY_SUFFIX] to context field keys if the user has
 * configured `dev.hermannm.devlog.LoggingContextJsonFieldWriter` with `logstash-logback-encoder`.
 * If this is not the case, we don't want to add the key suffix, as that will show up in the log
 * output.
 *
 * So to check this, we use this global boolean (volatile for thread-safety), defaulting to false.
 * If `LoggingContextJsonFieldWriter` is configured, its constructor will run when Logback is
 * initialized, and set this to true. Then we can check this value in [JsonLogField], to decide
 * whether or not to add the JSON key suffix.
 *
 * One obstacle with this approach is that we need Logback to be loaded before checking this field.
 * The user may construct a [JsonLogField] before loading Logback, in which case
 * `LoggingContextJsonFieldWriter`'s constructor will not have run yet, and we will omit the key
 * suffix when it should have been added. So to ensure that Logback is loaded before checking this
 * field, we call [ensureLoggerImplementationIsLoaded] from an `init` block on
 * [JsonLogField.Companion], which will run when the class is loaded. We test that this works in the
 * `LogbackLoggerTest` under `integration-tests/logback`.
 */
@Volatile internal var ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS = false

/** See [ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS]. */
internal expect fun ensureLoggerImplementationIsLoaded()

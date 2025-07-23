@file:JvmName("LogFieldJvm")

package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory as Slf4jLoggerFactory

@PublishedApi
internal actual open class JsonLogField
internal constructor(
    key: String,
    value: String,
    private val keyForLoggingContext: String,
) : LogField(key, value) {
  actual constructor(
      key: String,
      value: String,
  ) : this(
      key,
      value,
      keyForLoggingContext =
          if (ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS) {
            key + LOGGING_CONTEXT_JSON_KEY_SUFFIX
          } else {
            key
          },
  )

  actual override fun addToLogEvent(logEvent: LogEvent) {
    logEvent.addJsonField(key, value)
  }

  actual final override fun getKeyForLoggingContext(): String = keyForLoggingContext

  internal companion object {
    init {
      try {
        /**
         * Needed to make sure [ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS] is set.
         *
         * We catch all throwables here, since this is called in a static initializer where we never
         * wanna throw, and it's OK for this to fail silently.
         */
        ensureLoggerImplementationIsLoaded()
      } catch (_: Throwable) {}
    }
  }
}

internal fun createLogFieldFromContext(key: String, value: String): LogField {
  if (ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS && key.endsWith(LOGGING_CONTEXT_JSON_KEY_SUFFIX)) {
    return JsonLogFieldFromContext(key, value)
  } else {
    return StringLogFieldFromContext(key, value)
  }
}

internal class StringLogFieldFromContext(key: String, value: String) : StringLogField(key, value) {
  override fun addToLogEvent(logEvent: LogEvent) {
    // We only want to include fields from the logging context if it's not already in the context
    // (in which case the logger implementation will add the fields from SLF4J's MDC).
    if (!isFieldInLoggingContext(this)) {
      logEvent.addStringField(key, value)
    }
  }
}

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
  override fun addToLogEvent(logEvent: LogEvent) {
    // We only want to include fields from the logging context if it's not already in the context
    // (in which case the logger implementation will add the fields from SLF4J's MDC).
    if (!isFieldInLoggingContext(this)) {
      logEvent.addJsonField(key, value)
    }
  }
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
 * `dev.hermannm.devlog.output.logback.JsonContextFieldWriter` to strip this suffix from the key and
 * write the field value as raw JSON in the log output. This only works when using Logback with
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
 * configured `dev.hermannm.devlog.output.logback.JsonContextFieldWriter` with
 * `logstash-logback-encoder`. If this is not the case, we don't want to add the key suffix, as that
 * will show up in the log output.
 *
 * So to check this, we use this global boolean (volatile for thread-safety), defaulting to false.
 * If `JsonContextFieldWriter` is configured, its constructor will run when Logback is initialized,
 * and set this to true. Then we can check this value in [JsonLogField], to decide whether or not to
 * add the JSON key suffix.
 *
 * One obstacle with this approach is that we need Logback to be loaded before checking this field.
 * The user may construct a [JsonLogField] before loading Logback, in which case
 * `JsonContextFieldWriter`'s constructor will not have run yet, and we will omit the key suffix
 * when it should have been added. So to ensure that Logback is loaded before checking this field,
 * we call [ensureLoggerImplementationIsLoaded] from an `init` block on [JsonLogField.Companion],
 * which will run when the class is loaded. We test that this works in the `LogbackLoggerTest` under
 * `integration-tests/logback`.
 */
@kotlin.concurrent.Volatile @JvmField internal var ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS = false

/** See [ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS]. */
private fun ensureLoggerImplementationIsLoaded() {
  // This will initialize the SLF4J logger implementation, if not already initialized
  Slf4jLoggerFactory.getILoggerFactory()
}

@PublishedApi
internal actual fun fieldValueShouldUseToString(value: Any): Boolean {
  return when (value) {
    is Instant,
    is UUID,
    is URI,
    is URL,
    is BigDecimal -> true
    else -> false
  }
}

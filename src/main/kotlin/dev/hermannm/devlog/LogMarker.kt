package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.logstash.logback.marker.ObjectAppendingMarker
import net.logstash.logback.marker.RawJsonAppendingMarker
import net.logstash.logback.marker.SingleFieldAppendingMarker

/**
 * A log marker is a key-value pair to add structured context to a log event.
 *
 * If you output logs as JSON (e.g. using
 * [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)), each marker
 * will be its own field in the resulting JSON. This allows you to filter and query on these fields
 * in the log analysis tool of your choice, in a more structured manner than if you were to just use
 * string concatenation.
 *
 * You can construct a log marker using the [marker] function, which will serialize the value using
 * `kotlinx.serialization`. Alternatively, if you have a value that is already serialized, you can
 * use [rawJsonMarker]. You can then pass the marker to the various logging methods on [Logger].
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.marker
 * import kotlinx.serialization.Serializable
 *
 * private val log = Logger {}
 *
 * fun example() {
 *   val user = User(id = 1, name = "John Doe")
 *
 *   log.info("Registered new user", marker("user", user))
 * }
 *
 * @Serializable data class User(val id: Long, val name: String)
 * ```
 *
 * This would give the following output using `logstash-logback-encoder`:
 * ```json
 * {
 *   "message": "Registered new user",
 *   "user": {
 *     "id": "1",
 *     "name": "John Doe"
 *   },
 *   // ...timestamp etc.
 * }
 * ```
 */
class LogMarker
@PublishedApi // For use in inline functions
internal constructor(
    internal val logstashMarker: SingleFieldAppendingMarker,
) {
  @PublishedApi // For use in inline functions
  // For now, we don't make this public, as we don't necessarily want to bind ourselves to this API
  internal val key
    get() = logstashMarker.fieldName

  // We override toString, equals and hashCode manually here instead of using a data class, since we
  // don't want the data class copy/componentN methods to be part of our API.
  override fun toString() = logstashMarker.toString()

  override fun equals(other: Any?) =
      other is LogMarker && other.logstashMarker == this.logstashMarker

  override fun hashCode() = logstashMarker.hashCode()
}

/**
 * Constructs a [LogMarker], a key-value pair to add structured context to a log event. You can pass
 * the marker to the various logging methods on [Logger].
 *
 * The value is serialized using `kotlinx.serialization`, so if you pass an object here, you should
 * make sure it is annotated with [@Serializable][kotlinx.serialization.Serializable].
 * Alternatively, you can pass your own [serializer] for the value. If serialization fails, we fall
 * back to calling `toString()` on the value.
 *
 * If you have a value that is already serialized, you should use [rawJsonMarker] instead.
 *
 * Certain types that `kotlinx.serialization` doesn't support natively have special-case handling
 * here, using their `toString()` representation instead:
 * - [java.time.Instant]
 * - [java.util.UUID]
 * - [java.net.URI]
 * - [java.net.URL]
 * - [java.math.BigDecimal]
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.marker
 * import kotlinx.serialization.Serializable
 *
 * private val log = Logger {}
 *
 * fun example() {
 *   val user = User(id = 1, name = "John Doe")
 *
 *   log.info("Registered new user", marker("user", user))
 * }
 *
 * @Serializable data class User(val id: Long, val name: String)
 * ```
 *
 * This would give the following output using `logstash-logback-encoder`:
 * ```json
 * {
 *   "message": "Registered new user",
 *   "user": {
 *     "id": 1,
 *     "name": "John Doe"
 *   },
 *   // ...timestamp etc.
 * }
 * ```
 */
inline fun <reified ValueT> marker(
    key: String,
    value: ValueT,
    serializer: SerializationStrategy<ValueT>? = null
): LogMarker {
  try {
    if (serializer != null) {
      val serializedValue = logMarkerJson.encodeToString(serializer, value)
      return LogMarker(RawJsonAppendingMarker(key, serializedValue))
    }

    val logstashMarker =
        when (ValueT::class) {
          // Special case for String to avoid redundant serialization
          String::class -> ObjectAppendingMarker(key, value)
          // Special cases for common types that kotlinx.serialization doesn't handle by default.
          // If more cases are added here, you should also add them to the list in the docstring.
          Instant::class,
          UUID::class,
          URI::class,
          URL::class,
          BigDecimal::class -> ObjectAppendingMarker(key, value.toString())
          else -> {
            val serializedValue = logMarkerJson.encodeToString(value)
            RawJsonAppendingMarker(key, serializedValue)
          }
        }

    return LogMarker(logstashMarker)
  } catch (_: Exception) {
    // We don't want to ever throw an exception from constructing a marker, which may happen if
    // serialization fails, for example. So in those cases we fall back to toString().
    return LogMarker(ObjectAppendingMarker(key, value.toString()))
  }
}

/**
 * Constructs a [LogMarker], a key-value pair to add structured context to a log event, with the
 * given pre-serialized JSON string. You can pass the marker to the various logging methods on
 * [Logger].
 *
 * By default, this function checks that the given JSON string is actually valid JSON. The reason
 * for this is that giving raw JSON to our log encoder when it is not in fact valid JSON can break
 * our logs. So if the given JSON string is not valid JSON, we escape it as a string. If you are
 * 100% sure that the given JSON string is valid, you can set [validJson] to true.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.rawJsonMarker
 *
 * private val log = Logger {}
 *
 * fun example() {
 *   val userJson = """{"id":1,"name":"John Doe"}"""
 *
 *   log.info("Registered new user", rawJsonMarker("user", userJson))
 * }
 * ```
 *
 * This would give the following output using `logstash-logback-encoder`:
 * ```json
 * {
 *   "message": "Registered new user",
 *   "user": {
 *     "id": 1,
 *     "name": "John Doe"
 *   },
 *   // ...timestamp etc.
 * }
 * ```
 */
fun rawJsonMarker(key: String, json: String, validJson: Boolean = false): LogMarker {
  try {
    // Some log platforms (e.g. AWS CloudWatch) use newlines as the separator between log messages.
    // So if the JSON string has unescaped newlines, we must re-parse the JSON.
    val containsNewlines = json.contains('\n')

    // If we assume the JSON is valid, and there are no unescaped newlines, we can return it as-is.
    if (validJson && !containsNewlines) {
      return LogMarker(RawJsonAppendingMarker(key, json))
    }

    // If we do not assume that the JSON is valid, we must try to decode it.
    val decoded = logMarkerJson.parseToJsonElement(json)

    // If we successfully decoded the JSON, and it does not contain unescaped newlines, we can
    // return it as-is.
    if (!containsNewlines) {
      return LogMarker(RawJsonAppendingMarker(key, json))
    }

    // If the JSON did contain unescaped newlines, then we need to re-encode to escape them.
    val encoded = logMarkerJson.encodeToString(JsonElement.serializer(), decoded)
    return LogMarker(RawJsonAppendingMarker(key, encoded))
  } catch (_: Exception) {
    // If we failed to decode/re-encode the JSON string, we return it as a non-JSON string.
    return LogMarker(ObjectAppendingMarker(key, json))
  }
}

@PublishedApi // For use in inline functions
internal val logMarkerJson = Json { encodeDefaults = true }

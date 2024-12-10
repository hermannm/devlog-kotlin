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

class LogMarker
@PublishedApi // PublishedApi so we can use the constructor in the inline `marker` function.
internal constructor(
    internal val logstashMarker: SingleFieldAppendingMarker,
) {
  @PublishedApi // PublishedApi so we can use this in the inline `withLoggingContext` function.
  // For now, we don't make this public, as we don't necessarily want to bind ourselves to this API.
  internal val key
    get() = logstashMarker.fieldName

  // We override toString, equals and hashCode manually here instead of using a data class, since we
  // don't want the data class copy/componentN methods to be part of our API.
  override fun toString() = logstashMarker.toString()

  override fun equals(other: Any?) =
      other is LogMarker && other.logstashMarker == this.logstashMarker

  override fun hashCode() = logstashMarker.hashCode()
}

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
          // Special cases for common types that kotlinx.serialization doesn't handle by default
          Instant::class,
          URI::class,
          URL::class,
          UUID::class,
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

@PublishedApi // PublishedApi so we can use this in the inline `marker` function.
internal val logMarkerJson = Json { encodeDefaults = true }

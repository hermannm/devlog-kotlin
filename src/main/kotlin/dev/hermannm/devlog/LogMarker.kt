package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.logstash.logback.marker.Markers
import org.slf4j.Marker as Slf4jMarker

class LogMarker
@PublishedApi // PublishedApi so we can use the constructor in the inline `marker` function.
internal constructor(
    internal val slf4jMarker: Slf4jMarker,
) {
  // We override toString, equals and hashCode manually here instead of using a data class, since we
  // don't want the data class copy/componentN methods to be part of our API.
  override fun toString() = slf4jMarker.toString()

  override fun equals(other: Any?) = other is LogMarker && other.slf4jMarker == this.slf4jMarker

  override fun hashCode() = slf4jMarker.hashCode()
}

inline fun <reified ValueT> marker(name: String, value: ValueT): LogMarker {
  val slf4jMarker =
      try {
        when (ValueT::class) {
          // Special case for String to avoid redundant serialization
          String::class -> Markers.append(name, value)
          // Special cases for common types that kotlinx.serialization doesn't handle by default
          Instant::class,
          URI::class,
          URL::class,
          UUID::class,
          BigDecimal::class -> Markers.append(name, value.toString())
          else -> {
            val serializedValue = markerJson.encodeToString(value)
            Markers.appendRaw(name, serializedValue)
          }
        }
      } catch (_: Exception) {
        // We don't want to ever throw an exception from constructing a marker, which may happen if
        // serialization fails, for example. So in those cases we fall back to toString().
        Markers.append(name, value.toString())
      }

  return LogMarker(slf4jMarker)
}

fun rawMarker(name: String, json: String, validJson: Boolean = false): LogMarker {
  try {
    // Some log platforms (e.g. AWS CloudWatch) use newlines as the separator between log messages.
    // So if the JSON string has unescaped newlines, we must re-parse the JSON.
    val containsNewlines = json.contains('\n')

    // If we assume the JSON is valid, and there are no unescaped newlines, we can return it as-is.
    if (validJson && !containsNewlines) {
      return LogMarker(Markers.appendRaw(name, json))
    }

    // If we do not assume that the JSON is valid, we must try to decode it.
    val decoded = markerJson.parseToJsonElement(json)

    // If we successfully decoded the JSON, and it does not contain unescaped newlines, we can
    // return it as-is.
    if (!containsNewlines) {
      return LogMarker(Markers.appendRaw(name, json))
    }

    // If the JSON did contain unescaped newlines, then we need to re-encode to escape them.
    val encoded = markerJson.encodeToString(JsonElement.serializer(), decoded)
    return LogMarker(Markers.appendRaw(name, encoded))
  } catch (_: Exception) {
    // If we failed to decode/re-encode the JSON string, we return it as a non-JSON string.
    return LogMarker(Markers.append(name, json))
  }
}

@PublishedApi // PublishedApi so we can use this in the inline `marker` function.
internal val markerJson = Json { encodeDefaults = true }

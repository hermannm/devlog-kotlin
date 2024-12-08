package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        Markers.append("name", value.toString())
      }

  return LogMarker(slf4jMarker)
}

@PublishedApi // PublishedApi so we can use this in the inline `marker` function.
internal val markerJson = Json { encodeDefaults = true }

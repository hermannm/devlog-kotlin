package dev.hermannm.devlog

import ch.qos.logback.classic.spi.LoggingEvent as LogbackEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import kotlinx.serialization.SerializationStrategy
import net.logstash.logback.marker.SingleFieldAppendingMarker

/** Class used in the logging methods on [Logger] to add markers/cause exception to logs. */
@JvmInline // Inline value class, since we just wrap a Logback logging event
value class LogBuilder
internal constructor(
    @PublishedApi internal val logEvent: LogbackEvent,
) {
  /** Set this if the log was caused by an exception. */
  var cause: Throwable?
    set(value) = logEvent.setThrowableProxy(ThrowableProxy(value))
    get() = (logEvent.throwableProxy as? ThrowableProxy)?.throwable

  /**
   * Adds a [log marker][LogMarker] with the given key and value to the log.
   *
   * The value is serialized using `kotlinx.serialization`, so if you pass an object here, you
   * should make sure it is annotated with [@Serializable][kotlinx.serialization.Serializable].
   * Alternatively, you can pass your own serializer for the value. If serialization fails, we fall
   * back to calling `toString()` on the value.
   *
   * If you have a value that is already serialized, you should use [addRawJsonMarker] instead.
   */
  inline fun <reified ValueT> addMarker(
      key: String,
      value: ValueT,
      serializer: SerializationStrategy<ValueT>? = null,
  ) {
    if (!markerKeyAdded(key)) {
      logEvent.addMarker(createLogstashMarker(key, value, serializer))
    }
  }

  /**
   * Adds a [log marker][LogMarker] with the given key and pre-serialized JSON value to the log.
   *
   * By default, this function checks that the given JSON string is actually valid JSON. The reason
   * for this is that giving raw JSON to our log encoder when it is not in fact valid JSON can break
   * our logs. So if the given JSON string is not valid JSON, we escape it as a string. If you are
   * 100% sure that the given JSON string is valid, you can set [validJson] to true.
   */
  fun addRawJsonMarker(key: String, json: String, validJson: Boolean = false) {
    if (!markerKeyAdded(key)) {
      logEvent.addMarker(createRawJsonLogstashMarker(key, json, validJson))
    }
  }

  private fun getMarkers(): List<SingleFieldAppendingMarker> {
    // We know this cast is safe, since we only ever add markers of type SingleFieldAppendingMarker
    @Suppress("UNCHECKED_CAST")
    return (logEvent.markerList as List<SingleFieldAppendingMarker>?) ?: emptyList()
  }

  @PublishedApi
  internal fun markerKeyAdded(key: String): Boolean {
    return getMarkers().any { existingMarker -> existingMarker.fieldName == key }
  }

  internal fun addMarkersFromContextAndCause() {
    val exceptionMarkers = getLogMarkersFromException(cause)
    val contextMarkers = getLogMarkersFromContext()

    exceptionMarkers.forEach { marker ->
      // Don't add marker keys that have already been added
      if (!markerKeyAdded(marker.logstashMarker.fieldName)) {
        logEvent.addMarker(marker.logstashMarker)
      }
    }

    // Add context markers in reverse, so newest marker shows first
    contextMarkers.forEachReversed { logstashMarker ->
      if (!markerKeyAdded(logstashMarker.fieldName)) {
        logEvent.addMarker(logstashMarker)
      }
    }
  }
}

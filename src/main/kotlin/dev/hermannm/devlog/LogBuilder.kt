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
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.Logger
   * import kotlinx.serialization.Serializable
   *
   * private val log = Logger {}
   *
   * fun example() {
   *   val user = User(id = 1, name = "John Doe")
   *
   *   log.info {
   *     addMarker("user", user)
   *     "Registered new user"
   *   }
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
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.Logger
   *
   * private val log = Logger {}
   *
   * fun example() {
   *   val userJson = """{"id":1,"name":"John Doe"}"""
   *
   *   log.info {
   *     addRawJsonMarker("user", userJson)
   *     "Registered new user"
   *   }
   * }
   * ```
   *
   * This would give the following output using `logstash-logback-encoder`:
   * ```json
   * {"message":"Registered new user","user":{"id":1,"name":"John Doe"},/* ...timestamp etc. */}
   * ```
   */
  fun addRawJsonMarker(key: String, json: String, validJson: Boolean = false) {
    if (!markerKeyAdded(key)) {
      logEvent.addMarker(createRawJsonLogstashMarker(key, json, validJson))
    }
  }

  /** Adds log markers from [withLoggingContext]. */
  internal fun addMarkersFromContext() {
    // loggingContext will be null if withLoggingContext has not been called in this thread
    val contextMarkers = loggingContext.get() ?: return

    // Add context markers in reverse, so newest marker shows first
    contextMarkers.forEachReversed { logstashMarker ->
      // Don't add marker keys that have already been added
      if (!markerKeyAdded(logstashMarker.fieldName)) {
        logEvent.addMarker(logstashMarker)
      }
    }
  }

  /**
   * Checks if the log [cause] exception (or any of its own cause exceptions) implements the
   * [WithLogMarkers] interface, and if so, adds those markers.
   */
  internal fun addMarkersFromCauseException() {
    // The `cause` here is the log event cause exception. But this exception may itself have a
    // `cause` exception, and that may have another one, and so on. We want to go through all these
    // exceptions to look for log markers, so we re-assign this local variable as we iterate
    // through.
    var exception = cause
    while (exception != null) {
      if (exception is WithLogMarkers) {
        exception.logMarkers.forEach { marker ->
          // Don't add marker keys that have already been added
          if (!markerKeyAdded(marker.logstashMarker.fieldName)) {
            logEvent.addMarker(marker.logstashMarker)
          }
        }
      }

      // Avoid infinite loop from cyclic cause exceptions
      if (exception.cause === exception) {
        break
      }

      exception = exception.cause
    }
  }

  @PublishedApi
  internal fun markerKeyAdded(key: String): Boolean {
    // We know this cast is safe, since we only ever add markers of type SingleFieldAppendingMarker
    // (see createLogstashMarker and createRawJsonLogstashMarker)
    @Suppress("UNCHECKED_CAST")
    val markers = (logEvent.markerList as List<SingleFieldAppendingMarker>?) ?: emptyList()

    return markers.any { existingMarker -> existingMarker.fieldName == key }
  }
}

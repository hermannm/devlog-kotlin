package dev.hermannm.devlog

import kotlinx.serialization.SerializationStrategy

/** Class used in the logging methods on [Logger] to add markers/cause exception to logs. */
class LogBuilder
@PublishedApi // For use in inline functions
internal constructor() {
  /** Set this if the log was caused by an exception. */
  var cause: Throwable? = null

  @PublishedApi // For use in inline functions
  internal var markers: ArrayList<LogMarker>? = null

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
    getInitializedMarkers().add(marker(key, value, serializer = serializer))
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
    getInitializedMarkers().add(rawJsonMarker(key, json, validJson = validJson))
  }

  @PublishedApi // For use in inline functions
  internal fun getInitializedMarkers(): ArrayList<LogMarker> {
    if (markers == null) {
      markers = ArrayList()
    }
    // We never set this to null again after initializing above, so this should be safe
    return markers!!
  }
}

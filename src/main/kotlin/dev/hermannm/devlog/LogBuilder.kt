package dev.hermannm.devlog

import kotlinx.serialization.SerializationStrategy

/**
 * Class used in the lazy logging methods on [Logger] to add markers/cause exception to lazily
 * constructed logs.
 */
class LogBuilder
@PublishedApi // For use in inline functions
internal constructor() {
  @PublishedApi // For use in inline functions
  // Can't call this `cause`, as that clashes with `setCause`
  internal var causeException: Throwable? = null

  // Initialize to null so we don't allocate a list when no markers are included (common case).
  @PublishedApi // For use in inline functions
  internal var markers: ArrayList<LogMarker>? = null

  /** Sets the given exception as the cause of the log. */
  fun setCause(cause: Throwable) {
    this.causeException = cause
  }

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

  /**
   * Adds the given [log marker][LogMarker] to the log.
   *
   * This method is kept internal for now, as it is useful for testing. If we find a need from
   * library users to have a function like this, we should consider making it public.
   */
  internal fun addExistingMarker(marker: LogMarker) {
    getInitializedMarkers().add(marker)
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

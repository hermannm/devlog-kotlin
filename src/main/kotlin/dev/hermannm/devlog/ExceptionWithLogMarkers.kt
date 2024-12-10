package dev.hermannm.devlog

/**
 * Interface to allow you to attach [log markers][LogMarker] to exceptions. When passing a `cause`
 * exception to one of the methods on [Logger], it will check if the given exception implements this
 * interface, and if it does, these log markers will be attached to the log.
 *
 * This is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the log of
 * the exception. In this case, one may resort to string concatenation, but this interface allows
 * you to have the benefits of structured logging for exceptions as well.
 */
interface WithLogMarkers {
  /** Will be attached to a log if this is passed through a `cause` parameter to [Logger]. */
  val logMarkers: List<LogMarker>
}

/**
 * Base exception class implementing the [WithLogMarkers] interface for attaching structured data to
 * the exception when it's logged. If you don't want to create a custom exception and implement
 * [WithLogMarkers] on it, you can use this class instead.
 */
open class ExceptionWithLogMarkers(
    override val message: String?,
    override val logMarkers: List<LogMarker>,
    override val cause: Throwable? = null,
) : RuntimeException(), WithLogMarkers

/**
 * Checks if the given exception (or any of its cause exceptions) implements the [WithLogMarkers]
 * interface, and if so, returns those markers.
 */
internal fun getLogMarkersFromException(exception: Throwable?): List<LogMarker> {
  var e = exception // Redefine as var so we can re-assign to cause below
  var markers: List<LogMarker> = emptyList()
  while (e != null) {
    if (e is WithLogMarkers) {
      if (markers.isEmpty()) {
        markers = e.logMarkers
      } else {
        // We want markers to be a List, not a MutableList, so we don't have to allocate a new list
        // for the common case of there just being 1 exception with log markers
        @Suppress("SuspiciousCollectionReassignment")
        markers += e.logMarkers
      }
    }

    e = e.cause
  }

  return markers
}

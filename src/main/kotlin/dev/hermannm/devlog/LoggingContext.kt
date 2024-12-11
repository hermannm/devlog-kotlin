package dev.hermannm.devlog

import kotlin.concurrent.getOrSet

@PublishedApi // For use in inline functions
internal val loggingContext = ThreadLocal<ArrayList<LogMarker>>()

/**
 * Adds the given [log markers][LogMarker] to a thread-local list, which is added to every log made
 * by a [Logger] in the context of the given [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log marker, and
 * then all logs inside that context will include the event.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.marker
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = Logger {}
 *
 * fun example(event: Event) {
 *   withLoggingContext(
 *       marker("event", event),
 *   ) {
 *     log.debug("Started processing event")
 *     // ...
 *     log.debug("Finished processing event")
 *   }
 * }
 * ```
 *
 * The marker from `withLoggingContext` will then be attached to every log, like:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 */
inline fun <ReturnT> withLoggingContext(
    vararg logMarkers: LogMarker,
    block: () -> ReturnT
): ReturnT {
  val contextMarkers = loggingContext.getOrSet { ArrayList(logMarkers.size) }
  // We add context markers in reverse when adding them to log events, to show the newest marker
  // first. But if we called `withLoggingContext` with multiple markers, this would cause these
  // markers to show in reverse order to how they were passed. So to counteract that, we add the
  // markers to the logging context here in reverse order.
  logMarkers.asList().forEachReversed { _, marker -> contextMarkers.add(marker) }

  try {
    return block()
  } finally {
    for (i in logMarkers.indices) {
      contextMarkers.removeLast()
    }
    // Reset thread-local if list is empty, to avoid keeping the allocation around forever
    if (contextMarkers.isEmpty()) {
      loggingContext.remove()
    }
  }
}

internal fun getLogMarkersFromContext(): List<LogMarker> {
  // loggingContext will be null if withLoggingContext has not been called in this thread
  return loggingContext.get() ?: emptyList()
}

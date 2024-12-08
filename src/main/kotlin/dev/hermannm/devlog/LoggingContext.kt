package dev.hermannm.devlog

import kotlin.concurrent.getOrSet

@PublishedApi // PublishedApi to use this in inline `withLoggingContext` function.
internal val loggingContext = ThreadLocal<ArrayList<LogMarker>>()

inline fun <ReturnT> withLoggingContext(vararg markers: LogMarker, block: () -> ReturnT): ReturnT {
  val contextMarkers = loggingContext.getOrSet { ArrayList(markers.size) }
  // We add context markers in reverse when adding them to log entries, to show the newest marker
  // first. But if we called `withLoggingContext` with multiple markers, this would cause these
  // markers to show in reverse order to how they were passed. So to counteract that, we add the
  // markers to the logging context here in reverse order.
  markers.forEachReversed { contextMarkers.add(it) }

  try {
    return block()
  } finally {
    for (i in markers.indices) {
      contextMarkers.removeLast()
    }
    // Reset thread-local if list is empty, to avoid keeping the allocation around forever
    if (contextMarkers.isEmpty()) {
      loggingContext.remove()
    }
  }
}

/**
 * Internal utility function to get the log markers from [loggingContext] and falling back to
 * [emptyList] if the thread-local is not set, so we don't have to deal with nulls.
 */
internal fun getMarkersFromLoggingContext(): List<LogMarker> {
  return loggingContext.get() ?: emptyList()
}

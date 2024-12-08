package dev.hermannm.devlog

import net.logstash.logback.marker.Markers
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.Marker as Slf4jMarker
import org.slf4j.event.Level as Slf4jLogLevel
import org.slf4j.spi.LocationAwareLogger as Slf4jLocationAwareLogger
import org.slf4j.spi.LoggingEventBuilder

class Logger
internal constructor(
    private val slf4jLogger: Slf4jLogger,
) {
  constructor(name: String) : this(slf4jLogger = LoggerFactory.getLogger(name))

  constructor(function: () -> Unit) : this(name = getClassNameFromFunction(function))

  fun info(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    log(LogLevel.INFO, message, markers, cause)
  }

  fun warn(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    log(LogLevel.WARN, message, markers, cause)
  }

  fun error(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    log(LogLevel.ERROR, message, markers, cause)
  }

  fun debug(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    log(LogLevel.DEBUG, message, markers, cause)
  }

  fun trace(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    log(LogLevel.TRACE, message, markers, cause)
  }

  private val isLocationAware = slf4jLogger is Slf4jLocationAwareLogger

  private fun log(
      level: LogLevel,
      message: String,
      markers: Array<out LogMarker>,
      cause: Throwable?
  ) {
    if (!slf4jLogger.isEnabledForLevel(level.slf4jLevel)) {
      return
    }

    if (isLocationAware) {
      (slf4jLogger as Slf4jLocationAwareLogger).log(
          combineMarkers(markers),
          FULLY_QUALIFIED_CLASS_NAME,
          level.locationAwareLoggerLevel,
          message,
          null,
          cause,
      )
    } else {
      slf4jLogger
          .makeLoggingEventBuilder(level.slf4jLevel)
          .setMessage(message)
          .also { log ->
            addMarkers(log, markers)
            if (cause != null) {
              log.setCause(cause)
            }
          }
          .log()
    }
  }

  private fun addMarkers(log: LoggingEventBuilder, markers: Array<out LogMarker>) {
    markers.forEach { log.addMarker(it.slf4jMarker) }

    val contextMarkers = getMarkersFromLoggingContext()
    // Add context markers in reverse, so newest marker shows first
    contextMarkers.forEachReversed { log.addMarker(it.slf4jMarker) }
  }

  /**
   * [Slf4jLocationAwareLogger.log] takes just a single log marker, so to pass multiple markers, we
   * have to combine them using [Slf4jMarker.add].
   */
  private fun combineMarkers(markers: Array<out LogMarker>): Slf4jMarker? {
    val contextMarkers = getMarkersFromLoggingContext()

    return when {
      // We have to combine the markers for this log entry with the markers from the logging
      // context. But we can avoid doing this combination if:
      // - There are no log markers -> return null
      // - Log entry has 1 marker, and the context is empty -> return log entry marker
      // - Log entry has no markers, but context has 1 marker -> return context marker
      markers.isEmpty() && contextMarkers.isEmpty() -> null
      markers.size == 1 && contextMarkers.isEmpty() -> markers.first().slf4jMarker
      markers.isEmpty() && contextMarkers.size == 1 -> contextMarkers.first().slf4jMarker
      else -> {
        val combinedMarker = Markers.empty()
        markers.forEach { combinedMarker.add(it.slf4jMarker) }
        // Add context markers in reverse, so newest marker shows first
        contextMarkers.forEachReversed { combinedMarker.add(it.slf4jMarker) }
        combinedMarker
      }
    }
  }

  internal companion object {
    internal val FULLY_QUALIFIED_CLASS_NAME: String = Logger::class.java.name
  }
}

internal enum class LogLevel(
    internal val slf4jLevel: Slf4jLogLevel,
    internal val locationAwareLoggerLevel: Int,
) {
  INFO(Slf4jLogLevel.INFO, Slf4jLocationAwareLogger.INFO_INT),
  WARN(Slf4jLogLevel.WARN, Slf4jLocationAwareLogger.WARN_INT),
  ERROR(Slf4jLogLevel.ERROR, Slf4jLocationAwareLogger.ERROR_INT),
  DEBUG(Slf4jLogLevel.DEBUG, Slf4jLocationAwareLogger.DEBUG_INT),
  TRACE(Slf4jLogLevel.TRACE, Slf4jLocationAwareLogger.TRACE_INT),
}

/**
 * Implementation based on the
 * [KLoggerNameResolver from kotlin-logging-jvm](https://github.com/oshai/kotlin-logging/blob/e9c6ec570cd503c626fca5878efcf1291d4125b7/src/jvmMain/kotlin/mu/internal/KLoggerNameResolver.kt).
 *
 * Licensed by Ohad Shai under
 * [Apache 2.0](https://github.com/oshai/kotlin-logging/blob/e9c6ec570cd503c626fca5878efcf1291d4125b7/LICENSE).
 */
internal fun getClassNameFromFunction(function: () -> Unit): String {
  val name = function.javaClass.name
  return when {
    name.contains("Kt$") -> name.substringBefore("Kt$")
    name.contains("$") -> name.substringBefore("$")
    else -> name
  }
}

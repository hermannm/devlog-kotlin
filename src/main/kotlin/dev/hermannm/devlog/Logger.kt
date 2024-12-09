package dev.hermannm.devlog

import ch.qos.logback.classic.Logger as LogbackLogger
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory
import org.slf4j.event.Level as Slf4jLogLevel
import org.slf4j.spi.LocationAwareLogger

class Logger
internal constructor(
    internal val logbackLogger: LogbackLogger,
) {
  constructor(name: String) : this(getLogbackLogger(name))

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

  private fun log(
      level: LogLevel,
      message: String,
      markers: Array<out LogMarker>,
      cause: Throwable?
  ) {
    if (!logbackLogger.isEnabledForLevel(level.slf4jLevel)) {
      return
    }

    /**
     * Logback can be configured to output file/line information of where in the source code a log
     * occurred. But if we just call the normal SLF4J logger methods here, that would show this
     * class (Logger) as where the logs occurred - but what we actually want is to show where in the
     * user's code the log was made!
     *
     * To solve this problem, SLF4J provides a [LocationAwareLogger] interface, which Logback
     * implements. The interface has a [LocationAwareLogger.log] method that takes a fully qualified
     * class name, which Logback can then use to omit it from the file/line info.
     */
    logbackLogger.log(
        combineMarkers(markers),
        FULLY_QUALIFIED_CLASS_NAME,
        level.intValue,
        message,
        null,
        cause,
    )
  }

  /**
   * [LocationAwareLogger.log] takes just a single log marker, so to pass multiple markers, we have
   * to combine them using [LogstashMarker.add].
   */
  private fun combineMarkers(markers: Array<out LogMarker>): LogstashMarker? {
    // loggingContext will be null if withLoggingContext has not been called in this thread
    val contextMarkers = loggingContext.get() ?: emptyList()

    return when {
      // We have to combine the markers for this log entry with the markers from the logging
      // context. But we can avoid doing this combination if:
      // - There are no log markers -> return null
      // - Log entry has 1 marker, and the context is empty -> return log entry marker
      // - Log entry has no markers, but context has 1 marker -> return context marker
      markers.isEmpty() && contextMarkers.isEmpty() -> null
      markers.size == 1 && contextMarkers.isEmpty() -> markers.first().logstashMarker
      markers.isEmpty() && contextMarkers.size == 1 -> contextMarkers.first().logstashMarker
      else -> {
        val combinedMarker = Markers.empty()
        markers.forEach { combinedMarker.add(it.logstashMarker) }
        // Add context markers in reverse, so newest marker shows first
        contextMarkers.forEachReversed { combinedMarker.add(it.logstashMarker) }
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
    internal val intValue: Int,
) {
  INFO(Slf4jLogLevel.INFO, LocationAwareLogger.INFO_INT),
  WARN(Slf4jLogLevel.WARN, LocationAwareLogger.WARN_INT),
  ERROR(Slf4jLogLevel.ERROR, LocationAwareLogger.ERROR_INT),
  DEBUG(Slf4jLogLevel.DEBUG, LocationAwareLogger.DEBUG_INT),
  TRACE(Slf4jLogLevel.TRACE, LocationAwareLogger.TRACE_INT),
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

/**
 * @throws IllegalStateException If [LoggerFactory.getLogger] does not return a Logback logger. This
 *   library is made to work with Logback only (since we use Logback-specific features such as raw
 *   JSON markers), so we want to fail loudly if Logback is not configured.
 */
internal fun getLogbackLogger(name: String): LogbackLogger {
  try {
    return LoggerFactory.getLogger(name) as LogbackLogger
  } catch (e: ClassCastException) {
    throw IllegalStateException(
        "Failed to get Logback logger - have you added logback-classic as a dependency?",
        e,
    )
  }
}

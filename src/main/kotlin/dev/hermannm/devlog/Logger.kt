package dev.hermannm.devlog

import net.logstash.logback.marker.Markers
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level as Slf4jLogLevel
import org.slf4j.spi.LocationAwareLogger as Slf4jLocationAwareLogger

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
          makeAggregateMarker(markers),
          FULLY_QUALIFIED_CLASS_NAME,
          level.locationAwareLoggerLevel,
          message,
          null,
          cause,
      )
    } else {
      val log = slf4jLogger.atLevel(level.slf4jLevel).setMessage(message)
      for (marker in markers) {
        log.addMarker(marker.slf4jMarker)
      }
      if (cause != null) {
        log.setCause(cause)
      }
      log.log()
    }
  }

  private fun makeAggregateMarker(markers: Array<out LogMarker>): Marker? {
    return when (markers.size) {
      0 -> null
      1 -> markers.first().slf4jMarker
      else -> Markers.aggregate(*markers.mapArray { it.slf4jMarker })
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
private fun getClassNameFromFunction(func: () -> Unit): String {
  val name = func.javaClass.name
  return when {
    name.contains("Kt$") -> name.substringBefore("Kt$")
    name.contains("$") -> name.substringBefore("$")
    else -> name
  }
}

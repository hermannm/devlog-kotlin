package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory as Slf4jLoggerFactory
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.spi.LocationAwareLogger
import org.slf4j.spi.LoggingEventAware

@PublishedApi
internal interface LogHandler<LogEventT : LogEvent> {
  val underlyingLogger: Slf4jLogger

  fun createLogEventIfEnabled(level: LogLevel): LogEventT?

  fun log(event: LogEventT)

  companion object {
    fun get(name: String): LogHandler<*> {
      val underlyingLogger = Slf4jLoggerFactory.getLogger(name)

      try {
        if (underlyingLogger is LogbackLogger) {
          return LogbackLogHandler(underlyingLogger)
        }
      } catch (_: Throwable) {
        // The above will fail if Logback is not on the classpath. This likely means that the user
        // has chosen a different SLF4J implementation, in which case we want to just use that.
      }

      return Slf4jLogHandler(underlyingLogger)
    }
  }
}

@JvmInline
internal value class LogbackLogHandler(
    override val underlyingLogger: LogbackLogger,
) : LogHandler<LogbackLogEvent> {
  override fun createLogEventIfEnabled(level: LogLevel): LogbackLogEvent? {
    val logbackLevel = level.toLogback()

    if (!underlyingLogger.isEnabledFor(logbackLevel)) {
      return null
    }

    return LogbackLogEvent(
        logbackLevel,
        underlyingLogger,
        logHandlerClassName = FULLY_QUALIFIED_CLASS_NAME,
    )
  }

  override fun log(event: LogbackLogEvent) {
    underlyingLogger.callAppenders(event)
  }

  private fun LogLevel.toLogback(): LogbackLevel {
    return when (this) {
      LogLevel.INFO -> LogbackLevel.INFO
      LogLevel.WARN -> LogbackLevel.WARN
      LogLevel.ERROR -> LogbackLevel.ERROR
      LogLevel.DEBUG -> LogbackLevel.DEBUG
      LogLevel.TRACE -> LogbackLevel.TRACE
    }
  }

  internal companion object {
    /**
     * Passed to the [LogEvent] when logging to indicate which class made the log. Logback can then
     * use this to attach metadata to the log of where in the source code the log was made.
     */
    internal val FULLY_QUALIFIED_CLASS_NAME = LogbackLogHandler::class.java.name
  }
}

@JvmInline
internal value class Slf4jLogHandler(
    override val underlyingLogger: Slf4jLogger,
) : LogHandler<Slf4jLogEvent> {
  override fun createLogEventIfEnabled(level: LogLevel): Slf4jLogEvent? {
    val slf4jLevel = level.toSlf4j()

    if (!underlyingLogger.isEnabledForLevel(slf4jLevel)) {
      return null
    }

    return Slf4jLogEvent(
        slf4jLevel,
        underlyingLogger,
        logHandlerClassName = FULLY_QUALIFIED_CLASS_NAME,
    )
  }

  override fun log(event: Slf4jLogEvent) {
    when (underlyingLogger) {
      // If logger is LoggingEventAware, we can just log the event directly
      is LoggingEventAware -> underlyingLogger.log(event)
      // If logger is LocationAware, we want to use that interface so the logger implementation
      // can show the correct file location of where the log was made
      is LocationAwareLogger -> logWithLocationAwareApi(underlyingLogger, event)
      // Otherwise, we fall back to the base SLF4J Logger API
      else -> logWithBasicSlf4jApi(underlyingLogger, event)
    }
  }

  private fun logWithLocationAwareApi(logger: LocationAwareLogger, event: Slf4jLogEvent) {
    // Location-aware SLF4J API doesn't take KeyValuePair, so we must merge them into message
    val message = event.mergeMessageAndKeyValuePairs()
    logger.log(
        null, // marker (we don't use this)
        event.callerBoundary, // Fully qualified class name of class making log (set in constructor)
        event.level.toInt(),
        message,
        null, // argArray (we don't use this)
        event.throwable,
    )
  }

  private fun logWithBasicSlf4jApi(logger: Slf4jLogger, event: Slf4jLogEvent) {
    // Basic SLF4J API doesn't take KeyValuePair, so we must merge them into message
    val message = event.mergeMessageAndKeyValuePairs()
    // level should never be null here, since we pass it in the constructor
    when (event.level!!) {
      Slf4jLevel.INFO ->
          when (event.throwable) {
            null -> logger.info(message)
            else -> logger.info(message, event.throwable)
          }
      Slf4jLevel.WARN ->
          when (event.throwable) {
            null -> logger.warn(message)
            else -> logger.warn(message, event.throwable)
          }
      Slf4jLevel.ERROR ->
          when (event.throwable) {
            null -> logger.error(message)
            else -> logger.error(message, event.throwable)
          }
      Slf4jLevel.DEBUG ->
          when (event.throwable) {
            null -> logger.debug(message)
            else -> logger.debug(message, event.throwable)
          }
      Slf4jLevel.TRACE ->
          when (event.throwable) {
            null -> logger.trace(message)
            else -> logger.trace(message, event.throwable)
          }
    }
  }

  private fun LogLevel.toSlf4j(): Slf4jLevel {
    return when (this) {
      LogLevel.INFO -> Slf4jLevel.INFO
      LogLevel.WARN -> Slf4jLevel.WARN
      LogLevel.ERROR -> Slf4jLevel.ERROR
      LogLevel.DEBUG -> Slf4jLevel.DEBUG
      LogLevel.TRACE -> Slf4jLevel.TRACE
    }
  }

  internal companion object {
    /**
     * Passed to the [LogEvent] when logging to indicate which class made the log. If the SLF4J
     * implementation implements the [LoggingEventAware] or [LocationAwareLogger] interfaces, it can
     * use this to attach metadata to the log of where in the source code the log was made.
     */
    internal val FULLY_QUALIFIED_CLASS_NAME = Slf4jLogHandler::class.java.name
  }
}

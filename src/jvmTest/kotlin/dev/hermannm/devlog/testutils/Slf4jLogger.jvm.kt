package dev.hermannm.devlog.testutils

import ch.qos.logback.classic.Logger as LogbackLogger
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.spi.LocationAwareLogger
import org.slf4j.spi.LoggingEventAware
import org.slf4j.spi.LoggingEventBuilder

internal class EventAwareSlf4jLogger(
    private val logbackLogger: LogbackLogger,
) : Slf4jLogger by logbackLogger, LoggingEventAware by logbackLogger {
  init {
    this.shouldNotBeInstanceOf<LogbackLogger>()
    this.shouldBeInstanceOf<LoggingEventAware>()
  }

  override fun makeLoggingEventBuilder(level: Slf4jLevel): LoggingEventBuilder =
      logbackLogger.makeLoggingEventBuilder(level)
}

internal class LocationAwareSlf4jLogger(
    private val logbackLogger: LogbackLogger,
) : LocationAwareLogger by logbackLogger {
  init {
    this.shouldNotBeInstanceOf<LogbackLogger>()
    this.shouldNotBeInstanceOf<LoggingEventAware>()
    this.shouldBeInstanceOf<LocationAwareLogger>()
  }

  override fun makeLoggingEventBuilder(level: Slf4jLevel): LoggingEventBuilder =
      logbackLogger.makeLoggingEventBuilder(level)
}

internal class PlainSlf4jLogger(
    private val logbackLogger: LogbackLogger,
) : Slf4jLogger by logbackLogger {
  init {
    this.shouldNotBeInstanceOf<LogbackLogger>()
    this.shouldNotBeInstanceOf<LoggingEventAware>()
    this.shouldNotBeInstanceOf<LocationAwareSlf4jLogger>()
  }

  override fun makeLoggingEventBuilder(level: Slf4jLevel): LoggingEventBuilder =
      logbackLogger.makeLoggingEventBuilder(level)
}

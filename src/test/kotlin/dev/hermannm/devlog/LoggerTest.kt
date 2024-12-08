package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.spi.LocationAwareLogger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LoggerTest {
  /** We use a ListAppender from Logback here so we can inspect log events after logging. */
  private val logAppender = ListAppender<ILoggingEvent>()
  private val testLoggerName = "LoggerTest"
  private val logbackLogger = LoggerFactory.getLogger(testLoggerName) as LogbackLogger
  private val log = Logger(logbackLogger)
  /**
   * [Logger.log] uses different log methods depending on whether the underlying logger is
   * location-aware or not (see [Logger.isLocationAware]). We want to test here that our Logger
   * methods work for both location-aware (which Logback loggers are by default) and
   * location-unaware SLF4J loggers, so we create a separate location-unaware logger here.
   */
  private val locationUnawareLog = Logger(LocationUnawareLogger(logbackLogger))

  @BeforeAll
  fun setup() {
    logAppender.start()
    logbackLogger.addAppender(logAppender)
    logbackLogger.level = LogbackLevel.TRACE

    // Ensure that our different loggers implement/don't implement the LocationAwareLogger interface
    // as expected
    log.slf4jLogger.shouldBeInstanceOf<LocationAwareLogger>()
    locationUnawareLog.shouldNotBeInstanceOf<LocationAwareLogger>()
  }

  @AfterEach
  fun teardown() {
    logAppender.list.clear()
  }

  @Test
  fun `info log`() {
    testLogFunction(LogLevel.INFO) { message -> log.info(message) }
  }

  @Test
  fun `warn log`() {
    testLogFunction(LogLevel.WARN) { message -> log.warn(message) }
  }

  @Test
  fun `error log`() {
    testLogFunction(LogLevel.ERROR) { message -> log.error(message) }
  }

  @Test
  fun `debug log`() {
    testLogFunction(LogLevel.DEBUG) { message -> log.debug(message) }
  }

  @Test
  fun `trace log`() {
    testLogFunction(LogLevel.TRACE) { message -> log.trace(message) }
  }

  @Test
  fun `info log with location-unaware logger`() {
    testLogFunction(LogLevel.INFO) { message -> locationUnawareLog.info(message) }
  }

  @Test
  fun `warn log with location-unaware logger`() {
    testLogFunction(LogLevel.WARN) { message -> locationUnawareLog.warn(message) }
  }

  @Test
  fun `error log with location-unaware logger`() {
    testLogFunction(LogLevel.ERROR) { message -> locationUnawareLog.error(message) }
  }

  @Test
  fun `debug log with location-unaware logger`() {
    testLogFunction(LogLevel.DEBUG) { message -> locationUnawareLog.debug(message) }
  }

  @Test
  fun `trace log with location-unaware logger`() {
    testLogFunction(LogLevel.TRACE) { message -> locationUnawareLog.trace(message) }
  }

  private fun testLogFunction(logLevel: LogLevel, logFunction: (String) -> Unit) {
    val testMessage = "Test message"
    logFunction(testMessage)

    logAppender.list shouldHaveSize 1
    val log = logAppender.list.first()
    log.level.toString() shouldBe logLevel.slf4jLevel.toString()
    log.message shouldBe testMessage
    log.loggerName shouldBe testLoggerName
  }
}

/**
 * Wraps an SLF4J logger and implements the SLF4J logger interface by interface delegation - this
 * ensures that we only implement that interface, and not the LocationAwareLogger interface (see
 * [LoggerTest.locationUnawareLog]).
 */
internal class LocationUnawareLogger(
    private val innerLogger: Slf4jLogger,
) : Slf4jLogger by innerLogger

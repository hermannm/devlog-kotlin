package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LoggerTest {
  /** We use a ListAppender from Logback here so we can inspect log events after logging. */
  private val logAppender = ListAppender<ILoggingEvent>()
  private val testLoggerName = "LoggerTest"
  private val logbackLogger = getLogbackLogger(testLoggerName)
  private val log = Logger(logbackLogger)

  @BeforeAll
  fun setup() {
    logAppender.start()
    logbackLogger.addAppender(logAppender)
    logbackLogger.level = LogbackLevel.TRACE
  }

  @AfterEach
  fun teardown() {
    logAppender.list.clear()
  }

  @Test
  fun `info log`() {
    testLogFunction(LogLevel.INFO) { message, marker, exception ->
      log.info(message, marker, cause = exception)
    }
  }

  @Test
  fun `warn log`() {
    testLogFunction(LogLevel.WARN) { message, marker, exception ->
      log.warn(message, marker, cause = exception)
    }
  }

  @Test
  fun `error log`() {
    testLogFunction(LogLevel.ERROR) { message, marker, exception ->
      log.error(message, marker, cause = exception)
    }
  }

  @Test
  fun `debug log`() {
    testLogFunction(LogLevel.DEBUG) { message, marker, exception ->
      log.debug(message, marker, cause = exception)
    }
  }

  @Test
  fun `trace log`() {
    testLogFunction(LogLevel.TRACE) { message, marker, exception ->
      log.trace(message, marker, cause = exception)
    }
  }

  /**
   * We test logs with marker + cause exception above, but we also want to make sure that just
   * logging a message by itself works.
   */
  @Test
  fun `log with no markers or exceptions`() {
    log.info("Test")

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.message shouldBe "Test"
    logEvent.markerList.shouldBeNull()
    logEvent.throwableProxy.shouldBeNull()
  }

  @Test
  fun `Logger constructor with name parameter`() {
    val testName = "LoggerWithCustomName"
    val logger = Logger(name = testName)
    logger.logbackLogger.name shouldBe testName
  }

  @Test
  fun `Logger constructor with function parameter`() {
    // All loggers in this file should have this name (since file name and class name here are the
    // same), whether it's constructed inside the class, outside, or on a companion object.
    val expectedName = "dev.hermannm.devlog.LoggerTest"
    loggerConstructedInsideClass.logbackLogger.name shouldBe expectedName
    loggerConstructedOutsideClass.logbackLogger.name shouldBe expectedName
    loggerConstructedOnCompanionObject.logbackLogger.name shouldBe expectedName

    // Logger constructed in separate file should be named after that file.
    loggerConstructedInOtherFile.logbackLogger.name shouldBe "dev.hermannm.devlog.TestUtils"
  }

  @Test
  fun `Logger fully qualified class name has expected value`() {
    Logger.FULLY_QUALIFIED_CLASS_NAME shouldBe "dev.hermannm.devlog.Logger"
  }

  private fun testLogFunction(
      logLevel: LogLevel,
      logFunction: (String, LogMarker, Exception) -> Unit
  ) {
    val testMessage = "Test message"
    val testMarker = marker("test", true)
    val testException = Exception("Something went wrong")
    logFunction(testMessage, testMarker, testException)

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.level.toString() shouldBe logLevel.slf4jLevel.toString()
    logEvent.message shouldBe testMessage
    logEvent.markerList shouldContain testMarker.logstashMarker
    logEvent.throwableProxy.message shouldBe testException.message
    logEvent.loggerName shouldBe testLoggerName
  }

  private val loggerConstructedInsideClass = Logger {}

  companion object {
    private val loggerConstructedOnCompanionObject = Logger {}
  }
}

private val loggerConstructedOutsideClass = Logger {}

package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.logstash.logback.marker.ObjectAppendingMarker
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
    testLogFunction(LogLevel.INFO) { message, exception, markerKey, markerValue ->
      log.info {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `warn log`() {
    testLogFunction(LogLevel.WARN) { message, exception, markerKey, markerValue ->
      log.warn {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `error log`() {
    testLogFunction(LogLevel.ERROR) { message, exception, markerKey, markerValue ->
      log.error {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `debug log`() {
    testLogFunction(LogLevel.DEBUG) { message, exception, markerKey, markerValue ->
      log.debug {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `trace log`() {
    testLogFunction(LogLevel.TRACE) { message, exception, markerKey, markerValue ->
      log.trace {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `info log using 'at' method`() {
    testLogFunction(LogLevel.INFO) { message, exception, markerKey, markerValue ->
      log.at(LogLevel.INFO) {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `warn log using 'at' method`() {
    testLogFunction(LogLevel.WARN) { message, exception, markerKey, markerValue ->
      log.at(LogLevel.WARN) {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `error log using 'at' method`() {
    testLogFunction(LogLevel.ERROR) { message, exception, markerKey, markerValue ->
      log.at(LogLevel.ERROR) {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `debug log using 'at' method`() {
    testLogFunction(LogLevel.DEBUG) { message, exception, markerKey, markerValue ->
      log.at(LogLevel.DEBUG) {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  @Test
  fun `trace log using 'at' method`() {
    testLogFunction(LogLevel.TRACE) { message, exception, markerKey, markerValue ->
      log.at(LogLevel.TRACE) {
        cause = exception
        addMarker(markerKey, markerValue)
        message
      }
    }
  }

  /**
   * We test logs with marker + cause exception above, but we also want to make sure that just
   * logging a message by itself works.
   */
  @Test
  fun `log with no markers or exceptions`() {
    log.info { "Test" }

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.message shouldBe "Test"
    logEvent.markerList.shouldBeNull()
    logEvent.throwableProxy.shouldBeNull()
  }

  @Test
  fun `log builder functions do not get called if log level is disabled`() {
    // We have configured logback-test.xml to disable loggers with this prefix
    val disabledLogger = Logger(name = "com.example.disabled.Logger")

    val failingLogBuilder: LogBuilder.() -> String = {
      throw Exception("This function should not get called when log level is disabled")
    }

    disabledLogger.info(failingLogBuilder)
    disabledLogger.warn(failingLogBuilder)
    disabledLogger.error(failingLogBuilder)
    disabledLogger.debug(failingLogBuilder)
    disabledLogger.trace(failingLogBuilder)
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

  @Test
  fun `log has expected file location`() {
    log.info { "Test" }

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    val callerData = logEvent.callerData
    callerData.shouldNotBeEmpty()
    val caller = callerData.first()

    /**
     * We don't test line number here, as the logger methods will have wrong line numbers due to
     * being inline functions (see [Logger.info]).
     */
    caller.fileName shouldBe "LoggerTest.kt"
    caller.className shouldBe "dev.hermannm.devlog.LoggerTest"
    caller.methodName shouldBe "log has expected file location"
  }

  private fun testLogFunction(
      logLevel: LogLevel,
      // (message, cause exception, marker key, marker value)
      logFunction: (String, Exception, String, String) -> Unit
  ) {
    val message = "Test message"
    val markerKey = "key"
    val markerValue = "value"
    val exception = Exception("Something went wrong")
    logFunction(message, exception, markerKey, markerValue)

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.level.toString() shouldBe logLevel.toString()
    logEvent.message shouldBe message
    logEvent.throwableProxy.message shouldBe exception.message
    logEvent.loggerName shouldBe testLoggerName

    logEvent.markerList shouldHaveSize 1
    val marker = logEvent.markerList.first()
    val logstashMarker = marker.shouldBeInstanceOf<ObjectAppendingMarker>()
    logstashMarker.fieldName shouldBe markerKey

    val fakeJsonGenerator = FakeJsonGenerator()
    logstashMarker.writeTo(fakeJsonGenerator)
    fakeJsonGenerator.obj shouldBe markerValue
  }

  private val loggerConstructedInsideClass = Logger {}

  companion object {
    private val loggerConstructedOnCompanionObject = Logger {}
  }
}

private val loggerConstructedOutsideClass = Logger {}

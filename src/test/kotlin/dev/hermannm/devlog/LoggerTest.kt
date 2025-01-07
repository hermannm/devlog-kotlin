package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory as Slf4jLoggerFactory
import org.slf4j.event.KeyValuePair

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LoggerTest {
  /** We use a ListAppender from Logback here so we can inspect log events after logging. */
  private val logAppender = ListAppender<ILoggingEvent>()
  private val testLoggerName = "LoggerTest"
  private val logbackLogger = Slf4jLoggerFactory.getLogger(testLoggerName) as LogbackLogger
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

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `info log`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.INFO) {
      test.logger.info(test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `warn log`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.WARN) {
      test.logger.warn(test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `error log`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.ERROR) {
      test.logger.error(test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `debug log`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.DEBUG) {
      test.logger.debug(test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `trace log`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.TRACE) {
      test.logger.trace(test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `info log using 'at' method`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.INFO) {
      test.logger.at(LogLevel.INFO, test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `warn log using 'at' method`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.WARN) {
      test.logger.at(LogLevel.WARN, test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `error log using 'at' method`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.ERROR) {
      test.logger.at(LogLevel.ERROR, test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `debug log using 'at' method`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.DEBUG) {
      test.logger.at(LogLevel.DEBUG, test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `trace log using 'at' method`(test: LoggerTestCase) {
    test.verifyLogOutput(LogLevel.TRACE) {
      test.logger.at(LogLevel.TRACE, test.cause) {
        field(test.fieldKey1, test.fieldValue1)
        field(test.fieldKey2, test.fieldValue2)
        test.message
      }
    }
  }

  /**
   * We test logs with field + cause exception above, but we also want to make sure that just
   * logging a message by itself works.
   */
  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `log with no fields or exceptions`(test: LoggerTestCase) {
    test.logger.info { "Test" }

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.message shouldBe "Test"
    logEvent.keyValuePairs.shouldBeNull()
    logEvent.throwableProxy.shouldBeNull()
  }

  @Test
  fun `log builder functions do not get called if log level is disabled`() {
    val failingLogBuilder: LogBuilder.() -> String = {
      throw Exception("This function should not get called when log level is disabled")
    }

    try {
      // Incrementally disable log levels, and verify that the log builder does not get called for
      // disabled levels
      logbackLogger.level = LogbackLevel.DEBUG
      log.trace(null, failingLogBuilder)

      logbackLogger.level = LogbackLevel.INFO
      log.debug(null, failingLogBuilder)

      logbackLogger.level = LogbackLevel.WARN
      log.info(null, failingLogBuilder)

      logbackLogger.level = LogbackLevel.ERROR
      log.warn(null, failingLogBuilder)

      logbackLogger.level = LogbackLevel.OFF
      log.error(null, failingLogBuilder)
    } finally {
      // Reset logger level, so this test doesn't affect other tests
      logbackLogger.level = LogbackLevel.TRACE
    }
  }

  @Test
  fun `Logger constructor with name parameter`() {
    val testName = "LoggerWithCustomName"
    val logger = getLogger(name = testName)
    logger.underlyingLogger.name shouldBe testName
  }

  @Test
  fun `Logger constructor with function parameter`() {
    // All loggers in this file should have this name (since file name and class name here are the
    // same), whether it's constructed inside the class, outside, or on a companion object.
    val expectedName = "dev.hermannm.devlog.LoggerTest"
    loggerInsideClass.underlyingLogger.name shouldBe expectedName
    loggerOutsideClass.underlyingLogger.name shouldBe expectedName
    loggerOnCompanionObject.underlyingLogger.name shouldBe expectedName

    // Logger constructed in separate file should be named after that file.
    loggerInOtherFile.underlyingLogger.name shouldBe "dev.hermannm.devlog.TestUtils"
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `log has expected file location`(test: LoggerTestCase) {
    test.logger.info { "Test" }

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    logEvent.callerData.shouldNotBeEmpty()
    val caller = logEvent.callerData.first()

    if (test.shouldHaveCorrectFileLocation) {
      /**
       * We don't test line number here, as the logger methods will have wrong line numbers due to
       * being inline functions (see [Logger.info]).
       */
      caller.fileName shouldBe "LoggerTest.kt"
      caller.className shouldBe "dev.hermannm.devlog.LoggerTest"
      caller.methodName shouldBe "log has expected file location"
    }
  }

  @Test
  fun `log event caller boundaries have expected values`() {
    LogbackLogEvent.FULLY_QUALIFIED_CLASS_NAME shouldBe "dev.hermannm.devlog.LogbackLogEvent"
    Slf4jLogEvent.FULLY_QUALIFIED_CLASS_NAME shouldBe "dev.hermannm.devlog.Slf4jLogEvent"
  }

  @Test
  fun `Logback is loaded in tests`() {
    LOGBACK_IS_ON_CLASSPATH shouldBe true
  }

  @Test
  fun `non-local return works in all Logger methods`() {
    // This won't compile if the methods aren't inline, and we want to verify that
    log.info {
      return
    }
    log.warn {
      return
    }
    log.error {
      return
    }
    log.debug {
      return
    }
    log.trace {
      return
    }
    log.at(LogLevel.INFO) {
      return
    }
  }

  data class LoggerTestCase(
      val name: String,
      val logger: Logger,
      val loggerName: String = logger.underlyingLogger.name,
      val message: String = "Test message",
      val fieldKey1: String = "key1",
      val fieldValue1: String = "value1",
      val fieldKey2: String = "key2",
      val fieldValue2: User = User(id = 1, name = "John Doe"),
      val cause: Exception = Exception("Something went wrong"),
      val expectedMessage: String = message,
      val expectedFields: List<KeyValuePair>? =
          listOf(
              KeyValuePair(fieldKey1, fieldValue1),
              KeyValuePair(fieldKey2, RawJson("""{"id":1,"name":"John Doe"}""")),
          ),
      val shouldHaveCorrectFileLocation: Boolean = true,
  ) {
    override fun toString() = name
  }

  val loggerTestCases =
      listOf(
          LoggerTestCase("Logback logger", log),
          LoggerTestCase(
              "Event-aware SLF4J logger",
              logger = Logger(EventAwareSlf4jLogger(logbackLogger)),
          ),
          LoggerTestCase(
              "Location-aware SLF4J logger",
              logger = Logger(LocationAwareSlf4jLogger(logbackLogger)),
              expectedMessage = """Test message [key1=value1, key2={"id":1,"name":"John Doe"}]""",
              expectedFields = null,
          ),
          LoggerTestCase(
              "Plain SLF4J logger",
              logger = Logger(PlainSlf4jLogger(logbackLogger)),
              expectedMessage = """Test message [key1=value1, key2={"id":1,"name":"John Doe"}]""",
              expectedFields = null,
              // The plain SLF4J logger does not implement location-aware logging, so we don't
              // expect it to have correct file location
              shouldHaveCorrectFileLocation = false,
          ),
      )

  private fun LoggerTestCase.verifyLogOutput(expectedLogLevel: LogLevel, block: () -> Unit) {
    val timeBefore = Instant.now()
    block()
    val timeAfter = Instant.now()

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()

    logEvent.loggerName shouldBe this.loggerName
    logEvent.message shouldBe this.expectedMessage
    logEvent.level.toString() shouldBe expectedLogLevel.toString()
    logEvent.instant.shouldBeBetween(timeBefore, timeAfter)

    val throwableProxy = logEvent.throwableProxy.shouldBeInstanceOf<ThrowableProxy>()
    throwableProxy.throwable shouldBe this.cause

    logEvent.keyValuePairs shouldBe this.expectedFields
  }

  private val loggerInsideClass = getLogger {}

  companion object {
    private val loggerOnCompanionObject = getLogger {}
  }
}

private val loggerOutsideClass = getLogger {}

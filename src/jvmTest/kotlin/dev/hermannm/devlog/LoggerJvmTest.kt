package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import dev.hermannm.devlog.testutils.Event
import dev.hermannm.devlog.testutils.EventAwareSlf4jLogger
import dev.hermannm.devlog.testutils.EventType
import dev.hermannm.devlog.testutils.LocationAwareSlf4jLogger
import dev.hermannm.devlog.testutils.PlainSlf4jLogger
import dev.hermannm.devlog.testutils.TestCase
import dev.hermannm.devlog.testutils.parameterizedTest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import org.slf4j.LoggerFactory as Slf4jLoggerFactory
import org.slf4j.event.KeyValuePair

internal class LoggerJvmTest {
  companion object {
    /** We use a ListAppender from Logback here so we can inspect log events after logging. */
    private val logAppender = ListAppender<ILoggingEvent>()
    private const val TEST_LOGGER_NAME = "LoggerTest"
    private val logbackLogger = Slf4jLoggerFactory.getLogger(TEST_LOGGER_NAME) as LogbackLogger
    private val log = Logger(logbackLogger)

    init {
      logAppender.start()
      logbackLogger.addAppender(logAppender)
      logbackLogger.level = LogbackLevel.TRACE
    }
  }

  @AfterTest
  fun reset() {
    logAppender.list.clear()
  }

  @Test
  fun `info log`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.INFO) {
        test.logger.info(test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `warn log`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.WARN) {
        test.logger.warn(test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `error log`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.ERROR) {
        test.logger.error(test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `debug log`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.DEBUG) {
        test.logger.debug(test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `trace log`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.TRACE) {
        test.logger.trace(test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `info log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.INFO) {
        test.logger.at(LogLevel.INFO, test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `warn log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.WARN) {
        test.logger.at(LogLevel.WARN, test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `error log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.ERROR) {
        test.logger.at(LogLevel.ERROR, test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `debug log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.DEBUG) {
        test.logger.at(LogLevel.DEBUG, test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  @Test
  fun `trace log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.verifyLogOutput(LogLevel.TRACE) {
        test.logger.at(LogLevel.TRACE, test.cause) {
          field(test.fieldKey1, test.fieldValue1)
          field(test.fieldKey2, test.fieldValue2)
          test.message
        }
      }
    }
  }

  /**
   * We test logs with field + cause exception above, but we also want to make sure that just
   * logging a message by itself works.
   */
  @Test
  fun `log with no fields or exceptions`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
      test.logger.info { "Test" }

      logAppender.list shouldHaveSize 1
      val logEvent = logAppender.list.first()
      logEvent.message shouldBe "Test"
      logEvent.keyValuePairs.shouldBeNull()
      logEvent.throwableProxy.shouldBeNull()
    }
  }

  @Test
  fun `log has expected file location`() {
    parameterizedTest(loggerTestCases, afterEach = ::reset) { test ->
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
        caller.fileName shouldBe "LoggerJvmTest.kt"
        caller.className shouldBe "dev.hermannm.devlog.LoggerJvmTest"
        caller.methodName shouldBe "log has expected file location"
      }
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

  data class LoggerTestCase(
      override val name: String,
      val logger: Logger,
      val loggerName: String = logger.underlyingLogger.name,
      val message: String = "Test message",
      val fieldKey1: String = "key1",
      val fieldValue1: String = "value1",
      val fieldKey2: String = "key2",
      val fieldValue2: Event = Event(id = 1001, type = EventType.ORDER_PLACED),
      val cause: Exception = Exception("Something went wrong"),
      val expectedMessage: String = message,
      val expectedFields: List<KeyValuePair>? =
          listOf(
              KeyValuePair(fieldKey1, fieldValue1),
              KeyValuePair(fieldKey2, RawJson("""{"id":1001,"type":"ORDER_PLACED"}""")),
          ),
      val shouldHaveCorrectFileLocation: Boolean = true,
  ) : TestCase

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
              expectedMessage =
                  """Test message [key1=value1, key2={"id":1001,"type":"ORDER_PLACED"}]""",
              expectedFields = null,
          ),
          LoggerTestCase(
              "Plain SLF4J logger",
              logger = Logger(PlainSlf4jLogger(logbackLogger)),
              expectedMessage =
                  """Test message [key1=value1, key2={"id":1001,"type":"ORDER_PLACED"}]""",
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
  fun `isEnabled methods return expected results for enabled and disabled log levels`() {
    try {
      log.isTraceEnabled.shouldBeTrue()
      log.isEnabledFor(LogLevel.TRACE).shouldBeTrue()

      // Incrementally raise the log level, and verify that the isEnabled methods return expected
      logbackLogger.level = LogbackLevel.DEBUG

      log.isTraceEnabled.shouldBeFalse()
      log.isEnabledFor(LogLevel.TRACE).shouldBeFalse()
      log.isDebugEnabled.shouldBeTrue()
      log.isEnabledFor(LogLevel.DEBUG).shouldBeTrue()

      logbackLogger.level = LogbackLevel.INFO

      log.isDebugEnabled.shouldBeFalse()
      log.isEnabledFor(LogLevel.DEBUG).shouldBeFalse()
      log.isInfoEnabled.shouldBeTrue()
      log.isEnabledFor(LogLevel.INFO).shouldBeTrue()

      logbackLogger.level = LogbackLevel.WARN

      log.isInfoEnabled.shouldBeFalse()
      log.isEnabledFor(LogLevel.INFO).shouldBeFalse()
      log.isWarnEnabled.shouldBeTrue()
      log.isEnabledFor(LogLevel.WARN).shouldBeTrue()

      logbackLogger.level = LogbackLevel.ERROR

      log.isWarnEnabled.shouldBeFalse()
      log.isEnabledFor(LogLevel.WARN).shouldBeFalse()
      log.isErrorEnabled.shouldBeTrue()
      log.isEnabledFor(LogLevel.ERROR).shouldBeTrue()

      logbackLogger.level = LogbackLevel.OFF

      log.isErrorEnabled.shouldBeFalse()
      log.isEnabledFor(LogLevel.ERROR).shouldBeFalse()
    } finally {
      // Reset logger level, so this test doesn't affect other tests
      logbackLogger.level = LogbackLevel.TRACE
    }
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
}

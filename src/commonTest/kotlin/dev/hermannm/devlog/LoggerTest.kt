@file:Suppress("UsePropertyAccessSyntax")

package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.Event
import dev.hermannm.devlog.testutils.EventType
import dev.hermannm.devlog.testutils.TestCase
import dev.hermannm.devlog.testutils.loggerInOtherFile
import dev.hermannm.devlog.testutils.parameterizedTest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * We want to test all the different logger methods, on a variety of different loggers in different
 * platforms. In order to share as much common code as possible, we define this `LoggerTestCase`
 * class for testing expected log output. Each platform then defines:
 * - A list of [loggerTestCases]
 * - A [verifyLogOutput] function to verify the platform-specific log output
 * - A [getTestLogger] function for getting a logger that only enables logs at a given level
 * - A [resetLoggerTest] function for resetting any state between tests
 *
 * We use this here in the common [LoggerTest] class, so that we can define all common tests once,
 * delegating to platform-specific implementations when necessary.
 */
internal data class LoggerTestCase(
    override val name: String,
    val logger: Logger,
    val expectedMessage: String = LoggerTest.TestInput.MESSAGE,
    val expectedCause: Throwable? = LoggerTest.TestInput.CAUSE,
    val expectedFields: List<LogField> =
        listOf(
            StringLogField(LoggerTest.TestInput.FIELD_KEY_1, LoggerTest.TestInput.FIELD_VALUE_1),
            JsonLogField(LoggerTest.TestInput.FIELD_KEY_2, """{"id":1001,"type":"ORDER_PLACED"}"""),
        ),
    val shouldHaveCorrectFileLocation: Boolean = true,
) : TestCase

internal expect val loggerTestCases: List<LoggerTestCase>

internal expect fun LoggerTestCase.verifyLogOutput(expectedLogLevel: LogLevel, block: () -> Unit)

/**
 * Returns a logger that only enables logs at or above the given level. If [level] is `null`, all
 * logs should be disabled.
 */
internal expect fun getTestLogger(level: LogLevel?): Logger

internal expect fun resetLoggerTest()

internal class LoggerTest {
  private val loggerInsideClass = getLogger()

  companion object {
    private val log = getLogger()
    private val loggerOnCompanionObject = getLogger()
  }

  /** Input passed to the [loggerTestCases] in the tests on this class. */
  object TestInput {
    const val MESSAGE: String = "Test message"
    const val FIELD_KEY_1: String = "key1"
    const val FIELD_VALUE_1: String = "value1"
    const val FIELD_KEY_2: String = "key2"
    val FIELD_VALUE_2: Event = Event(id = 1001, type = EventType.ORDER_PLACED)
    val CAUSE: Throwable? = Exception("Something went wrong")
  }

  @AfterTest
  fun reset() {
    resetLoggerTest()
  }

  @Test
  fun `info log`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.INFO) {
        test.logger.info(cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `warn log`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.WARN) {
        test.logger.warn(cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `error log`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.ERROR) {
        test.logger.error(cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `debug log`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.DEBUG) {
        test.logger.debug(cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `trace log`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.TRACE) {
        test.logger.trace(cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `info log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.INFO) {
        test.logger.at(LogLevel.INFO, cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `warn log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.WARN) {
        test.logger.at(LogLevel.WARN, cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `error log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.ERROR) {
        test.logger.at(LogLevel.ERROR, cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `debug log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.DEBUG) {
        test.logger.at(LogLevel.DEBUG, cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
        }
      }
    }
  }

  @Test
  fun `trace log using 'at' method`() {
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      test.verifyLogOutput(expectedLogLevel = LogLevel.TRACE) {
        test.logger.at(LogLevel.TRACE, cause = TestInput.CAUSE) {
          field(TestInput.FIELD_KEY_1, TestInput.FIELD_VALUE_1)
          field(TestInput.FIELD_KEY_2, TestInput.FIELD_VALUE_2)
          TestInput.MESSAGE
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
    parameterizedTest(loggerTestCases, afterEach = ::resetLoggerTest) { test ->
      val updatedTest =
          test.copy(
              expectedMessage = "Test",
              expectedCause = null,
              expectedFields = emptyList(),
          )
      updatedTest.verifyLogOutput(expectedLogLevel = LogLevel.INFO) {
        updatedTest.logger.info { "Test" }
      }
    }
  }

  @Test
  fun `getLogger with name parameter`() {
    val testName = "LoggerWithCustomName"
    val logger = getLogger(name = testName)
    logger.underlyingLogger.getName() shouldBe testName
  }

  @Test
  fun `getLogger with function parameter`() {
    // All loggers in this file should have this name (since file name and class name here are the
    // same), whether it's constructed inside the class, outside, or on a companion object.
    val expectedName = "dev.hermannm.devlog.LoggerTest"
    loggerInsideClass.underlyingLogger.getName() shouldBe expectedName
    loggerOutsideClass.underlyingLogger.getName() shouldBe expectedName
    loggerOnCompanionObject.underlyingLogger.getName() shouldBe expectedName

    // Logger created in separate file should be named after that file.
    loggerInOtherFile.underlyingLogger.getName() shouldBe
        "dev.hermannm.devlog.testutils.LoggerInOtherFile"
  }

  @Test
  fun `getLogger with class parameter`() {
    val logger = getLogger(LoggerTest::class)
    logger.underlyingLogger.getName() shouldBe "dev.hermannm.devlog.LoggerTest"
  }

  @Test
  fun `getLogger strips away Kt suffix`() {
    val logger = getLogger(LoggerNameTestKt::class)
    logger.underlyingLogger.getName() shouldBe "dev.hermannm.devlog.LoggerNameTest"
  }

  @Test
  fun `getLogger only removes Kt if it is a suffix`() {
    val logger = getLogger(ClassWithKtInName::class)
    logger.underlyingLogger.getName() shouldBe "dev.hermannm.devlog.ClassWithKtInName"
  }

  @Test
  fun `log builder does not get called if log level is disabled`() {
    val failingLogBuilder: LogBuilder.() -> String = {
      throw Exception("This function should not get called when log level is disabled")
    }

    // Incrementally disable log levels, and verify that the log builder does not get called for
    // disabled levels
    getTestLogger(LogLevel.DEBUG).trace(null, failingLogBuilder)
    getTestLogger(LogLevel.INFO).debug(null, failingLogBuilder)
    getTestLogger(LogLevel.WARN).info(null, failingLogBuilder)
    getTestLogger(LogLevel.ERROR).warn(null, failingLogBuilder)
    getTestLogger(level = null).error(null, failingLogBuilder)
  }

  @Test
  fun `isEnabled methods return expected results for enabled and disabled log levels`() {
    // Incrementally raise the log level, and verify that the isEnabled methods return expected
    val traceLogger = getTestLogger(LogLevel.TRACE)
    traceLogger.isTraceEnabled.shouldBeTrue()
    traceLogger.isEnabledFor(LogLevel.TRACE).shouldBeTrue()

    val debugLogger = getTestLogger(LogLevel.DEBUG)
    debugLogger.isTraceEnabled.shouldBeFalse()
    debugLogger.isEnabledFor(LogLevel.TRACE).shouldBeFalse()
    debugLogger.isDebugEnabled.shouldBeTrue()
    debugLogger.isEnabledFor(LogLevel.DEBUG).shouldBeTrue()

    val infoLogger = getTestLogger(LogLevel.INFO)
    infoLogger.isDebugEnabled.shouldBeFalse()
    infoLogger.isEnabledFor(LogLevel.DEBUG).shouldBeFalse()
    infoLogger.isInfoEnabled.shouldBeTrue()
    infoLogger.isEnabledFor(LogLevel.INFO).shouldBeTrue()

    val warnLogger = getTestLogger(LogLevel.WARN)
    warnLogger.isInfoEnabled.shouldBeFalse()
    warnLogger.isEnabledFor(LogLevel.INFO).shouldBeFalse()
    warnLogger.isWarnEnabled.shouldBeTrue()
    warnLogger.isEnabledFor(LogLevel.WARN).shouldBeTrue()

    val errorLogger = getTestLogger(LogLevel.ERROR)
    errorLogger.isWarnEnabled.shouldBeFalse()
    errorLogger.isEnabledFor(LogLevel.WARN).shouldBeFalse()
    errorLogger.isErrorEnabled.shouldBeTrue()
    errorLogger.isEnabledFor(LogLevel.ERROR).shouldBeTrue()

    val disabledLogger = getTestLogger(level = null)
    disabledLogger.isErrorEnabled.shouldBeFalse()
    disabledLogger.isEnabledFor(LogLevel.ERROR).shouldBeFalse()
  }
}

private val loggerOutsideClass = getLogger()

/**
 * Used to test that the `Kt` suffix is stripped away from classes passed to `getLogger`. This is
 * the suffix used for the synthetic classes that Kotlin generates for the top-level of files.
 */
private object LoggerNameTestKt

/**
 * Used to test that the logic used for [LoggerNameTestKt] only applies to classes with `Kt` as a
 * suffix, not when it has `Kt` in the middle of the name like this.
 */
private object ClassWithKtInName

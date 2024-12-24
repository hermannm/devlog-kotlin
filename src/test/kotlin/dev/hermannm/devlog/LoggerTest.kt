package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
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
  private val log = Logger(LogbackLogHandler(logbackLogger))

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
    test.logger.info {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.INFO)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `warn log`(test: LoggerTestCase) {
    test.logger.warn {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.WARN)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `error log`(test: LoggerTestCase) {
    test.logger.error {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.ERROR)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `debug log`(test: LoggerTestCase) {
    test.logger.debug {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.DEBUG)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `trace log`(test: LoggerTestCase) {
    test.logger.trace {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.TRACE)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `info log using 'at' method`(test: LoggerTestCase) {
    test.logger.at(LogLevel.INFO) {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.INFO)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `warn log using 'at' method`(test: LoggerTestCase) {
    test.logger.at(LogLevel.WARN) {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.WARN)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `error log using 'at' method`(test: LoggerTestCase) {
    test.logger.at(LogLevel.ERROR) {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.ERROR)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `debug log using 'at' method`(test: LoggerTestCase) {
    test.logger.at(LogLevel.DEBUG) {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.DEBUG)
  }

  @ParameterizedTest
  @MethodSource("getLoggerTestCases")
  fun `trace log using 'at' method`(test: LoggerTestCase) {
    test.logger.at(LogLevel.TRACE) {
      cause = test.cause
      addField(test.fieldKey1, test.fieldValue1)
      addField(test.fieldKey2, test.fieldValue2)
      test.message
    }

    verifyLogOutput(test, expectedLogLevel = LogLevel.TRACE)
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
      log.trace(failingLogBuilder)

      logbackLogger.level = LogbackLevel.INFO
      log.debug(failingLogBuilder)

      logbackLogger.level = LogbackLevel.WARN
      log.info(failingLogBuilder)

      logbackLogger.level = LogbackLevel.ERROR
      log.warn(failingLogBuilder)

      logbackLogger.level = LogbackLevel.OFF
      log.error(failingLogBuilder)
    } finally {
      // Reset logger level, so this test doesn't affect other tests
      logbackLogger.level = LogbackLevel.TRACE
    }
  }

  @Test
  fun `Logger constructor with name parameter`() {
    val testName = "LoggerWithCustomName"
    val logger = Logger(name = testName)
    logger.logHandler.underlyingLogger.name shouldBe testName
  }

  @Test
  fun `Logger constructor with function parameter`() {
    // All loggers in this file should have this name (since file name and class name here are the
    // same), whether it's constructed inside the class, outside, or on a companion object.
    val expectedName = "dev.hermannm.devlog.LoggerTest"
    loggerConstructedInsideClass.logHandler.underlyingLogger.name shouldBe expectedName
    loggerConstructedOutsideClass.logHandler.underlyingLogger.name shouldBe expectedName
    loggerConstructedOnCompanionObject.logHandler.underlyingLogger.name shouldBe expectedName

    // Logger constructed in separate file should be named after that file.
    loggerConstructedInOtherFile.logHandler.underlyingLogger.name shouldBe
        "dev.hermannm.devlog.TestUtils"
  }

  @Test
  fun `fully qualified class names used for logger caller data has expected value`() {
    LogbackLogHandler.FULLY_QUALIFIED_CLASS_NAME shouldBe "dev.hermannm.devlog.LogbackLogHandler"
    Slf4jLogHandler.FULLY_QUALIFIED_CLASS_NAME shouldBe "dev.hermannm.devlog.Slf4jLogHandler"
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

  /** See comment in [LogEvent.Logback.setThrowable]. */
  @Test
  fun `cause exception can be set to null`() {
    log.error {
      cause = null
      "Test"
    }
  }

  /** See comment in [LogBuilder.cause] setter and [LogEvent.Logback.setThrowable]. */
  @Test
  fun `setting cause multiple times only keeps the first non-null exception`() {
    val exception1 = Exception("Exception 1")
    val exception2 = Exception("Exception 2")

    log.error {
      cause = null
      cause = exception1
      cause = exception2
      "Test"
    }

    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()
    val cause = logEvent.throwableProxy.shouldBeInstanceOf<ThrowableProxy>().throwable
    cause shouldBe exception1
  }

  data class LoggerTestCase(
      val name: String,
      val logger: Logger,
      val loggerName: String = logger.logHandler.underlyingLogger.name,
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
              KeyValuePair(fieldKey2, RawJsonValue("""{"id":1,"name":"John Doe"}""")),
          ),
      val shouldHaveCorrectFileLocation: Boolean = true,
  ) {
    override fun toString() = name

    /** To test log fields with object values. */
    @Serializable data class User(val id: Int, val name: String)
  }

  val loggerTestCases =
      listOf(
          LoggerTestCase("Logback logger", log),
          LoggerTestCase(
              "Event-aware SLF4J logger",
              logger = Logger(Slf4jLogHandler(EventAwareSlf4jLogger(logbackLogger))),
          ),
          LoggerTestCase(
              "Location-aware SLF4J logger",
              logger = Logger(Slf4jLogHandler(LocationAwareSlf4jLogger(logbackLogger))),
              expectedMessage = """Test message [key1=value1, key2={"id":1,"name":"John Doe"}]""",
              expectedFields = null,
          ),
          LoggerTestCase(
              "Plain SLF4J logger",
              logger = Logger(Slf4jLogHandler(PlainSlf4jLogger(logbackLogger))),
              expectedMessage = """Test message [key1=value1, key2={"id":1,"name":"John Doe"}]""",
              expectedFields = null,
              // The plain SLF4J logger does not implement location-aware logging, so we don't
              // expect it to have correct file location
              shouldHaveCorrectFileLocation = false,
          ),
      )

  private fun verifyLogOutput(test: LoggerTestCase, expectedLogLevel: LogLevel) {
    logAppender.list shouldHaveSize 1
    val logEvent = logAppender.list.first()

    logEvent.loggerName shouldBe test.loggerName
    logEvent.message shouldBe test.expectedMessage
    logEvent.level.toString() shouldBe expectedLogLevel.toString()

    val throwableProxy = logEvent.throwableProxy.shouldBeInstanceOf<ThrowableProxy>()
    throwableProxy.throwable shouldBe test.cause

    logEvent.keyValuePairs shouldBe test.expectedFields
  }

  private val loggerConstructedInsideClass = Logger {}

  companion object {
    private val loggerConstructedOnCompanionObject = Logger {}
  }
}

private val loggerConstructedOutsideClass = Logger {}

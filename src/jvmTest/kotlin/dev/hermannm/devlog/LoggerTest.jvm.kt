@file:Suppress("UsePropertyAccessSyntax")

package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import dev.hermannm.devlog.testutils.EventAwareSlf4jLogger
import dev.hermannm.devlog.testutils.LocationAwareSlf4jLogger
import dev.hermannm.devlog.testutils.PlainSlf4jLogger
import dev.hermannm.devlog.testutils.parameterizedTest
import io.kotest.matchers.collections.shouldContainExactly
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

internal actual val loggerTestCases =
    listOf(
        LoggerTestCase(
            "Logback logger",
            logger = Logger(LoggerJvmTest.logbackLogger),
        ),
        LoggerTestCase(
            "Event-aware SLF4J logger",
            logger = Logger(EventAwareSlf4jLogger(LoggerJvmTest.logbackLogger)),
        ),
        LoggerTestCase(
            "Location-aware SLF4J logger",
            logger = Logger(LocationAwareSlf4jLogger(LoggerJvmTest.logbackLogger)),
            expectedMessage =
                """Test message [key1=value1, key2={"id":1001,"type":"ORDER_PLACED"}]""",
            expectedFields = emptyList(),
        ),
        LoggerTestCase(
            "Plain SLF4J logger",
            logger = Logger(PlainSlf4jLogger(LoggerJvmTest.logbackLogger)),
            expectedMessage =
                """Test message [key1=value1, key2={"id":1001,"type":"ORDER_PLACED"}]""",
            expectedFields = emptyList(),
            // The plain SLF4J logger does not implement location-aware logging, so we don't
            // expect it to have correct file location
            shouldHaveCorrectFileLocation = false,
        ),
    )

internal actual fun LoggerTestCase.verifyLogOutput(expectedLogLevel: LogLevel, block: () -> Unit) {
  val timeBefore = Instant.now()
  block()
  val timeAfter = Instant.now()

  LoggerJvmTest.logAppender.list shouldHaveSize 1
  val logEvent = LoggerJvmTest.logAppender.list.first()

  logEvent.loggerName shouldBe this.logger.underlyingLogger.name
  logEvent.message shouldBe this.expectedMessage
  logEvent.level shouldBe expectedLogLevel.toLogback()
  logEvent.instant.shouldBeBetween(timeBefore, timeAfter)

  if (this.expectedCause == null) {
    logEvent.throwableProxy.shouldBeNull()
  } else {
    val throwableProxy = logEvent.throwableProxy.shouldBeInstanceOf<ThrowableProxy>()
    throwableProxy.throwable shouldBe this.expectedCause
  }

  if (this.expectedFields.isEmpty()) {
    logEvent.keyValuePairs.shouldBeNull()
  } else {
    logEvent.keyValuePairs.shouldContainExactly(
        this.expectedFields.map { field ->
          val expectedValue =
              when (field) {
                is StringLogField -> field.value
                is JsonLogField -> RawJson(field.value)
              }
          KeyValuePair(field.key, expectedValue)
        },
    )
  }
}

internal actual fun getTestLogger(level: LogLevel?): Logger {
  LoggerJvmTest.logbackLogger.level = level?.toLogback() ?: LogbackLevel.OFF
  return Logger(LoggerJvmTest.logbackLogger)
}

internal actual fun resetLoggerTest() = LoggerJvmTest.reset()

internal class LoggerJvmTest {
  companion object {
    /** We use a ListAppender from Logback here so we can inspect log events after logging. */
    val logAppender = ListAppender<ILoggingEvent>()
    const val TEST_LOGGER_NAME = "LoggerTest"
    val logbackLogger = Slf4jLoggerFactory.getLogger(TEST_LOGGER_NAME) as LogbackLogger

    init {
      logAppender.start()
      logbackLogger.addAppender(logAppender)
      logbackLogger.level = LogbackLevel.TRACE
    }

    @AfterTest
    fun reset() {
      logAppender.list.clear()
      logbackLogger.level = LogbackLevel.TRACE
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
        caller.fileName shouldBe "LoggerTest.jvm.kt"
        caller.className shouldBe "dev.hermannm.devlog.LoggerJvmTest"
        caller.methodName shouldBe "log has expected file location"
      }
    }
  }

  @Test
  fun `getLogger called in file with platform extension omits the extension from the name`() {
    // `loggerOutsideClass` calls `getLogger {}` at the top-level scope, in this file
    // `LoggerTest.jvm.kt`. The synthetic class for this file will be named `LoggerTest_jvmKt`, and
    // we want to verify that that our `getLoggerName` function strips the `_jvmKt` suffix.
    loggerOutsideClass.underlyingLogger.getName() shouldBe "dev.hermannm.devlog.LoggerTest"
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
}

private val loggerOutsideClass = getLogger {}

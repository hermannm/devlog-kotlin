package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import org.slf4j.event.Level as Slf4jLevel

class LogLevelJvmTest {
  /**
   * We need [kotlin.reflect.full] to use [memberProperties], so we place this test here under
   * jvmTest.
   */
  @Test
  fun `companion object members on LogLevel contain all expected levels and no more`() {
    LogLevel.Companion::class
        .memberProperties
        .map { it.get(LogLevel.Companion) }
        .shouldContainExactlyInAnyOrder(
            LogLevel.ERROR,
            LogLevel.WARN,
            LogLevel.INFO,
            LogLevel.DEBUG,
            LogLevel.TRACE,
        )
  }

  @Test
  fun `LogLevel converts to expected SLF4J levels`() {
    LogLevel.ERROR.toSlf4j() shouldBe Slf4jLevel.ERROR
    LogLevel.WARN.toSlf4j() shouldBe Slf4jLevel.WARN
    LogLevel.INFO.toSlf4j() shouldBe Slf4jLevel.INFO
    LogLevel.DEBUG.toSlf4j() shouldBe Slf4jLevel.DEBUG
    LogLevel.TRACE.toSlf4j() shouldBe Slf4jLevel.TRACE
  }

  @Test
  fun `LogLevel converts to expected Logback levels`() {
    LogLevel.ERROR.toLogback() shouldBe LogbackLevel.ERROR
    LogLevel.WARN.toLogback() shouldBe LogbackLevel.WARN
    LogLevel.INFO.toLogback() shouldBe LogbackLevel.INFO
    LogLevel.DEBUG.toLogback() shouldBe LogbackLevel.DEBUG
    LogLevel.TRACE.toLogback() shouldBe LogbackLevel.TRACE
  }
}

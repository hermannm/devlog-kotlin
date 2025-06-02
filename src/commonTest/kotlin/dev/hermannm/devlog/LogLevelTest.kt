package dev.hermannm.devlog

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.memberProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LogLevelTest {
  @Test
  fun `toString returns expected strings`() {
    LogLevel.ERROR.toString() shouldBe "ERROR"
    LogLevel.WARN.toString() shouldBe "WARN"
    LogLevel.INFO.toString() shouldBe "INFO"
    LogLevel.DEBUG.toString() shouldBe "DEBUG"
    LogLevel.TRACE.toString() shouldBe "TRACE"
  }

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
}

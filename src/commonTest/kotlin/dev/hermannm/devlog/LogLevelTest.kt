package dev.hermannm.devlog

import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class LogLevelTest {
  @Test
  fun `toString returns expected strings`() {
    LogLevel.ERROR.toString() shouldBe "ERROR"
    LogLevel.WARN.toString() shouldBe "WARN"
    LogLevel.INFO.toString() shouldBe "INFO"
    LogLevel.DEBUG.toString() shouldBe "DEBUG"
    LogLevel.TRACE.toString() shouldBe "TRACE"
  }
}

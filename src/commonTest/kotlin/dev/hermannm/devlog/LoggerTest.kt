package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.loggerInOtherFile
import io.kotest.matchers.shouldBe
import kotlin.test.Test

internal class LoggerTest {
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

  private val loggerInsideClass = getLogger {}

  companion object {
    private val loggerOnCompanionObject = getLogger {}
  }
}

private val loggerOutsideClass = getLogger {}

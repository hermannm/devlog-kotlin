package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.LoggingContext
import dev.hermannm.devlog.LoggingContextState
import dev.hermannm.devlog.getCopyOfLoggingContext
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

internal actual fun createLoggingContext(fields: Map<String, String>): LoggingContext {
  return LoggingContext(map = fields, state = LoggingContextState.empty())
}

internal actual fun loggingContextShouldContainExactly(expectedFields: Map<String, String>) {
  val context = getCopyOfLoggingContext()
  context.map.shouldNotBeNull()

  context.map.size shouldBe expectedFields.size
  for ((key, expectedValue) in expectedFields) {
    withClue({ "key='${key}', expectedValue='${expectedValue}'" }) {
      val actualValue = context.map[key]
      actualValue.shouldNotBeNull()
      actualValue shouldBe expectedValue
    }
  }
}

internal actual fun loggingContextShouldBeEmpty() {
  val context = getCopyOfLoggingContext()
  context.map.shouldBeNull()
}

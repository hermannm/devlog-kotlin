package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.getCopyOfLoggingContext
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

internal fun loggingContextShouldContainExactly(expectedFields: Map<String, String>) {
  val contextFields = getCopyOfLoggingContext().getFields()
  contextFields.shouldNotBeNull()

  contextFields.size shouldBe expectedFields.size
  for ((key, expectedValue) in expectedFields) {
    withClue({ "key='${key}', expectedValue='${expectedValue}'" }) {
      val actualValue = contextFields[key]
      actualValue.shouldNotBeNull()
      actualValue shouldBe expectedValue
    }
  }
}

internal fun loggingContextShouldBeEmpty() {
  val contextFields = getCopyOfLoggingContext().getFields()
  contextFields.isNullOrEmpty().shouldBeTrue()
}

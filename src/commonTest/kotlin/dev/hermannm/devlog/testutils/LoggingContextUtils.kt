package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.getLoggingContextFields
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

internal fun loggingContextShouldContainExactly(map: Map<String, String>) {
  val contextFields = getLoggingContextFields()
  contextFields.shouldNotBeNull()

  contextFields.size shouldBe map.size
  for ((key, value) in map) {
    withClue({ "key='${key}', value='${value}'" }) {
      val field = contextFields.find { field -> field.getKeyForLoggingContext() == key }
      field.shouldNotBeNull()
      field.value shouldBe value
    }
  }
}

internal fun loggingContextShouldBeEmpty() {
  getLoggingContextFields().shouldBeNull()
}

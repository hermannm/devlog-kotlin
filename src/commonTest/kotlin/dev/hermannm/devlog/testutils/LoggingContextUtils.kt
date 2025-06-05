package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.LoggingContext
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

internal infix fun LoggingContext.shouldContainExactly(map: Map<String, String>) {
  val contextFields = this.getFieldList()

  contextFields.size shouldBe map.size
  for ((key, value) in map) {
    withClue({ "key='${key}', value='${value}'" }) {
      val field = contextFields.find { field -> field.getKeyForLoggingContext() == key }
      field.shouldNotBeNull()
      field.value shouldBe value
    }
  }
}

internal fun LoggingContext.shouldBeEmpty() {
  this.getFieldList().shouldBeEmpty()
}

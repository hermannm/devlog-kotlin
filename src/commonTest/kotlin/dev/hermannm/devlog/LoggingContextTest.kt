package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.LogOutput
import dev.hermannm.devlog.testutils.captureLogOutput
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val log = getLogger()

internal class LoggingContextTest {
  @Test
  fun `field from logging context is included in log`() {
    val output = captureLogOutput {
      withLoggingContext(
          field("key", "value"),
      ) {
        log.info { "Test" }
      }
    }

    output.contextFields shouldContainExactly mapOf("key" to JsonPrimitive("value"))
  }

  @Test
  fun `logging context applies to all logs in scope`() {
    val outputs = arrayOfNulls<LogOutput>(2)

    withLoggingContext(
        field("key", "value"),
    ) {
      outputs[0] = captureLogOutput { log.info { "Test" } }
      outputs[1] = captureLogOutput { log.info { "Test 2" } }
    }

    outputs.forEach { output ->
      output.shouldNotBeNull()
      output.contextFields shouldContainExactly mapOf("key" to JsonPrimitive("value"))
    }
  }

  @Test
  fun `rawJsonField works with logging context`() {
    val output = captureLogOutput {
      withLoggingContext(
          rawJsonField("event", """{"id":1001,"type":"ORDER_UPDATED"}"""),
      ) {
        log.info { "Test" }
      }
    }

    output.contextFields shouldContainExactly
        mapOf(
            "event" to
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(1001),
                        "type" to JsonPrimitive("ORDER_UPDATED"),
                    ),
                ),
        )
  }

  @Test
  fun `logging context does not apply to logs outside scope`() {
    withLoggingContext(
        field("key", "value"),
    ) {
      log.info { "Inside scope" }
    }

    val output = captureLogOutput { log.info { "Outside scope" } }
    output.contextFields.shouldBeEmpty()
  }

  @Test
  fun `nested logging contexts work`() {
    val output = captureLogOutput {
      withLoggingContext(
          field("outerContext", "value"),
      ) {
        withLoggingContext(
            field("nestedContext", "value"),
        ) {
          log.info { "Test" }
        }
      }
    }

    output.contextFields shouldContainExactly
        mapOf(
            "nestedContext" to JsonPrimitive("value"),
            "outerContext" to JsonPrimitive("value"),
        )
  }

  @Test
  fun `duplicate context field keys only includes the newest fields`() {
    var outputFromInnerContext: LogOutput? = null
    // We want to verify that after exiting the inner logging context, the fields from the outer
    // context are restored
    var outputFromOuterContext: LogOutput? = null

    withLoggingContext(
        field("duplicateKey", "outer"),
    ) {
      withLoggingContext(
          field("duplicateKey", "inner1"),
          field("duplicateKey", "inner2"),
      ) {
        outputFromInnerContext = captureLogOutput { log.info { "Test" } }
      }

      outputFromOuterContext = captureLogOutput { log.info { "Test" } }
    }

    outputFromInnerContext!!.contextFields shouldContainExactly
        mapOf("duplicateKey" to JsonPrimitive("inner1"))

    outputFromOuterContext!!.contextFields shouldContainExactly
        mapOf("duplicateKey" to JsonPrimitive("outer"))
  }

  /**
   * Priority for duplicate keys in log fields is Log event -> Exception -> Context, so log event
   * field should override context field.
   */
  @Test
  fun `context field does not override duplicate log event field`() {
    val output = captureLogOutput {
      withLoggingContext(
          field("duplicateKey", "from context"),
      ) {
        log.info {
          field("duplicateKey", "from log event")
          "Test"
        }
      }
    }

    output.logFields shouldBe
        """
          "duplicateKey":"from log event"
        """
            .trimIndent()

    // We would prefer for log event fields to override context fields, but logstash-logback-encoder
    // does not have support for that at the time of writing. If the library adds support for that
    // at some point, we can uncomment this line to test that the context fields are overwritten.
    // output.contextFields.shouldBeEmpty()
  }

  @Test
  fun `passing a list to withLoggingContext works`() {
    val output = captureLogOutput {
      withLoggingContext(
          logFields =
              listOf(
                  field("key1", "value1"),
                  field("key2", "value2"),
              ),
      ) {
        log.info { "Test" }
      }
    }

    output.contextFields shouldContainExactly
        mapOf(
            "key1" to JsonPrimitive("value1"),
            "key2" to JsonPrimitive("value2"),
        )
  }

  @Test
  fun `passing an empty list to withLoggingContext works`() {
    val output = captureLogOutput {
      withLoggingContext(
          logFields = emptyList(),
      ) {
        log.info { "Test" }
      }
    }

    output.contextFields.shouldBeEmpty()
  }

  @Test
  fun `non-local return works in withLoggingContext`() {
    withLoggingContext(field("key", "value")) {
      // This won't compile if withLoggingContext isn't inline, and we want to verify that
      return
    }
  }
}

package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.LogOutput
import dev.hermannm.devlog.testutils.captureLogOutput
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
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
    val outputFromInnerContext: LogOutput
    // We want to verify that after exiting the inner logging context, the fields from the outer
    // context are restored
    val outputFromOuterContext: LogOutput

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

    outputFromInnerContext.contextFields shouldContainExactly
        mapOf("duplicateKey" to JsonPrimitive("inner1"))

    outputFromOuterContext.contextFields shouldContainExactly
        mapOf("duplicateKey" to JsonPrimitive("outer"))
  }

  /**
   * Priority for duplicate keys in log fields is Log event -> Exception -> Context, so log event
   * field should override context field.
   */
  @Test
  fun `context field does not override duplicate log event field`() {
    val output1: LogOutput
    val output2: LogOutput

    withLoggingContext(
        field("duplicateKey", "from context"),
    ) {
      output1 = captureLogOutput {
        log.info {
          field("duplicateKey", "from log event")
          "Test"
        }
      }

      // Test that logging context still applies here after
      output2 = captureLogOutput { log.info { "Test 2" } }
    }

    output1.logFields shouldBe
        """
          "duplicateKey":"from log event"
        """
            .trimIndent()
    output1.contextFields.shouldBeEmpty()

    output2.logFields.shouldBeEmpty()
    output2.contextFields shouldContainExactly
        mapOf("duplicateKey" to JsonPrimitive("from context"))
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
  fun `non-local return works in withLoggingContext vararg overload`() {
    withLoggingContext(field("key", "value")) {
      // This won't compile if withLoggingContext isn't inline, and we want to verify that
      return
    }
  }

  @Test
  fun `non-local return works in withLoggingContext collection overload`() {
    withLoggingContext(listOf(field("key", "value"))) {
      // This won't compile if withLoggingContext isn't inline, and we want to verify that
      return
    }
  }

  @Test
  fun `non-local return works in withLoggingContext existingContext overload`() {
    withLoggingContext(getCopyOfLoggingContext()) {
      // This won't compile if withLoggingContext isn't inline, and we want to verify that
      return
    }
  }

  @Test
  fun `lambda uses EXACTLY_ONCE contract in withLoggingContext vararg overload`() {
    val uninitialized: String

    withLoggingContext(field("key", "value")) { uninitialized = "Initialized" }

    // This won't compile unless `withLoggingContext` uses `callsInPlace` contract with
    // `InvocationKind.EXACTLY_ONCE`
    useString(uninitialized)
  }

  @Test
  fun `lambda uses EXACTLY_ONCE contract in withLoggingContext collection overload`() {
    val uninitialized: String

    withLoggingContext(listOf(field("key", "value"))) { uninitialized = "Initialized" }

    // This won't compile unless `withLoggingContext` uses `callsInPlace` contract with
    // `InvocationKind.EXACTLY_ONCE`
    useString(uninitialized)
  }

  @Test
  fun `lambda uses EXACTLY_ONCE contract in withLoggingContext existingContext overload`() {
    val uninitialized: String

    withLoggingContext(getCopyOfLoggingContext()) { uninitialized = "Initialized" }

    // This won't compile unless `withLoggingContext` uses `callsInPlace` contract with
    // `InvocationKind.EXACTLY_ONCE`
    useString(uninitialized)
  }

  /**
   * We want to make sure that constructing a unique object of `String` works, for
   * [LoggingContextState.IS_JSON_SENTINEL].
   */
  @Test
  fun `constructing a string creates a unique String instance`() {
    /**
     * We call [kotlin.String] explicitly here, for the same reason as
     * [LoggingContextState.IS_JSON_SENTINEL].
     */
    @Suppress("RemoveRedundantQualifierName") val uniqueString = kotlin.String()
    (uniqueString === "").shouldBeFalse()
  }

  // Dummy method for contract tests
  private fun useString(string: String): Int {
    return string.length
  }
}

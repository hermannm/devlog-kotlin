package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.Event
import dev.hermannm.devlog.testutils.EventType
import dev.hermannm.devlog.testutils.LogOutput
import dev.hermannm.devlog.testutils.captureLogOutput
import dev.hermannm.devlog.testutils.createLoggingContext
import dev.hermannm.devlog.testutils.loggingContextShouldContainExactly
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

    output.contextFields shouldContainExactly mapOf("key" to "value")
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
      output.contextFields shouldContainExactly mapOf("key" to "value")
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
            "nestedContext" to "value",
            "outerContext" to "value",
        )
  }

  @Test
  fun `logging context is attached to exceptions`() {
    val output = captureLogOutput {
      try {
        withLoggingContext(
            field("key", "value"),
        ) {
          throw IllegalStateException("Something went wrong")
        }
      } catch (e: IllegalStateException) {
        log.error(e) { "Test" }
      }
    }

    // We expect the exception context field to be in `logFields`, not `contextFields`, because it
    // has escaped its original context
    output.logFields shouldBe
        """
          "key":"value"
        """
            .trimIndent()
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

    outputFromInnerContext.contextFields shouldContainExactly mapOf("duplicateKey" to "inner1")

    outputFromOuterContext.contextFields shouldContainExactly mapOf("duplicateKey" to "outer")
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
    output2.contextFields shouldContainExactly mapOf("duplicateKey" to "from context")
  }

  @Test
  fun `exception context field overrides outer context field`() {
    val exceptionLogOutput: LogOutput
    val outputAfterException: LogOutput

    withLoggingContext(
        field("duplicateKey", "from outer context"),
    ) {
      try {
        withLoggingContext(
            field("duplicateKey", "attached to exception"),
        ) {
          throw IllegalStateException("Something went wrong")
        }
      } catch (e: IllegalStateException) {
        exceptionLogOutput = captureLogOutput { log.error(e) { "Test" } }
      }

      outputAfterException = captureLogOutput { log.info { "Test 2" } }
    }

    // We expect the exception context field to be in `logFields`, not `contextFields`, because it
    // has escaped its original context
    exceptionLogOutput.logFields shouldBe
        """
          "duplicateKey":"attached to exception"
        """
            .trimIndent()
    // The context fields should be empty, since the outer `duplicateKey` was overridden
    exceptionLogOutput.contextFields.shouldBeEmpty()

    // Check that the outer logging context is restored after logging the exception
    outputAfterException.logFields.shouldBeEmpty()
    outputAfterException.contextFields shouldContainExactly
        mapOf("duplicateKey" to "from outer context")
  }

  @Test
  fun `nested logging context restores previous context fields on exit`() {
    val event1 = Event(id = 1001, type = EventType.ORDER_PLACED)
    val event2 = Event(id = 1002, type = EventType.ORDER_UPDATED)

    withLoggingContext(
        field("event", event1),
        field("stringField", "parentValue"),
        field("parentOnlyField", "value1"),
        field("fieldThatIsStringInParentButJsonInChild", "stringValue"),
    ) {
      val parentContext =
          mapOf(
              "event" to """{"id":1001,"type":"ORDER_PLACED"}""",
              "stringField" to "parentValue",
              "parentOnlyField" to "value1",
              "fieldThatIsStringInParentButJsonInChild" to "stringValue",
          )
      loggingContextShouldContainExactly(parentContext)

      withLoggingContext(
          field("event", event2),
          field("stringField", "childValue"),
          field("childOnlyField", "value2"),
          rawJsonField("fieldThatIsStringInParentButJsonInChild", """{"test":true}"""),
      ) {
        loggingContextShouldContainExactly(
            mapOf(
                "event" to """{"id":1002,"type":"ORDER_UPDATED"}""",
                "stringField" to "childValue",
                "parentOnlyField" to "value1",
                "childOnlyField" to "value2",
                "fieldThatIsStringInParentButJsonInChild" to """{"test":true}""",
            ),
        )
      }

      loggingContextShouldContainExactly(parentContext)
    }
  }

  @Test
  fun `withLoggingContext existingContext overload merges given context with existing fields`() {
    val existingContext =
        createLoggingContext(mapOf("fieldMap1" to "value", "fieldMap2" to "value"))

    withLoggingContext(field("existingField", "value")) {
      loggingContextShouldContainExactly(mapOf("existingField" to "value"))

      withLoggingContext(existingContext) {
        loggingContextShouldContainExactly(
            mapOf(
                "existingField" to "value",
                "fieldMap1" to "value",
                "fieldMap2" to "value",
            ),
        )
      }

      // Previous fields should be restored after
      loggingContextShouldContainExactly(mapOf("existingField" to "value"))
    }
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
            "key1" to "value1",
            "key2" to "value2",
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
  fun `passing empty varargs to withLoggingContext works`() {
    val output = captureLogOutput { withLoggingContext { log.info { "Test" } } }

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

  // Dummy method for contract tests
  private fun useString(string: String): Int {
    return string.length
  }
}

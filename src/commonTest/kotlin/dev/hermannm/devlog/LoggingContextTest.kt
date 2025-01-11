package dev.hermannm.devlog

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

private val log = getLogger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
              "event${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to """{"id":1001,"type":"ORDER_PLACED"}""",
              "stringField" to "parentValue",
              "parentOnlyField" to "value1",
              "fieldThatIsStringInParentButJsonInChild" to "stringValue",
          )
      LoggingContext shouldContainExactly parentContext

      withLoggingContext(
          field("event", event2),
          field("stringField", "childValue"),
          field("childOnlyField", "value2"),
          rawJsonField("fieldThatIsStringInParentButJsonInChild", """{"test":true}"""),
      ) {
        LoggingContext shouldContainExactly
            mapOf(
                "event${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to
                    """{"id":1002,"type":"ORDER_UPDATED"}""",
                "stringField" to "childValue",
                "parentOnlyField" to "value1",
                "childOnlyField" to "value2",
                "fieldThatIsStringInParentButJsonInChild${LOGGING_CONTEXT_JSON_KEY_SUFFIX}" to
                    """{"test":true}""",
            )
      }

      LoggingContext shouldContainExactly parentContext
    }
  }

  @Test
  fun `ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS has expected value`() {
    // Since we use LoggingContextJsonFieldWriter in tests, we expect this to be set
    ADD_JSON_SUFFIX_TO_LOGGING_CONTEXT_KEYS.shouldBeTrue()
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

  @Test
  fun `getLoggingContext allows passing logging context between threads`() {
    val event = Event(id = 1001, type = EventType.ORDER_PLACED)

    val lock = ReentrantLock()
    // Used to wait for the child thread to complete its log
    val latch = CountDownLatch(1)

    val output = captureLogOutput {
      // Aquire a lock around the outer withLoggingContext in the parent thread, to test that
      // the logging context works in the child thread even when the outer context has exited
      lock.withLock {
        withLoggingContext(field("event", event)) {
          // Get the parent logging context (the one we just entered)
          val loggingContext = getLoggingContext()

          thread {
            // Acquire the lock here in the child thread - this will block until the outer
            // logging context has exited
            lock.withLock {
              // Use the parent logging context here in the child thread
              withLoggingContext(loggingContext) { log.error { "Test" } }
              latch.countDown()
            }
          }
        }
      }

      latch.await() // Waits until completed
    }

    output.contextFields shouldContainExactly
        mapOf(
            "event" to
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(1001),
                        "type" to JsonPrimitive("ORDER_PLACED"),
                    ),
                ),
        )
  }
}

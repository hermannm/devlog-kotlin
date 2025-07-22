package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.Event
import dev.hermannm.devlog.testutils.EventType
import dev.hermannm.devlog.testutils.captureLogOutput
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.JsonPrimitive

private val log = getLogger()

internal class ExceptionWithLoggingContextTest {
  @Test
  fun `exception implementing HasLogFields has field included in log`() {
    val output = captureLogOutput {
      log.error(exceptionWithLoggingContext("exceptionField", "value")) { "Test" }
    }

    output.logFields shouldBe
        """
          "exceptionField":"value"
        """
            .trimIndent()
  }

  @Test
  fun `ExceptionWithLoggingContext includes fields from logging context`() {
    val output = captureLogOutput {
      try {
        withLoggingContext(field("contextField", "value")) {
          throw exceptionWithLoggingContext("exceptionField", "value")
        }
      } catch (e: Exception) {
        log.error(e) { "Test" }
      }
    }

    output.logFields shouldBe
        """
          "exceptionField":"value","contextField":"value"
        """
            .trimIndent()
    output.contextFields.shouldBeEmpty()
  }

  /**
   * The first test above verifies that logging context fields are included when the exception is
   * caught outside the context. We want to verify that this still works when the exception is
   * caught inside the context, and that this doesn't duplicate the fields from the context.
   */
  @Test
  fun `ExceptionWithLoggingContext still includes fields from logging context when caught within that context`() {
    val output = captureLogOutput {
      withLoggingContext(field("contextField", "value")) {
        try {
          throw exceptionWithLoggingContext("exceptionField", "value")
        } catch (e: Exception) {
          log.error(e) { "Test" }
        }
      }
    }

    output.logFields shouldBe
        """
          "exceptionField":"value"
        """
            .trimIndent()
    output.contextFields shouldContainExactly mapOf("contextField" to JsonPrimitive("value"))
  }

  @Test
  fun `child exception that implements HasLogFields`() {
    val output = captureLogOutput {
      val exception =
          Exception(
              "Parent exception",
              exceptionWithLoggingContext("childException", "value"),
          )
      log.error(exception) { "Test" }
    }

    output.logFields shouldBe
        """
          "childException":"value"
        """
            .trimIndent()
  }

  @Test
  fun `parent and child exceptions that both implement HasLogFields have their fields merged`() {
    val output = captureLogOutput {
      val exception =
          ExceptionWithLoggingContext(
              message = "Parent exception",
              logFields = listOf(field("parentField1", "value"), field("parentField2", "value")),
              cause = exceptionWithLoggingContext("childField", "value"),
          )
      log.error(exception) { "Test" }
    }

    output.logFields shouldBe
        """
          "parentField1":"value","parentField2":"value","childField":"value"
        """
            .trimIndent()
  }

  @Test
  fun `exception log fields are placed between context and log event fields`() {
    val output = captureLogOutput {
      try {
        withLoggingContext(field("contextField", "value")) {
          throw exceptionWithLoggingContext("exceptionField", "value")
        }
      } catch (e: Exception) {
        log.error(e) {
          field("logEventField", "value")
          "Test"
        }
      }
    }

    output.logFields shouldBe
        """
          "logEventField":"value","exceptionField":"value","contextField":"value"
        """
            .trimIndent()
  }

  @Test
  fun `exception with duplicate log fields only includes first field`() {
    val output = captureLogOutput {
      val exception =
          ExceptionWithLoggingContext(
              "Test",
              listOf(field("duplicateKey", "value1"), field("duplicateKey", "value2")),
              cause = exceptionWithLoggingContext("duplicateKey", "value3"),
          )
      log.error(exception) { "Test" }
    }

    output.logFields shouldBe
        """
          "duplicateKey":"value1"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log fields is Log event -> Exception -> Context, so log event
   * field should override exception field.
   */
  @Test
  fun `exception log field does not override duplicate log event field`() {
    val output = captureLogOutput {
      log.error(exceptionWithLoggingContext("duplicateKey", "from exception")) {
        field("duplicateKey", "from log event")
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "duplicateKey":"from log event"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log fields is Log event -> Exception -> Context, so exception
   * log field should override context field.
   */
  @Test
  fun `exception log field overrides duplicate context field`() {
    val output = captureLogOutput {
      withLoggingContext(field("duplicateKey", "from context")) {
        log.error(exceptionWithLoggingContext("duplicateKey", "from exception")) { "Test" }
      }
    }

    output.logFields shouldBe
        """
          "duplicateKey":"from exception"
        """
            .trimIndent()
  }

  @Test
  fun `serializable object field works on ExceptionWithLoggingContext`() {
    val event = Event(id = 1001, type = EventType.ORDER_PLACED)

    val exception =
        ExceptionWithLoggingContext(
            message = null,
            logFields = listOf(field("event", event)),
        )

    val output = captureLogOutput { log.error(exception) { "Test" } }

    output.logFields shouldBe
        """
          "event":{"id":1001,"type":"ORDER_PLACED"}
        """
            .trimIndent()
  }

  @Test
  fun `custom implementation of HasLogFields works`() {
    class CustomException : Exception(), HasLogFields {
      override val logFields =
          listOf(
              field("key1", "value1"),
              field("key2", "value2"),
          )
    }

    val output = captureLogOutput { log.error(cause = CustomException()) { "Test" } }

    output.logFields shouldBe
        """
          "key1":"value1","key2":"value2"
        """
            .trimIndent()
  }

  @Test
  fun `inheriting from ExceptionWithLoggingContext works`() {
    class CustomException :
        ExceptionWithLoggingContext(
            message = "Test",
            logFields = listOf(field("key", "value")),
        )

    val output = captureLogOutput { log.error(cause = CustomException()) { "Test" } }

    output.logFields shouldBe
        """
          "key":"value"
        """
            .trimIndent()
  }

  @Test
  fun `vararg overload works`() {
    val cause = Exception("Cause")
    val exception =
        ExceptionWithLoggingContext(
            "Test",
            field("key1", "value1"),
            field("key2", "value2"),
            cause = cause,
        )

    exception.message shouldBe "Test"
    exception.logFields shouldBe listOf(field("key1", "value1"), field("key2", "value2"))
    exception.cause shouldBe cause
  }

  @Test
  fun `overload without message works`() {
    val cause = Exception("Cause message")
    val exception =
        ExceptionWithLoggingContext(
            listOf(field("key", "value")),
            cause,
        )

    exception.message shouldBe "Cause message"
    exception.logFields shouldBe listOf(field("key", "value"))
    exception.cause shouldBe cause
  }

  @Test
  fun `vararg overload without message works`() {
    val cause = Exception("Cause message")
    val exception =
        ExceptionWithLoggingContext(
            field("key1", "value1"),
            field("key2", "value2"),
            cause = cause,
        )

    exception.message shouldBe "Cause message"
    exception.logFields shouldBe listOf(field("key1", "value1"), field("key2", "value2"))
    exception.cause shouldBe cause
  }

  @Test
  fun `extending ExceptionWithLoggingContext and overriding message works`() {
    class CustomException : ExceptionWithLoggingContext(listOf(field("key", "value"))) {
      override val message = "Custom message"
    }

    val exception = CustomException()
    exception.message shouldBe "Custom message"
    exception.logFields shouldBe listOf(field("key", "value"))
  }

  @Test
  fun `extending ExceptionWithLoggingContext with varargs and overriding message works`() {
    class CustomException :
        ExceptionWithLoggingContext(
            field("key1", "value1"),
            field("key2", "value2"),
        ) {
      override val message = "Custom message"
    }

    val exception = CustomException()
    exception.message shouldBe "Custom message"
    exception.logFields shouldBe listOf(field("key1", "value1"), field("key2", "value2"))
  }

  /** See comment in [LogBuilder.addFieldsFromCauseException]. */
  @Test
  fun `exception cause cycle should not cause infinite loop`() {
    class EvilException : Exception() {
      var settableCause: Throwable? = null

      override val cause: Throwable?
        get() = settableCause
    }

    val exception1 = EvilException()
    val exception2 = EvilException()
    val exception3 = EvilException()

    // Set up cause cycle
    exception1.settableCause = exception2
    exception2.settableCause = exception3
    exception3.settableCause = exception1

    log.info(exception1) { "Should not cause an infinite loop" }
  }
}

private fun exceptionWithLoggingContext(key: String, value: String) =
    ExceptionWithLoggingContext(
        message = "Test exception",
        logFields = listOf(field(key, value)),
    )

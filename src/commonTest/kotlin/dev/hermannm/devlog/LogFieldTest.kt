package dev.hermannm.devlog

import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private val log = getLogger {}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class LogFieldTest {
  @Test
  fun `basic log field test`() {
    val output = captureLogOutput {
      log.info {
        field("key", "value")
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "key":"value"
        """
            .trimIndent()
  }

  @Test
  fun `log field with Serializable object`() {
    val event = Event(id = 1001, type = EventType.ORDER_PLACED)

    val output = captureLogOutput {
      log.info {
        field("event", event)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "event":{"id":1001,"type":"ORDER_PLACED"}
        """
            .trimIndent()
  }

  @Test
  fun `multiple log fields`() {
    val output = captureLogOutput {
      log.info {
        field("first", true)
        field("second", listOf("value1", "value2"))
        field("third", 10)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "first":true,"second":["value1","value2"],"third":10
        """
            .trimIndent()
  }

  @Test
  fun `custom serializer`() {
    val prefixSerializer =
        object : SerializationStrategy<String> {
          override val descriptor = String.serializer().descriptor

          override fun serialize(encoder: Encoder, value: String) {
            encoder.encodeString("Prefix: ${value}")
          }
        }

    val output = captureLogOutput {
      log.info {
        field("key", "value", serializer = prefixSerializer)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "key":"Prefix: value"
        """
            .trimIndent()
  }

  /**
   * On [field] and [LogBuilder.field], we place a non-nullable `Any` bound on the `ValueT` type
   * parameter, and type the `value` parameter as `ValueT?`. This is to support passing in a custom
   * serializer for a type, but still allow passing in `null` for the value (since this is handled
   * before checking the serializer in [encodeFieldValue]). This test checks that this works.
   */
  @Test
  fun `custom serializer with nullable value`() {
    val event: Event? = null

    val output = captureLogOutput {
      withLoggingContext(
          // Test `field` in context and on log, since these are different functions
          field("eventInContext", event, Event.serializer()),
      ) {
        log.info {
          field("event", event, Event.serializer())
          "Test"
        }
      }
    }

    output.logFields shouldBe
        """
          "event":null
        """
            .trimIndent()
    output.contextFields shouldContainExactly mapOf("eventInContext" to JsonNull)
  }

  @Test
  fun `non-serializable object falls back to toString`() {
    data class NonSerializableEvent(val id: Long, val type: String)

    val event = NonSerializableEvent(id = 1001, type = "ORDER_UPDATED")

    val output = captureLogOutput {
      log.info {
        field("event", event)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "event":"NonSerializableEvent(id=1001, type=ORDER_UPDATED)"
        """
            .trimIndent()
  }

  @Test
  fun `duplicate field keys only includes the first field`() {
    val output = captureLogOutput {
      log.info {
        field("duplicateKey", "value1")
        field("duplicateKey", "value2")
        field("duplicateKey", "value3")
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "duplicateKey":"value1"
        """
            .trimIndent()
  }

  @Test
  fun `null field value is allowed`() {
    val nullValue: String? = null

    val output = captureLogOutput {
      log.info {
        field("key", nullValue)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "key":null
        """
            .trimIndent()
  }

  /**
   * We want to test handling of raw JSON by:
   * - [LogBuilder.rawJsonField] method for adding log field to a log
   * - [rawJsonField] function for creating log fields (for `withLoggingContext`)
   * - [rawJson] function for creating a `JsonElement` from a raw JSON string
   *
   * So we create a test case for each of these, and run each test case through every test of raw
   * JSON handling.
   */
  class RawJsonTestCase(
      private val name: String,
      val addRawJsonField:
          (logBuilder: LogBuilder, key: String, json: String, validJson: Boolean) -> Unit,
  ) {
    override fun toString() = name
  }

  val rawJsonTestCases: List<RawJsonTestCase> =
      listOf(
          RawJsonTestCase("rawJsonField LogBuilder method") { logBuilder, key, json, validJson ->
            logBuilder.rawJsonField(key, json, validJson)
          },
          RawJsonTestCase("rawJsonField function") { logBuilder, key, json, validJson ->
            val field = rawJsonField(key, json, validJson)
            logBuilder.existingField(field)
          },
          RawJsonTestCase("rawJson function") { logBuilder, key, json, validJson ->
            logBuilder.field(key, value = rawJson(json, validJson))
          },
      )

  @ParameterizedTest
  @MethodSource("getRawJsonTestCases")
  fun `raw JSON field works for valid JSON`(test: RawJsonTestCase) {
    val eventJson = """{"id":1001,"type":"ORDER_UPDATED"}"""

    // The above JSON should work both for validJson = true and validJson = false
    for (assumeValidJson in listOf(true, false)) {
      withClue({ "assumeValidJson = ${assumeValidJson}" }) {
        val output = captureLogOutput {
          log.info {
            test.addRawJsonField(this, "event", eventJson, assumeValidJson)
            "Test"
          }
        }

        output.logFields shouldBe
            """
              "event":${eventJson}
            """
                .trimIndent()
      }
    }
  }

  @ParameterizedTest
  @MethodSource("getRawJsonTestCases")
  fun `raw JSON field escapes invalid JSON by default`(test: RawJsonTestCase) {
    val invalidJson = """{"id":1"""

    val output = captureLogOutput {
      log.info {
        test.addRawJsonField(this, "event", invalidJson, false)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "event":"{\"id\":1"
        """
            .trimIndent()
  }

  /**
   * When the user sets validJson = true on rawJsonField, they promise that the given JSON is valid,
   * so it should be passed on as-is. We therefore verify here that no validity checks are made on
   * the given JSON, although the user _should_ never pass invalid JSON to rawJsonField like this.
   */
  @ParameterizedTest
  @MethodSource("getRawJsonTestCases")
  fun `raw JSON field does not escape invalid JSON when validJson is set to true`(
      test: RawJsonTestCase
  ) {
    val invalidJson = """{"id":1"""

    val output = captureLogOutput {
      log.info {
        test.addRawJsonField(this, "event", invalidJson, true)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "event":${invalidJson}
        """
            .trimIndent()
  }

  @ParameterizedTest
  @MethodSource("getRawJsonTestCases")
  fun `raw JSON field re-encodes JSON when it contains newlines`(test: RawJsonTestCase) {
    val jsonWithNewlines =
        """
          {
            "id": 1001,
            "type": "ORDER_UPDATED"
          }
        """
            .trimIndent()

    val output = captureLogOutput {
      log.info {
        test.addRawJsonField(this, "event", jsonWithNewlines, false)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "event":{"id":1001,"type":"ORDER_UPDATED"}
        """
            .trimIndent()
  }

  /**
   * [kotlinx.serialization.json.JsonUnquotedLiteral], which we use in [rawJson], throws if given a
   * literal "null" string. So we have to check for "null" and instead return [JsonNull] in that
   * case - we want to test that this works.
   */
  @Test
  fun `passing a JSON literal null to rawJson works`() {
    val value = rawJson("null")
    value shouldBe JsonNull
  }

  @Test
  fun `existingField allows adding a previously constructed field to the log`() {
    val existingField = field("key", "value")

    val output = captureLogOutput {
      log.info {
        existingField(existingField)
        "Test"
      }
    }

    output.logFields shouldBe
        """
          "key":"value"
        """
            .trimIndent()
  }

  @Suppress("ReplaceCallWithBinaryOperator") // We want to use .equals explicitly here
  @Test
  fun `LogField equals, toString and hashCode work as expected`() {
    val stringField = field("key", "value")
    val stringField2 = field("key", "value")

    stringField.equals(stringField2).shouldBeTrue()
    stringField.hashCode() shouldBe stringField2.hashCode()
    stringField.toString() shouldBe "key=value"
    stringField.toString() shouldBe stringField2.toString()

    val objectField = field("key", Event(id = 1001, type = EventType.ORDER_PLACED))

    stringField.equals(objectField).shouldBeFalse()
    stringField.hashCode() shouldNotBe objectField.hashCode()

    val objectAsStringField = field("key", """{"id":1001,"type":"ORDER_PLACED"}""")

    objectField.equals(objectAsStringField).shouldBeTrue()
    objectField.hashCode() shouldBe objectAsStringField.hashCode()
    objectField.toString() shouldBe """key={"id":1001,"type":"ORDER_PLACED"}"""
    objectField.toString() shouldBe objectAsStringField.toString()
  }
}

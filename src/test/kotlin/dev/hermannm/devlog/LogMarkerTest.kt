package dev.hermannm.devlog

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Test

private val log = Logger {}

class LogMarkerTest {
  @Test
  fun `basic log marker test`() {
    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("testMarker", "value"),
      )
    }

    markers shouldBe
        """
          "testMarker":"value"
        """
            .trimIndent()
  }

  @Test
  fun `log marker with Serializable object`() {
    @Serializable data class User(val id: Int, val name: String)

    val user = User(id = 1, name = "John Doe")

    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("user", user),
      )
    }

    markers shouldBe
        """
          "user":{"id":1,"name":"John Doe"}
        """
            .trimIndent()
  }

  @Test
  fun `multiple log markers`() {
    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("first", true),
          marker("second", listOf("value1", "value2")),
          marker("third", 10),
      )
    }

    markers shouldBe
        """
          "first":true,"second":["value1","value2"],"third":10
        """
            .trimIndent()
  }

  @Test
  fun `special-case types`() {
    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("instant", Instant.parse("2024-12-09T16:38:23Z")),
          marker("uri", URI.create("https://example.com")),
          marker("url", URL("https://example.com")),
          marker("uuid", UUID.fromString("3638dd04-d196-41ad-8b15-5188a22a6ba4")),
          marker("bigDecimal", BigDecimal("100.0")),
      )
    }

    markers shouldContain """"instant":"2024-12-09T16:38:23Z""""
    markers shouldContain """"uri":"https://example.com""""
    markers shouldContain """"url":"https://example.com""""
    markers shouldContain """"uuid":"3638dd04-d196-41ad-8b15-5188a22a6ba4""""
    markers shouldContain """"bigDecimal":"100.0""""
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

    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("key", "value", serializer = prefixSerializer),
      )
    }

    markers shouldBe
        """
          "key":"Prefix: value"
        """
            .trimIndent()
  }

  @Test
  fun `non-serializable object falls back to toString`() {
    data class User(val id: Int, val name: String)

    val user = User(id = 1, name = "John Doe")

    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("user", user),
      )
    }

    markers shouldBe
        """
          "user":"User(id=1, name=John Doe)"
        """
            .trimIndent()
  }

  @Test
  fun `duplicate markers only includes the first marker`() {
    val markers = captureLogMarkers {
      log.info(
          "Test",
          marker("duplicateKey", "value1"),
          marker("duplicateKey", "value2"),
          marker("duplicateKey", "value3"),
      )
    }

    markers shouldBe
        """
          "duplicateKey":"value1"
        """
            .trimIndent()
  }

  @Test
  fun `rawJsonMarker works for valid JSON`() {
    val userJson = """{"id":1,"name":"John Doe"}"""

    // The above JSON should work both for validJson = true and validJson = false
    for (assumeValidJson in listOf(true, false)) {
      withClue({ "assumeValidJson = ${assumeValidJson}" }) {
        val markers = captureLogMarkers {
          log.info(
              "Test",
              rawJsonMarker("user", userJson, validJson = assumeValidJson),
          )
        }

        markers shouldBe
            """
              "user":${userJson}
            """
                .trimIndent()
      }
    }
  }

  @Test
  fun `rawJsonMarker escapes invalid JSON by default`() {
    val invalidJson = """{"id":1"""

    val markers = captureLogMarkers {
      log.info(
          "Test",
          rawJsonMarker("user", invalidJson),
      )
    }

    markers shouldBe
        """
          "user":"{\"id\":1"
        """
            .trimIndent()
  }

  /**
   * When the user sets validJson = true on rawJsonMarker, they promise that the given JSON is
   * valid, so it should be passed on as-is. We therefore verify here that no validity checks are
   * made on the given JSON, although the user _should_ never pass invalid JSON to rawJsonMarker
   * like this.
   */
  @Test
  fun `rawJsonMarker does not escape invalid JSON when validJson is set to true`() {
    val invalidJson = """{"id":1"""

    val markers = captureLogMarkers {
      log.info(
          "Test",
          rawJsonMarker("user", invalidJson, validJson = true),
      )
    }

    markers shouldBe
        """
          "user":${invalidJson}
        """
            .trimIndent()
  }

  @Test
  fun `rawJsonMarker re-encodes JSON when it contains newlines`() {
    val jsonWithNewlines =
        """
          {
            "id": 1,
            "name": "John Doe"
          }
        """
            .trimIndent()

    val markers = captureLogMarkers {
      log.info(
          "Test",
          rawJsonMarker("user", jsonWithNewlines),
      )
    }

    markers shouldBe
        """
          "user":{"id":1,"name":"John Doe"}
        """
            .trimIndent()
  }
}

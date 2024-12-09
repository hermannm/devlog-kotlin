package dev.hermannm.devlog

import io.kotest.assertions.withClue
import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Encoder
import org.junit.jupiter.api.Test

val log = Logger {}

class LogMarkerTest {
  @Test
  fun `basic log marker test`() {
    val output = captureStdout {
      log.info(
          "Test",
          marker("testMarker", "value"),
      )
    }

    output shouldContain
        """
          "testMarker":"value"
        """
            .trimIndent()
  }

  @Test
  fun `log marker with Serializable object`() {
    @Serializable data class User(val id: Int, val name: String)

    val output = captureStdout {
      val user = User(id = 1, name = "hermannm")
      log.info(
          "Test",
          marker("user", user),
      )
    }

    output shouldContain
        """
          "user":{"id":1,"name":"hermannm"}
        """
            .trimIndent()
  }

  @Test
  fun `multiple log markers`() {
    val output = captureStdout {
      log.info(
          "Test",
          marker("first", true),
          marker("second", listOf("value1", "value2")),
          marker("third", 10),
      )
    }

    output shouldContain
        """
          "first":true,"second":["value1","value2"],"third":10
        """
            .trimIndent()
  }

  @Test
  fun `special-case types`() {
    val output = captureStdout {
      log.info(
          "Test",
          marker("instant", Instant.parse("2024-12-09T16:38:23Z")),
          marker("uri", URI.create("https://example.com")),
          marker("url", URL("https://example.com")),
          marker("uuid", UUID.fromString("3638dd04-d196-41ad-8b15-5188a22a6ba4")),
          marker("bigDecimal", BigDecimal("100.0")),
      )
    }

    output shouldContain """"instant":"2024-12-09T16:38:23Z""""
    output shouldContain """"uri":"https://example.com""""
    output shouldContain """"url":"https://example.com""""
    output shouldContain """"uuid":"3638dd04-d196-41ad-8b15-5188a22a6ba4""""
    output shouldContain """"bigDecimal":"100.0""""
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

    val output = captureStdout {
      log.info(
          "Test",
          marker("key", "value", serializer = prefixSerializer),
      )
    }

    output shouldContain
        """
          "key":"Prefix: value"
        """
            .trimIndent()
  }

  @Test
  fun `non-serializable object falls back to toString`() {
    data class User(val id: Int, val name: String)

    val output = captureStdout {
      val user = User(id = 1, name = "hermannm")
      log.info(
          "Test",
          marker("user", user),
      )
    }

    output shouldContain
        """
          "user":"User(id=1, name=hermannm)"
        """
            .trimIndent()
  }

  @Test
  fun `rawMarker works for valid JSON`() {
    val userJson = """{"id":1,"name":"hermannm"}"""

    // The above JSON should work both for validJson = true and validJson = false
    for (assumeValidJson in listOf(true, false)) {
      withClue({ "assumeValidJson = ${assumeValidJson}" }) {
        val output = captureStdout {
          log.info(
              "Test",
              rawMarker("user", userJson, validJson = assumeValidJson),
          )
        }

        output shouldContain
            """
              "user":${userJson}
            """
                .trimIndent()
      }
    }
  }

  @Test
  fun `rawMarker escapes invalid JSON by default`() {
    val invalidJson = """{"id":1"""

    val output = captureStdout {
      log.info(
          "Test",
          rawMarker("user", invalidJson),
      )
    }

    output shouldContain
        """
          "user":"{\"id\":1"
        """
            .trimIndent()
  }

  /**
   * When the user sets validJson = true on rawMarker, they promise that the given JSON is valid, so
   * it should be passed on as-is. We therefore verify here that no validity checks are made on the
   * given JSON, although the user _should_ never pass invalid JSON to rawMarker like this.
   */
  @Test
  fun `rawMarker does not escape invalid JSON when validJson is set to true`() {
    val invalidJson = """{"id":1"""

    val output = captureStdout {
      log.info(
          "Test",
          rawMarker("user", invalidJson, validJson = true),
      )
    }

    output shouldContain
        """
          "user":${invalidJson}
        """
            .trimIndent()
  }

  @Test
  fun `rawMarker re-encodes JSON when it contains newlines`() {
    val jsonWithNewlines =
        """
          {
            "id": 1,
            "name": "hermannm"
          }
        """
            .trimIndent()

    val output = captureStdout {
      log.info(
          "Test",
          rawMarker("user", jsonWithNewlines),
      )
    }

    output shouldContain
        """
          "user":{"id":1,"name":"hermannm"}
        """
            .trimIndent()
  }
}

package dev.hermannm.devlog

import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream
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
            encoder.encodeString("Prefix: $value")
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

  /**
   * Since we have configured logback in resources/logback-test.xml to use the Logback Logstash JSON
   * encoder, we can verify in our tests that markers have the expected JSON output.
   */
  private inline fun captureStdout(block: () -> Unit): String {
    val originalStdout = System.out

    val outputStream = ByteArrayOutputStream()
    System.setOut(PrintStream(outputStream))

    try {
      block()
    } finally {
      System.setOut(originalStdout)
    }

    return outputStream.toString("UTF-8")
  }
}

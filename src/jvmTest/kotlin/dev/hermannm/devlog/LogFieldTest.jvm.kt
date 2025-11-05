package dev.hermannm.devlog

import dev.hermannm.devlog.testutils.captureLogOutput
import dev.hermannm.devlog.testutils.runTestCases
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import tools.jackson.databind.ObjectMapper

private val log = getLogger()

internal class LogFieldJvmTest {
  @Test
  fun `special-case types`() {
    runTestCases(LogFieldTestCase.entries) { test ->
      val output = captureLogOutput {
        log.info {
          test.addField(this, "instant", Instant.parse("2024-12-09T16:38:23Z"))
          test.addField(this, "uri", URI.create("https://example.com"))
          @Suppress("DEPRECATION") test.addField(this, "url", URL("https://example.com"))
          test.addField(this, "uuid", UUID.fromString("3638dd04-d196-41ad-8b15-5188a22a6ba4"))
          test.addField(this, "bigDecimal", BigDecimal("100.0"))
          "Test"
        }
      }

      output.logFields shouldContain """"instant":"2024-12-09T16:38:23Z""""
      output.logFields shouldContain """"uri":"https://example.com""""
      output.logFields shouldContain """"url":"https://example.com""""
      output.logFields shouldContain """"uuid":"3638dd04-d196-41ad-8b15-5188a22a6ba4""""
      output.logFields shouldContain """"bigDecimal":"100.0""""
    }
  }

  private val jacksonObjectMapper = ObjectMapper()

  @Test
  fun `ValidRawJson is serializable with Jackson`() {
    val validRawJson = rawJson("""{"test":true}""")
    validRawJson.shouldBeInstanceOf<ValidRawJson>()

    val serializedValue = jacksonObjectMapper.writeValueAsString(validRawJson)
    serializedValue.shouldBe("""{"test":true}""")
  }

  @Test
  fun `NotValidJson is serializable with Jackson`() {
    val invalidJson = rawJson("""{"key":valueWithoutQuotes}""")
    invalidJson.shouldBeInstanceOf<NotValidJson>()

    val serializedValue = jacksonObjectMapper.writeValueAsString(invalidJson)
    serializedValue.shouldBe(
        """
        "{\"key\":valueWithoutQuotes}"
        """
            .trimIndent(),
    )
  }
}

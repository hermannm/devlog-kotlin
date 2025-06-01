package dev.hermannm.devlog

import io.kotest.matchers.string.shouldContain
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

private val log = getLogger {}

internal class LogFieldJvmTest {
  @Test
  fun `special-case types`() {
    val output = captureLogOutput {
      log.info {
        field("instant", Instant.parse("2024-12-09T16:38:23Z"))
        field("uri", URI.create("https://example.com"))
        @Suppress("DEPRECATION") field("url", URL("https://example.com"))
        field("uuid", UUID.fromString("3638dd04-d196-41ad-8b15-5188a22a6ba4"))
        field("bigDecimal", BigDecimal("100.0"))
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

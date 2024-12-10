package dev.hermannm.devlog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val log = Logger {}

class LoggingContextTest {
  @Test
  fun `marker from logging context is included in log`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("key", "value"),
      ) {
        log.info("Test")
      }
    }

    markers shouldBe
        """
          "key":"value"
        """
            .trimIndent()
  }

  @Test
  fun `multiple context markers combined with log marker have expected order`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("contextMarker1", "value"),
          marker("contextMarker2", "value"),
      ) {
        log.info(
            "Test",
            marker("logMarker", "value"),
        )
      }
    }

    markers shouldBe
        """
          "logMarker":"value","contextMarker1":"value","contextMarker2":"value"
        """
            .trimIndent()
  }

  @Test
  fun `duplicate context marker keys only includes the newest marker`() {
    var markersFromInnerContext: String? = null
    // We want to verify that after exiting the inner logging context, the markers from the outer
    // context are used again
    var markersFromOuterContext: String? = null

    withLoggingContext(
        marker("duplicateKey", "outer"),
    ) {
      withLoggingContext(
          marker("duplicateKey", "inner1"),
          marker("duplicateKey", "inner2"),
      ) {
        markersFromInnerContext = captureLogMarkers { log.info("Test") }
      }

      markersFromOuterContext = captureLogMarkers { log.info("Test") }
    }

    markersFromInnerContext shouldBe
        """
          "duplicateKey":"inner1"
        """
            .trimIndent()

    markersFromOuterContext shouldBe
        """
          "duplicateKey":"outer"
        """
            .trimIndent()
  }
}

package dev.hermannm.devlog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val log = Logger {}

class ExceptionWithLogMarkersTest {
  @Test
  fun `exception implementing WithLogMarkers has marker included in log`() {
    val markers = captureLogMarkers {
      log.error("Test", cause = exceptionWithLogMarker(marker("errorCode", 60)))
    }

    markers shouldBe
        """
          "errorCode":60
        """
            .trimIndent()
  }

  @Test
  fun `log markers from WithLogMarkers are placed between context and log entry markers`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("contextMarker", "value"),
      ) {
        log.error(
            "Test",
            marker("logEntryMarker", "value"),
            cause = exceptionWithLogMarker(marker("exceptionMarker", "value")),
        )
      }
    }

    markers shouldBe
        """
          "logEntryMarker":"value","exceptionMarker":"value","contextMarker":"value"
        """
            .trimIndent()
  }

  @Test
  fun `nested exception that implements WithLogMarkers`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          cause =
              Exception(
                  "Parent exception",
                  exceptionWithLogMarker(marker("nestedException", true)),
              ),
      )
    }

    markers shouldBe
        """
          "nestedException":true
        """
            .trimIndent()
  }

  @Test
  fun `parent and child exceptions that both implement WithLogMarkers have their markers merged`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          cause =
              ExceptionWithLogMarkers(
                  message = "Parent exception",
                  logMarkers =
                      listOf(
                          marker("parentMarker1", "value"),
                          marker("parentMarker2", "value"),
                      ),
                  cause = exceptionWithLogMarker(marker("childMarker", "value")),
              ),
      )
    }

    markers shouldBe
        """
          "parentMarker1":"value","parentMarker2":"value","childMarker":"value"
        """
            .trimIndent()
  }

  @Test
  fun `exception with duplicate log markers logs first marker only`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          cause =
              ExceptionWithLogMarkers(
                  "Test",
                  listOf(
                      marker("duplicate", "value1"),
                  ),
                  cause = exceptionWithLogMarker(marker("duplicate", "value2")),
              ),
      )
    }

    markers shouldBe
        """
          "duplicate":"value1"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log markers is Log entry -> Exception -> Context, so log entry
   * marker should override exception marker
   */
  @Test
  fun `exception marker does not override duplicate log entry marker`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          marker("duplicate", "from log entry"),
          cause = exceptionWithLogMarker(marker("duplicate", "from exception")),
      )
    }

    markers shouldBe
        """
          "duplicate":"from log entry"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log markers is Log entry -> Exception -> Context, so log entry
   * marker should override exception marker
   */
  @Test
  fun `exception marker overrides duplicate context marker`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("duplicate", "from context"),
      ) {
        log.error(
            "Test",
            cause = exceptionWithLogMarker(marker("duplicate", "from exception")),
        )
      }
    }

    markers shouldBe
        """
          "duplicate":"from exception"
        """
            .trimIndent()
  }

  @Test
  fun `custom implementation of WithLogMarkers works`() {
    class CustomException : Exception(), WithLogMarkers {
      override val logMarkers =
          listOf(
              marker("key1", "value1"),
              marker("key2", "value2"),
          )
    }

    val markers = captureLogMarkers { log.error("Test", cause = CustomException()) }

    markers shouldBe
        """
          "key1":"value1","key2":"value2"
        """
            .trimIndent()
  }

  @Test
  fun `inheriting from ExceptionWithLogMarkers works`() {
    class CustomException :
        ExceptionWithLogMarkers(
            message = "Test",
            logMarkers = listOf(marker("key", "value")),
        )

    val markers = captureLogMarkers { log.error("Test", cause = CustomException()) }

    markers shouldBe
        """
          "key":"value"
        """
            .trimIndent()
  }
}

private fun exceptionWithLogMarker(marker: LogMarker) =
    ExceptionWithLogMarkers(
        message = "Test exception",
        logMarkers = listOf(marker),
    )

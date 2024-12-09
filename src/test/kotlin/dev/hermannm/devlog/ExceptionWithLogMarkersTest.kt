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
  fun `exception markers are placed between context and log event markers`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("contextMarker", "value"),
      ) {
        log.error(
            "Test",
            marker("logEventMarker", "value"),
            cause = exceptionWithLogMarker(marker("exceptionMarker", "value")),
        )
      }
    }

    markers shouldBe
        """
          "logEventMarker":"value","exceptionMarker":"value","contextMarker":"value"
        """
            .trimIndent()
  }

  @Test
  fun `child exception that implements WithLogMarkers`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          cause =
              Exception(
                  "Parent exception",
                  exceptionWithLogMarker(marker("childException", true)),
              ),
      )
    }

    markers shouldBe
        """
          "childException":true
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
                      marker("duplicateKey", "value1"),
                      marker("duplicateKey", "value2"),
                  ),
                  cause = exceptionWithLogMarker(marker("duplicateKey", "value3")),
              ),
      )
    }

    markers shouldBe
        """
          "duplicateKey":"value1"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log markers is Log event -> Exception -> Context, so log event
   * marker should override exception marker.
   */
  @Test
  fun `exception marker does not override duplicate log event marker`() {
    val markers = captureLogMarkers {
      log.error(
          "Test",
          marker("duplicateKey", "from log event"),
          cause = exceptionWithLogMarker(marker("duplicateKey", "from exception")),
      )
    }

    markers shouldBe
        """
          "duplicateKey":"from log event"
        """
            .trimIndent()
  }

  /**
   * Priority for duplicate keys in log markers is Log event -> Exception -> Context, so log event
   * marker should override exception marker.
   */
  @Test
  fun `exception marker overrides duplicate context marker`() {
    val markers = captureLogMarkers {
      withLoggingContext(
          marker("duplicateKey", "from context"),
      ) {
        log.error(
            "Test",
            cause = exceptionWithLogMarker(marker("duplicateKey", "from exception")),
        )
      }
    }

    markers shouldBe
        """
          "duplicateKey":"from exception"
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

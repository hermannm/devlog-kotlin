package dev.hermannm.devlog

import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Since we have configured Logback in resources/logback-test.xml to use the Logstash JSON encoder,
 * we can verify in our tests that markers have the expected JSON output.
 */
internal inline fun captureLogMarkers(block: () -> Unit): String {
  val originalStdout = System.out

  // We redirect System.out to our own output stream, so we can capture the log output
  val outputStream = ByteArrayOutputStream()
  System.setOut(PrintStream(outputStream))

  try {
    block()
  } finally {
    System.setOut(originalStdout)
  }

  val logOutput = outputStream.toString("UTF-8")
  // We expect each call to captureLogMarkers to capture just a single log line, so it should only
  // contain 1 newline. If we get more, that is likely an error and should fail our tests.
  logOutput shouldContainOnlyOnce "\n"

  // Markers are included at the end of the log entry, and the last field before the markers is
  // "level_value". We want our tests to assert on the contents of all markers, so we strip away
  // the non-marker fields here.
  val indexOfLevelValue = logOutput.indexOf("\"level_value\"")
  indexOfLevelValue shouldNotBe -1 // -1 = not found
  val indexOfCommaAfterLevelValue = logOutput.indexOf(',', startIndex = indexOfLevelValue)
  indexOfCommaAfterLevelValue shouldNotBe -1
  val markers =
      logOutput.substring(
          // Markers start after the comma after level_value
          startIndex = indexOfCommaAfterLevelValue + 1,
          // We want to drop the final }\n
          endIndex = logOutput.length - 2,
      )
  return markers
}

/**
 * Used in [LoggerTest] to test that the logger gets the expected name from the file it's
 * constructed in.
 */
internal val loggerConstructedInOtherFile = Logger {}

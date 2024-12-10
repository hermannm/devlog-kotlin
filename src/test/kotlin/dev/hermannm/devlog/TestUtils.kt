package dev.hermannm.devlog

import io.kotest.matchers.nulls.shouldNotBeNull
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

  var indexOfLastComma: Int? = null

  // Markers are included at the end of the log entry, and the last field before the markers is
  // either "level_value" or "stack_trace". We want our tests to assert on the contents of all
  // markers, so we strip away the non-marker fields here.
  val indexOfStackTrace = logOutput.indexOf("\"stack_trace\"")
  if (indexOfStackTrace == -1) {
    val indexOfLevelValue = logOutput.indexOf("\"level_value\"")
    indexOfLevelValue shouldNotBe -1 // -1 = not found
    indexOfLastComma = logOutput.indexOf(',', startIndex = indexOfLevelValue)
  } else {
    // We want to iterate past "stack_trace" and its string value, meaning we want to iterate until
    // we've passed 4 unescaped quotes
    var quoteCount = 0
    for (i in indexOfStackTrace until logOutput.length) {
      if (logOutput[i] == '"' && logOutput[i - 1] != '\\') {
        quoteCount++
      }

      if (quoteCount == 4) {
        indexOfLastComma = i + 1
        break
      }
    }
  }

  indexOfLastComma.shouldNotBeNull() shouldNotBe -1

  val markers =
      logOutput.substring(
          // Markers start after the comma after level_value
          startIndex = indexOfLastComma + 1,
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

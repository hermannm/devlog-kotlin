package dev.hermannm.devlog

import io.kotest.matchers.string.shouldContainOnlyOnce
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Since we have configured Logback in resources/logback-test.xml to use the Logstash JSON encoder,
 * we can verify in our tests that markers have the expected JSON output.
 */
internal inline fun captureStdout(block: () -> Unit): String {
  val originalStdout = System.out

  val outputStream = ByteArrayOutputStream()
  System.setOut(PrintStream(outputStream))

  try {
    block()
  } finally {
    System.setOut(originalStdout)
  }

  val output = outputStream.toString("UTF-8")
  // We expect each call to captureStdout to capture just a single log line, so it only contain 1
  // newline. If we get more, that is likely an error and should fail our tests.
  output shouldContainOnlyOnce "\n"
  return output
}

/**
 * Used in [LoggerTest] to test that the logger gets the expected name from the file it's
 * constructed in.
 */
internal val loggerConstructedInOtherFile = Logger {}

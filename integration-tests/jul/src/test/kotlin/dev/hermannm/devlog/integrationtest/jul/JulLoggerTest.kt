package dev.hermannm.devlog.integrationtest.jul

import dev.hermannm.devlog.getLogger
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

private val log = getLogger {}

/** Tests the slf4j-jdk14 implementation, which uses java.util.logging (commonly called JUL). */
class JulLoggerTest {
  @Test
  fun log() {
    @Serializable data class User(val id: Long, val name: String)

    val user = User(id = 1, name = "John Doe")

    // java.util.logging logger outputs to stderr by default
    val output = captureStderr {
      log.info {
        field("user", user)
        "Test"
      }
    }

    val indexOfPackageName =
        withClue({ "Log output:\n${output}" }) {
          output.indexOf("dev.hermannm.devlog") shouldNotBe -1 // -1 == not found
        }

    // Log output includes timestamp, which we can't test deterministically here - so we test output
    // from the package name (included in log output) up to the trailing newline
    output.substring(indexOfPackageName).removeSuffix("\n") shouldBe
        """
          dev.hermannm.devlog.integrationtest.jul.JulLoggerTest log
          INFO: Test [user={"id":1,"name":"John Doe"}]
        """
            .trimIndent()
  }

  @Test
  fun `Logback should not be on classpath`() {
    shouldThrowExactly<ClassNotFoundException> { Class.forName("ch.qos.logback.classic.Logger") }
  }
}

inline fun captureStderr(block: () -> Unit): String {
  val originalStderr = System.err

  // We redirect System.err to our own output stream, so we can capture the log output
  val outputStream = ByteArrayOutputStream()
  System.setErr(PrintStream(outputStream))

  try {
    block()
  } finally {
    System.setErr(originalStderr)
  }

  return outputStream.toString("UTF-8")
}

package dev.hermannm.devlog.integrationtest.jul

import dev.hermannm.devlog.getLogger
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

private val log = getLogger {}

/** Tests the slf4j-jdk14 implementation, which uses java.util.logging (commonly called JUL). */
class JulLoggerTest {
  @Test
  fun infoLog() {
    @Serializable data class User(val id: Long, val name: String)

    val user = User(id = 1, name = "John Doe")

    // JDK14 logger outputs to stderr by default
    val output = captureStderr {
      log.info {
        addField("user", user)
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
          dev.hermannm.devlog.integrationtest.jul.JulLoggerTest infoLog
          INFO: Test [user={"id":1,"name":"John Doe"}]
        """
            .trimIndent()
  }

  @Test
  fun `Logback should not be on classpath`() {
    var exception: Throwable? = null

    try {
      Class.forName("ch.qos.logback.classic.Logger")
    } catch (e: Throwable) {
      exception = e
    }

    exception.shouldNotBeNull().shouldBeInstanceOf<ClassNotFoundException>()
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

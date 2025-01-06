package dev.hermannm.devlog.integrationtest.logback

import dev.hermannm.devlog.getLogger
import dev.hermannm.devlog.rawJsonField
import dev.hermannm.devlog.withLoggingContext
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test

private val log = getLogger {}

class LogbackLoggerTest {
  @Test
  fun log() {
    @Serializable data class User(val id: Long, val name: String)

    val user = User(id = 1, name = "John Doe")

    val output = captureStdout {
      withLoggingContext(rawJsonField("contextField", """{"test":true}""")) {
        log.info {
          field("user", user)
          "Test"
        }
      }
    }

    output shouldContain """"level":"INFO""""
    output shouldContain """"message":"Test""""
    output shouldContain """"user":{"id":1,"name":"John Doe"}"""
    output shouldContain """"contextField":{"test":true}"""
  }

  @Test
  fun `Logback should be on classpath`() {
    shouldNotThrowAny { Class.forName("ch.qos.logback.classic.Logger") }
    // We also want to make sure that logstash-logback-encoder is loaded
    shouldNotThrowAny {
      Class.forName("net.logstash.logback.composite.loggingevent.mdc.MdcEntryWriter")
    }
  }
}

inline fun captureStdout(block: () -> Unit): String {
  val originalStdout = System.out

  // We redirect System.err to our own output stream, so we can capture the log output
  val outputStream = ByteArrayOutputStream()
  System.setOut(PrintStream(outputStream))

  try {
    block()
  } finally {
    System.setOut(originalStdout)
  }

  return outputStream.toString("UTF-8")
}

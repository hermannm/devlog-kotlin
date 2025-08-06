@file:JvmName("PrettyLogEncoderJvm")

package dev.hermannm.devlog.output.logback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.core.encoder.EncoderBase
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import net.logstash.logback.encoder.StreamingEncoder
import org.slf4j.MDC
import org.slf4j.event.KeyValuePair

public class PrettyLogEncoder : EncoderBase<ILoggingEvent>(), StreamingEncoder<ILoggingEvent> {
  private var timeFormat: TimeFormat = TimeFormat.TIME_OF_DAY

  public fun setTimeFormat(format: TimeFormat) {
    this.timeFormat = format
  }

  private var includeLoggerName: Boolean = true

  public fun setIncludeLoggerName(value: Boolean) {
    this.includeLoggerName = value
  }

  private val lineSeparator = System.lineSeparator()
  private val colorsEnabled = true

  override fun encode(event: ILoggingEvent, outputStream: OutputStream) {
    val output = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)

    writeTime(event.instant, output)
    output.write(' ')
    writeLevel(event.level, output)

    if (includeLoggerName) {
      output.write(' ')
      writeLoggerName(event.loggerName, output)
    } else {
      output.withColor(Colors.GRAY) { output.write(':') }
    }

    output.write(' ')
    output.write(event.formattedMessage)
    output.write(lineSeparator)

    val logFields = event.keyValuePairs
    if (!logFields.isNullOrEmpty()) {
      writeLogFields(logFields, output)
    }

    val loggingContext = MDC.getCopyOfContextMap()
    if (!loggingContext.isNullOrEmpty()) {
      writeLoggingContext(loggingContext, output)
    }

    val causeException = event.throwableProxy
    if (causeException != null) {
      writeCauseException(causeException, output)
    }

    output.flush()
  }

  private fun writeTime(time: Instant, output: OutputStreamWriter) {
    val localTime = time.atZone(ZoneId.systemDefault())

    output.withColor(Colors.GRAY) {
      output.write('[')

      when (timeFormat) {
        TimeFormat.DATE_TIME -> writeDateTime(localTime, output)
        TimeFormat.TIME_OF_DAY -> writeTimeOfDay(localTime, output)
      }

      output.write(']')
    }
  }

  private fun writeDateTime(time: ZonedDateTime, output: OutputStreamWriter) {
    output.write(time.year.toString().padStart(4, '0'))
    output.write('-')
    output.write(time.monthValue.toString().padStart(2, '0'))
    output.write('-')
    output.write(time.dayOfMonth.toString().padStart(2, '0'))
    output.write(' ')

    writeTimeOfDay(time, output)
  }

  private fun writeTimeOfDay(time: ZonedDateTime, output: OutputStreamWriter) {
    output.write(time.hour.toString().padStart(2, '0'))
    output.write(':')
    output.write(time.minute.toString().padStart(2, '0'))
    output.write(':')
    output.write(time.second.toString().padStart(2, '0'))
  }

  private fun writeLevel(level: Level, output: OutputStreamWriter) {
    if (!colorsEnabled) {
      output.write(level.levelStr)
      return
    }

    val levelColor =
        when {
          level.levelInt >= Level.ERROR_INT -> Colors.RED
          level.levelInt >= Level.WARN_INT -> Colors.YELLOW
          level.levelInt >= Level.INFO_INT -> Colors.GREEN
          level.levelInt >= Level.DEBUG_INT -> Colors.MAGENTA
          level.levelInt >= Level.TRACE_INT -> Colors.CYAN
          else -> Colors.GRAY
        }

    output.withColor(levelColor) { output.write(level.levelStr) }
  }

  private fun writeLoggerName(loggerName: String, output: OutputStreamWriter) {
    output.withColor(Colors.GRAY) {
      output.write(loggerName)
      output.write(':')
    }
  }

  private fun writeLogFields(fields: List<KeyValuePair>, output: OutputStreamWriter) {
    for (field in fields) {
      writeLogField(field.key, field.value.toString(), output)
    }
  }

  private fun writeLoggingContext(
      loggingContext: Map<String, String?>,
      output: OutputStreamWriter
  ) {
    for ((key, value) in loggingContext) {
      writeLogField(key, value = value ?: "null", output)
    }
  }

  private fun writeLogField(key: String, value: String, output: OutputStreamWriter) {
    writeLogFieldKey(key, output)
    output.write(' ')
    output.write(value)
    output.write(lineSeparator)
  }

  private fun writeLogFieldKey(key: String, output: OutputStreamWriter) {
    output.write("    ")

    output.withColor(Colors.CYAN) { output.write(key) }
    output.withColor(Colors.GRAY) { output.write(':') }
  }

  private fun writeCauseException(rootException: IThrowableProxy, output: OutputStreamWriter) {
    writeLogFieldKey("cause", output)

    val multipleExceptions = rootException.cause != null
    if (multipleExceptions) {
      output.write(lineSeparator)
    } else {
      output.write(' ')
    }

    var exception: IThrowableProxy? = rootException
    while (exception != null) {
      if (multipleExceptions) {
        output.write("      ")
        output.withColor(Colors.GRAY) { output.write('-') }
        output.write(' ')
      }

      output.withColor(Colors.RED) {
        output.write(exception.className)
        output.write(':')
        output.write(' ')
        output.write(exception.message)
      }

      output.write(lineSeparator)

      for (stackTraceElement in exception.stackTraceElementProxyArray) {
        if (multipleExceptions) {
          output.write("          ")
        } else {
          output.write("             ")
        }

        output.withColor(Colors.GRAY) {
          output.write("at ")
          output.write(stackTraceElement.stackTraceElement.toString())
          output.write(lineSeparator)
        }
      }

      exception = exception.cause
    }
  }

  @OptIn(ExperimentalContracts::class)
  private inline fun OutputStreamWriter.withColor(color: String, crossinline block: () -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    if (colorsEnabled) {
      this.write(color)
      block()
      this.write(Colors.RESET)
    } else {
      block()
    }
  }

  override fun encode(event: ILoggingEvent): ByteArray {
    val buffer = ByteArrayOutputStream(1024)
    encode(event, buffer)
    return buffer.toByteArray()
  }

  override fun headerBytes(): ByteArray? = null

  override fun footerBytes(): ByteArray? = null
}

public enum class TimeFormat {
  DATE_TIME,
  TIME_OF_DAY,
}

private fun OutputStreamWriter.write(char: Char) {
  this.write(char.code)
}

private object Colors {
  const val RESET = "\u001B[0m"
  const val RED = "\u001B[31m"
  const val GREEN = "\u001B[32m"
  const val YELLOW = "\u001B[33m"
  const val MAGENTA = "\u001B[35m"
  const val CYAN = "\u001B[36m"
  const val GRAY = "\u001B[37m"
}

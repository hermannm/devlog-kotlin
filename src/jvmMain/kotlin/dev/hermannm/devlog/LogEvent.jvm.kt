@file:JvmName("LogEventJvm")

package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.LoggingEvent as BaseLogbackEvent
import ch.qos.logback.classic.spi.PackagingDataCalculator
import ch.qos.logback.classic.spi.StackTraceElementProxy
import ch.qos.logback.classic.spi.ThrowableProxy
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializable
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import java.util.Collections
import java.util.IdentityHashMap
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.event.DefaultLoggingEvent as BaseSlf4jEvent
import org.slf4j.event.KeyValuePair
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.spi.LocationAwareLogger
import org.slf4j.spi.LoggingEventAware

@PublishedApi
internal actual fun createLogEvent(level: LogLevel, logger: Slf4jLogger): LogEvent {
  if (LOGBACK_IS_ON_CLASSPATH && logger is LogbackLogger) {
    return LogbackLogEvent(level, logger)
  }

  return Slf4jLogEvent(level, logger)
}

/**
 * We want to support using this library without having Logback on the classpath at all (hence we
 * mark it as an optional dependency in the POM). This is because if the user has chosen a different
 * SLF4J implementation, loading Logback can interfere with that.
 *
 * If the user has not added Logback as a dependency, the below class loading will fail, and we'll
 * stick to only using SLF4J. We cache the result in this field instead of doing the try/catch every
 * time in [createLogEvent], as that would pay the cost of the exception every time for non-Logback
 * implementations.
 */
internal val LOGBACK_IS_ON_CLASSPATH =
    try {
      Class.forName("ch.qos.logback.classic.Logger")
      true
    } catch (_: Throwable) {
      false
    }

/** Extends SLF4J's log event class to implement [LogEvent]. */
internal class Slf4jLogEvent(
    level: LogLevel,
    logger: Slf4jLogger,
) : LogEvent, BaseSlf4jEvent(level.toSlf4j(), logger) {
  init {
    this.callerBoundary = FULLY_QUALIFIED_CLASS_NAME
    this.timeStamp = System.currentTimeMillis()
  }

  override fun setCause(cause: Throwable, logger: Slf4jLogger, logBuilder: LogBuilder) {
    this.throwable = cause
  }

  override fun addStringField(key: String, value: String) {
    if (!isFieldKeyAdded(key)) {
      addKeyValue(key, value)
    }
  }

  override fun addJsonField(key: String, json: String) {
    if (!isFieldKeyAdded(key)) {
      addKeyValue(key, RawJson(json))
    }
  }

  private fun isFieldKeyAdded(key: String): Boolean {
    return keyValuePairs?.any { it.key == key } ?: false
  }

  override fun log(message: String, logger: Slf4jLogger) {
    this.message = message

    overwriteDuplicateContextFieldsForLog(keyValuePairs)
    try {
      when (logger) {
        // If logger is LoggingEventAware, we can just log the event directly
        is LoggingEventAware -> logger.log(this)
        // If logger is LocationAware, we want to use that interface so the logger implementation
        // can show the correct file location of where the log was made
        is LocationAwareLogger -> logWithLocationAwareApi(logger)
        // Otherwise, we fall back to the base SLF4J Logger API
        else -> logWithBasicSlf4jApi(logger)
      }
    } finally {
      restoreContextFieldsOverwrittenForLog()
    }
  }

  private fun logWithLocationAwareApi(logger: LocationAwareLogger) {
    // Location-aware SLF4J API doesn't take KeyValuePair, so we must merge them into message
    val message = mergeMessageAndKeyValuePairs()
    logger.log(
        null, // marker (we don't use this)
        callerBoundary, // Fully qualified class name of class making log (set in constructor)
        level.toInt(),
        message,
        null, // argArray (we don't use this)
        throwable,
    )
  }

  private fun logWithBasicSlf4jApi(logger: Slf4jLogger) {
    // Basic SLF4J API doesn't take KeyValuePair, so we must merge them into message
    val message = mergeMessageAndKeyValuePairs()
    when (level) {
      // We don't assume that the SLF4J implementation accepts a `null` cause exception in the
      // overload that takes a throwable. So we only call that overload if `throwable != null`.
      Slf4jLevel.ERROR ->
          if (throwable == null) logger.error(message) else logger.error(message, throwable)
      Slf4jLevel.WARN ->
          if (throwable == null) logger.warn(message) else logger.warn(message, throwable)
      Slf4jLevel.INFO ->
          if (throwable == null) logger.info(message) else logger.info(message, throwable)
      Slf4jLevel.DEBUG ->
          if (throwable == null) logger.debug(message) else logger.debug(message, throwable)
      Slf4jLevel.TRACE ->
          if (throwable == null) logger.trace(message) else logger.trace(message, throwable)
    }
  }

  private fun mergeMessageAndKeyValuePairs(): String {
    val keyValuePairs = this.keyValuePairs
    // If there are no key-value pairs, we can just return the message as-is
    if (keyValuePairs.isNullOrEmpty()) {
      return message
    }

    val builder = StringBuilder()
    builder.append(message)

    builder.append(" [")
    keyValuePairs.forEachIndexed { index, keyValuePair ->
      builder.append(keyValuePair.key)
      builder.append('=')
      builder.append(keyValuePair.value)
      if (index != keyValuePairs.size - 1) {
        builder.append(", ")
      }
    }
    builder.append(']')

    return builder.toString()
  }

  /** We don't traverse the cause exception tree in this log event implementation. */
  override fun handlesExceptionTreeTraversal(): Boolean = false

  internal companion object {
    /** See [LogbackLogEvent.FULLY_QUALIFIED_CLASS_NAME]. */
    @JvmField internal val FULLY_QUALIFIED_CLASS_NAME = Slf4jLogEvent::class.java.name
  }
}

internal fun LogLevel.toSlf4j(): Slf4jLevel {
  return this.match(
      ERROR = { Slf4jLevel.ERROR },
      WARN = { Slf4jLevel.WARN },
      INFO = { Slf4jLevel.INFO },
      DEBUG = { Slf4jLevel.DEBUG },
      TRACE = { Slf4jLevel.TRACE },
  )
}

/** Extends Logback's custom log event class to implement [LogEvent]. */
internal class LogbackLogEvent(level: LogLevel, logger: LogbackLogger) :
    LogEvent,
    BaseLogbackEvent(
        FULLY_QUALIFIED_CLASS_NAME,
        logger,
        level.toLogback(),
        null, // message (we set this when finalizing the log)
        null, // cause (we set this in `setCause`)
        null, // argArray (we don't use this)
    ) {
  private var cause: CustomLogbackThrowableProxy? = null

  override fun setCause(cause: Throwable, logger: PlatformLogger, logBuilder: LogBuilder) {
    val cause = CustomLogbackThrowableProxy(cause, logBuilder)
    if (logger.asLogbackLogger().loggerContext.isPackagingDataEnabled) {
      cause.calculatePackageData()
    }
    this.cause = cause
  }

  override fun addStringField(key: String, value: String) {
    if (!isFieldKeyAdded(key)) {
      addKeyValuePair(KeyValuePair(key, value))
    }
  }

  override fun addJsonField(key: String, json: String) {
    if (!isFieldKeyAdded(key)) {
      addKeyValuePair(KeyValuePair(key, RawJson(json)))
    }
  }

  private fun isFieldKeyAdded(key: String): Boolean {
    return keyValuePairs?.any { it.key == key } ?: false
  }

  override fun log(message: String, logger: PlatformLogger) {
    this.message = message

    overwriteDuplicateContextFieldsForLog(keyValuePairs)
    try {
      logger.asLogbackLogger().callAppenders(this)
    } finally {
      restoreContextFieldsOverwrittenForLog()
    }
  }

  override fun getThrowableProxy(): IThrowableProxy? = cause

  /** Override to no-op, since we set our own [CustomLogbackThrowableProxy]. */
  override fun setThrowableProxy(tp: ThrowableProxy?) {}

  /**
   * We traverse the cause exception tree when constructing [CustomLogbackThrowableProxy] in
   * [setCause].
   */
  override fun handlesExceptionTreeTraversal(): Boolean = true

  internal companion object {
    /**
     * When a [LogbackLogEvent] is constructed, we know the `logger` is a [LogbackLogger]. So when
     * we receive the logger again in [setCause] and [log], we can safely cast it. It's fast to cast
     * to a concrete class, so we'd rather do that than increase the allocated size of the event by
     * adding a field.
     */
    @JvmStatic
    private fun PlatformLogger.asLogbackLogger(): LogbackLogger {
      return this as LogbackLogger
    }

    /**
     * SLF4J has the concept of a "caller boundary": the fully qualified class name of the logger
     * class that made the log. This is used by logger implementations, such as Logback, when the
     * user enables "caller info": showing the location in the source code where the log was made.
     * Logback then knows to exclude stack trace elements up to this caller boundary, since the user
     * wants to see where in _their_ code the log was made, not the location in the logging library.
     *
     * In our case, the caller boundary is in fact not [dev.hermannm.devlog.Logger], but our
     * [LogEvent] implementations. This is because all the methods on `Logger` are `inline` - so the
     * logger method actually called by user code at runtime is [LogbackLogEvent.log] /
     * [Slf4jLogEvent.log].
     */
    @JvmField internal val FULLY_QUALIFIED_CLASS_NAME = LogbackLogEvent::class.java.name
  }
}

internal fun LogLevel.toLogback(): LogbackLevel {
  return this.match(
      ERROR = { LogbackLevel.ERROR },
      WARN = { LogbackLevel.WARN },
      INFO = { LogbackLevel.INFO },
      DEBUG = { LogbackLevel.DEBUG },
      TRACE = { LogbackLevel.TRACE },
  )
}

/**
 * Implements Logback's [IThrowableProxy] interface to omit [LoggingContextProvider] from suppressed
 * exceptions (since we only use that exception to carry logging context, and we don't want it to
 * show up in the logged stack trace).
 */
internal class CustomLogbackThrowableProxy : IThrowableProxy {
  @JvmField internal val throwable: Throwable
  private val className: String
  private val message: String?
  private val stackTrace: Array<StackTraceElementProxy>
  private var cause: CustomLogbackThrowableProxy? = null
  private var suppressed: Array<CustomLogbackThrowableProxy> = EMPTY_SUPPRESSED_ARRAY
  private var commonFrames: Int = 0
  private var cyclic: Boolean
  private var packageDataCalculated = false

  constructor(
      throwable: Throwable,
      logBuilder: LogBuilder,
  ) : this(
      throwable,
      logBuilder,
      alreadyProcessed = Collections.newSetFromMap(IdentityHashMap()),
  )

  private constructor(
      throwable: Throwable,
      logBuilder: LogBuilder,
      alreadyProcessed: MutableSet<Throwable>,
      alreadyCheckedForLogFields: Boolean = false,
  ) {
    if (!alreadyCheckedForLogFields) {
      logBuilder.addFieldsFromException(throwable)
    }

    this.throwable = throwable
    this.className = throwable.javaClass.name
    this.message = throwable.message
    this.stackTrace = stackTraceToProxy(throwable.stackTrace)
    this.cyclic = false

    alreadyProcessed.add(throwable)

    val cause = throwable.cause
    if (cause != null) {
      if (alreadyProcessed.contains(cause)) {
        this.cause = CustomLogbackThrowableProxy(cause, isCyclic = true)
      } else {
        val causeProxy = CustomLogbackThrowableProxy(cause, logBuilder, alreadyProcessed)
        causeProxy.commonFrames = countCommonFrames(cause.stackTrace, this.stackTrace)
        this.cause = causeProxy
      }
    }

    val suppressed: Array<Throwable> = throwable.suppressed
    if (suppressed.isNotEmpty()) {
      var indexOfLoggingContextProvider = -1
      suppressed.forEachIndexed { index, suppressedThrowable ->
        when (suppressedThrowable) {
          is LoggingContextProvider -> {
            indexOfLoggingContextProvider = index
            suppressedThrowable.addFieldsToLog(logBuilder)
          }
          is ExceptionWithLoggingContext -> {
            suppressedThrowable.addFieldsToLog(logBuilder)
          }
          is HasLoggingContext -> {
            logBuilder.addFields(suppressedThrowable.logFields)
          }
        }
      }

      val hasLoggingContextProvider = indexOfLoggingContextProvider != -1
      val newArraySize = if (hasLoggingContextProvider) suppressed.size - 1 else suppressed.size

      this.suppressed =
          Array(newArraySize) { index ->
            val indexInOriginal =
                if (hasLoggingContextProvider && index >= indexOfLoggingContextProvider) {
                  index + 1
                } else {
                  index
                }
            val suppressedThrowable = suppressed[indexInOriginal]

            if (alreadyProcessed.contains(suppressedThrowable)) {
              return@Array CustomLogbackThrowableProxy(suppressedThrowable, isCyclic = true)
            }

            val suppressedProxy =
                CustomLogbackThrowableProxy(
                    suppressedThrowable,
                    logBuilder,
                    alreadyProcessed,
                    // Since we check when looking for `indexOfLoggingContextProvider` above
                    alreadyCheckedForLogFields = true,
                )
            suppressedProxy.commonFrames =
                countCommonFrames(suppressedThrowable.stackTrace, this.stackTrace)
            return@Array suppressedProxy
          }
    }
  }

  private constructor(throwable: Throwable, @Suppress("unused") isCyclic: Boolean) {
    this.throwable = throwable
    this.className = throwable.javaClass.name
    this.message = throwable.message
    this.stackTrace = EMPTY_STACK_TRACE
    this.cyclic = true
  }

  fun calculatePackageData() {
    if (!packageDataCalculated) {
      packageDataCalculated = true
      PackagingDataCalculator().calculate(this)
    }
  }

  override fun getMessage(): String? = message

  override fun getClassName(): String = className

  override fun getStackTraceElementProxyArray(): Array<out StackTraceElementProxy> = stackTrace

  override fun getCommonFrames(): Int = commonFrames

  override fun getCause(): IThrowableProxy? = cause

  override fun getSuppressed(): Array<out IThrowableProxy> = suppressed

  override fun isCyclic(): Boolean = cyclic

  private companion object {
    private val EMPTY_SUPPRESSED_ARRAY: Array<CustomLogbackThrowableProxy> = emptyArray()
    private val EMPTY_STACK_TRACE: Array<StackTraceElementProxy> = emptyArray()

    @JvmStatic
    private fun stackTraceToProxy(
        stackTrace: Array<StackTraceElement>
    ): Array<StackTraceElementProxy> {
      if (stackTrace.isEmpty()) {
        return EMPTY_STACK_TRACE
      } else {
        return Array(stackTrace.size) { index -> StackTraceElementProxy(stackTrace[index]) }
      }
    }

    @JvmStatic
    private fun countCommonFrames(
        childStackTrace: Array<StackTraceElement>,
        parentStackTrace: Array<StackTraceElementProxy>
    ): Int {
      var commonFrames = 0
      var childIndex = childStackTrace.size - 1
      var parentIndex = parentStackTrace.size - 1
      while (childIndex >= 0 && parentIndex >= 0) {
        val childStackTraceElement = childStackTrace[childIndex]
        val parentStackTraceElement = parentStackTrace[parentIndex].stackTraceElement
        if (childStackTraceElement == parentStackTraceElement) {
          commonFrames++
        } else {
          break
        }
        childIndex--
        parentIndex--
      }
      return commonFrames
    }
  }
}

/**
 * Wrapper class for a pre-serialized JSON string. It implements [JsonSerializable] from Jackson,
 * because most JSON-outputting logger implementations will use that library to encode the logs (at
 * least `logstash-logback-encoder` for Logback does this).
 *
 * Since we use this to wrap a value that has already been serialized with `kotlinx.serialization`,
 * we simply call [JsonGenerator.writeRawValue] in [serialize] to write the JSON string as-is.
 */
internal class RawJson(private val json: String) : JsonSerializable {
  override fun toString() = json

  override fun serialize(generator: JsonGenerator, serializers: SerializerProvider) {
    generator.writeRawValue(json)
  }

  override fun serializeWithType(
      generator: JsonGenerator,
      serializers: SerializerProvider,
      typeSerializer: TypeSerializer
  ) {
    // Since we don't know what type the raw JSON is, we can only redirect to normal serialization
    serialize(generator, serializers)
  }

  override fun equals(other: Any?) = other is RawJson && other.json == this.json

  override fun hashCode() = json.hashCode()
}

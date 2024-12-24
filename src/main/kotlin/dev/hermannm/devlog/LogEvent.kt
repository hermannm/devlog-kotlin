package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.LoggingEvent as BaseLogbackEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.event.DefaultLoggingEvent as BaseSlf4jEvent
import org.slf4j.event.KeyValuePair
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.spi.LoggingEventAware

/**
 * The purpose of [LogBuilder] is to build a log event. We could store intermediate state on
 * `LogBuilder`, and then use that to construct a log event when the builder is finished. But if we
 * instead construct the log event in-place on `LogBuilder`, we can avoid allocations on the hot
 * path.
 *
 * SLF4J has support for building log events, through the [org.slf4j.event.LoggingEvent] interface,
 * [org.slf4j.event.DefaultLoggingEvent] implementation, and [LoggingEventAware] logger interface.
 * And [LogbackLogger] implements `LoggingEventAware` - great! Except Logback uses a different event
 * format internally, so in its implementation of [LoggingEventAware.log], it has to map from the
 * SLF4J event to its own event format. This allocates a new event, defating the purpose of
 * constructing our log event in-place on `LogBuilder`.
 *
 * So to optimize for the common SLF4J + Logback combination, we construct the log event on
 * Logback's format in [LogbackLogEvent], so we can log it directly. However, we still want to be
 * compatible with alternative SLF4J implementations, so we implement SLF4J's format in
 * [Slf4jLogEvent]. [LogEvent] is the common interface between the two, so that [LogBuilder] can
 * call this interface without having to care about the underlying implementation.
 */
@PublishedApi
internal interface LogEvent {
  /** Already implemented by [BaseLogbackEvent.setMessage] and [BaseSlf4jEvent.setMessage]. */
  fun setMessage(message: String)

  /**
   * Already implemented by [BaseSlf4jEvent.setThrowable], must be implemented for Logback using
   * [BaseLogbackEvent.setThrowableProxy].
   */
  fun setThrowable(cause: Throwable?)

  /**
   * Already implemented by [BaseSlf4jEvent.getThrowable], must be implemented for Logback using
   * [BaseLogbackEvent.getThrowableProxy].
   */
  fun getThrowable(): Throwable?

  /**
   * Already implemented by [BaseLogbackEvent.addKeyValuePair], must be implemented for SLF4J using
   * [BaseSlf4jEvent.addKeyValue].
   */
  fun addKeyValuePair(keyValue: KeyValuePair)

  /**
   * Returns null if no key-value pairs have been added yet.
   *
   * Already implemented by [BaseLogbackEvent.getKeyValuePairs] and
   * [BaseSlf4jEvent.getKeyValuePairs].
   */
  fun getKeyValuePairs(): List<KeyValuePair>?
}

/** Extends Logback's custom log event class to implement [LogEvent]. */
internal class LogbackLogEvent(
    level: LogbackLevel,
    logger: LogbackLogger,
    logHandlerClassName: String
) :
    LogEvent,
    BaseLogbackEvent(
        logHandlerClassName,
        logger,
        level,
        null, // message (we set this when finalizing the log)
        null, // throwable (may be set by LogBuilder)
        null, // argArray (we don't use this)
    ) {
  override fun setThrowable(cause: Throwable?) {
    /**
     * Passing null to [ThrowableProxy] will throw, so we must only call it if cause is not null. We
     * still want to allow passing null here, to support the case where the user has a cause
     * exception that may or not be null.
     *
     * Calling [BaseLogbackEvent.setThrowableProxy] twice on the same event will also throw - and at
     * the time of writing, there is no way to just overwrite the previous throwableProxy. We would
     * rather ignore the second cause exception than throw an exception from our logger method, so
     * we only set throwableProxy here if it has not already been set.
     */
    if (cause != null && throwableProxy == null) {
      setThrowableProxy(ThrowableProxy(cause))
    }
  }

  override fun getThrowable(): Throwable? = (throwableProxy as? ThrowableProxy)?.throwable
}

/** Extends SLF4J's log event class to implement [LogEvent]. */
internal class Slf4jLogEvent(level: Slf4jLevel, logger: Slf4jLogger, logHandlerClassName: String) :
    LogEvent,
    BaseSlf4jEvent(
        level,
        logger,
    ) {
  init {
    super.setCallerBoundary(logHandlerClassName)
  }

  override fun addKeyValuePair(keyValue: KeyValuePair) {
    super.addKeyValue(keyValue.key, keyValue.value)
  }

  internal fun mergeMessageAndKeyValuePairs(): String {
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
}

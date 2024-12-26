package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.LoggingEvent as BaseLogbackEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.event.DefaultLoggingEvent as BaseSlf4jEvent
import org.slf4j.event.KeyValuePair
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.spi.CallerBoundaryAware
import org.slf4j.spi.LoggingEventAware
import org.slf4j.spi.LoggingEventBuilder

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
 * SLF4J event to its own event format. This allocates a new event, defeating the purpose of
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
  companion object {
    @PublishedApi
    internal fun create(level: LogLevel, logger: Slf4jLogger): LogEvent {
      if (LOGBACK_IS_ON_CLASSPATH && logger is LogbackLogger) {
        return LogbackLogEvent(level, logger)
      }

      return Slf4jLogEvent(logger.makeLoggingEventBuilder(level.slf4jLevel))
    }

    /**
     * We want to support using this library without having Logback on the classpath at all (hence
     * we mark it as an optional dependency in the POM). This is because if the user has chosen a
     * different SLF4J implementation, loading Logback can interfere with that.
     *
     * If the user has not added Logback as a dependency, the below class loading will fail, and
     * we'll stick to only using SLF4J. We cache the result in this field instead of doing the
     * try/catch every time in [LogEvent.create], as that would pay the cost of the exception every
     * time for non-Logback implementations.
     */
    internal val LOGBACK_IS_ON_CLASSPATH =
        try {
          Class.forName("ch.qos.logback.classic.Logger")
          true
        } catch (_: Throwable) {
          false
        }
  }

  /** Already implemented by [BaseLogbackEvent.setMessage] and [BaseSlf4jEvent.setMessage]. */
  fun setMessage(message: String)

  /**
   * Already implemented by [BaseSlf4jEvent.setThrowable], must be implemented for Logback using
   * [BaseLogbackEvent.setThrowableProxy].
   */
  fun setCause(cause: Throwable?)

  /**
   * Already implemented by [BaseSlf4jEvent.getThrowable], must be implemented for Logback using
   * [BaseLogbackEvent.getThrowableProxy].
   */
  fun getCause(): Throwable?

  /**
   * Already implemented by [BaseLogbackEvent.addKeyValuePair], must be implemented for SLF4J using
   * [BaseSlf4jEvent.addKeyValue].
   */
  fun addKeyValuePair(keyValue: KeyValuePair)

  fun isKeyAdded(key: String): Boolean

  /** Must be implemented for both Logback and SLF4J. */
  fun log(logger: Slf4jLogger)
}

/** Extends Logback's custom log event class to implement [LogEvent]. */
internal class LogbackLogEvent(
    level: LogLevel,
    logger: LogbackLogger,
) :
    LogEvent,
    BaseLogbackEvent(
        FULLY_QUALIFIED_CLASS_NAME,
        logger,
        level.toLogback(),
        null, // message (we set this when finalizing the log)
        null, // throwable (may be set by LogBuilder)
        null, // argArray (we don't use this)
    ) {
  override fun setCause(cause: Throwable?) {
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

  override fun getCause(): Throwable? = (throwableProxy as? ThrowableProxy)?.throwable

  override fun isKeyAdded(key: String): Boolean {
    // keyValuePairs will be null if none have been added yet
    val addedFields = keyValuePairs ?: return false

    return addedFields.any { field -> field.key == key }
  }

  override fun log(logger: Slf4jLogger) {
    // Safe to cast here, since we only construct this event if the logger is a LogbackLogger.
    // We choose to cast instead of keeping the LogbackLogger as a field on the event, since casting
    // to a concrete class is fast, and we don't want to increase the allocated size of the event.
    (logger as LogbackLogger).callAppenders(this)
  }

  internal companion object {
    /**
     * SLF4J has the concept of a "caller boundary": the fully qualified class name of the logger
     * class that made the log. This is used by logger implementations, such as Logback, when the
     * user enables "caller info": showing the location in the source code where the log was made.
     * Logback then knows to exclude stack trace elements up to this caller boundary, since the user
     * wants to see where in _their_ code the log was made, not the location in the logging library.
     *
     * In our case, the caller boundary is in fact not [Logger], but our [LogEvent] implementations.
     * This is because all the methods on `Logger` are `inline` - so the logger method actually
     * called by user code at runtime is [LogbackLogEvent.log]/[Slf4jLogEvent.log].
     */
    internal val FULLY_QUALIFIED_CLASS_NAME = LogbackLogEvent::class.java.name
  }
}

/**
 * We use an extension function for converting a [LogLevel] to the Logback equivalent, instead of a
 * field on [LogLevel] (like we do for [Slf4jLevel]). This is to allow using this library without
 * Logback on the classpath (such as when using an alternative SLF4J implementation). In such cases,
 * loading Logback may interfere with the user's chosen SLF4J logger.
 */
internal fun LogLevel.toLogback(): LogbackLevel {
  return when (this) {
    LogLevel.INFO -> LogbackLevel.INFO
    LogLevel.WARN -> LogbackLevel.WARN
    LogLevel.ERROR -> LogbackLevel.ERROR
    LogLevel.DEBUG -> LogbackLevel.DEBUG
    LogLevel.TRACE -> LogbackLevel.TRACE
  }
}

/** Wraps SLF4J's log event builder interface to implement [LogEvent]. */
internal class Slf4jLogEvent(
    private val eventBuilder: LoggingEventBuilder,
    private var addedFieldKeys: ArrayList<String>? = null,
    private var cause: Throwable? = null,
) : LogEvent {
  init {
    if (eventBuilder is CallerBoundaryAware) {
      eventBuilder.setCallerBoundary(FULLY_QUALIFIED_CLASS_NAME)
    }
  }

  override fun setMessage(message: String) {
    eventBuilder.setMessage(message)
  }

  override fun getCause(): Throwable? = cause

  override fun setCause(cause: Throwable?) {
    eventBuilder.setCause(cause)
    this.cause = cause
  }

  override fun addKeyValuePair(keyValue: KeyValuePair) {
    eventBuilder.addKeyValue(keyValue.key, keyValue.value)

    if (addedFieldKeys == null) {
      addedFieldKeys = ArrayList(4)
    }
    // We know this can't be null here, since we never set this back to null after initializing
    addedFieldKeys!!.add(keyValue.key)
  }

  override fun isKeyAdded(key: String): Boolean {
    return addedFieldKeys?.contains(key) ?: false
  }

  override fun log(logger: Slf4jLogger) {
    eventBuilder.log()
  }

  internal companion object {
    /** See [LogbackLogEvent.FULLY_QUALIFIED_CLASS_NAME]. */
    internal val FULLY_QUALIFIED_CLASS_NAME = Slf4jLogEvent::class.java.name
  }
}

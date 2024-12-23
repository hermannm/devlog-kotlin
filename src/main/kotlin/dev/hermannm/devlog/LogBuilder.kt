package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.LoggingEvent as LogbackEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import kotlinx.serialization.SerializationStrategy
import net.logstash.logback.marker.SingleFieldAppendingMarker
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.Marker
import org.slf4j.event.DefaultLoggingEvent as Slf4jEvent
import org.slf4j.event.Level as Slf4jLevel
import org.slf4j.event.Level
import org.slf4j.spi.LocationAwareLogger
import org.slf4j.spi.LoggingEventAware

/**
 * Class used in the logging methods on [Logger], allowing you to set a [cause] exception and
 * [add structured key-value data][addField] to a log.
 *
 * ### Example
 *
 * ```
 * private val log = Logger {}
 *
 * fun example(user: User) {
 *   try {
 *     storeUser(user)
 *   } catch (e: Exception) {
 *     // The lambda argument passed to this logger method has a LogBuilder as its receiver, which
 *     // means that you can set `LogBuilder.cause` and call `LogBuilder.addField` in this scope.
 *     log.error {
 *       cause = e
 *       addField("user", user)
 *       "Failed to store user in database"
 *     }
 *   }
 * }
 * ```
 */
@JvmInline // Inline value class, since we just wrap a log event
value class LogBuilder
@PublishedApi
internal constructor(
    @PublishedApi internal val logEvent: LogEvent,
) {
  /**
   * Set this if the log was caused by an exception, to include the exception message and stack
   * trace in the log.
   *
   * This property should only be set once on a single log. If you set `cause` multiple times, only
   * the first non-null exception will be kept (this is due to a limitation in Logback's
   * LoggingEvent API).
   */
  var cause: Throwable?
    set(value) = logEvent.setThrowable(value)
    get() = logEvent.getThrowable()

  /**
   * Adds a [log field][LogField] (structured key-value data) to the log.
   *
   * When outputting logs as JSON, this becomes a field in the logged JSON object (see example
   * below). This allows you to filter and query on the field in the log analysis tool of your
   * choice, in a more structured manner than if you were to just use string concatenation.
   *
   * The value is serialized using `kotlinx.serialization`, so if you pass an object here, you
   * should make sure it is annotated with [@Serializable][kotlinx.serialization.Serializable].
   * Alternatively, you can pass your own serializer for the value. If serialization fails, we fall
   * back to calling `toString()` on the value.
   *
   * If you have a value that is already serialized, you should use [addRawJsonField] instead.
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.Logger
   * import kotlinx.serialization.Serializable
   *
   * private val log = Logger {}
   *
   * fun example() {
   *   val user = User(id = 1, name = "John Doe")
   *
   *   log.info {
   *     addField("user", user)
   *     "Registered new user"
   *   }
   * }
   *
   * @Serializable data class User(val id: Long, val name: String)
   * ```
   *
   * This gives the following output (using `logstash-logback-encoder`):
   * ```json
   * {
   *   "message": "Registered new user",
   *   "user": {
   *     "id": "1",
   *     "name": "John Doe"
   *   },
   *   // ...timestamp etc.
   * }
   * ```
   */
  inline fun <reified ValueT> addField(
      key: String,
      value: ValueT,
      serializer: SerializationStrategy<ValueT>? = null,
  ) {
    if (!keyAdded(key)) {
      logEvent.addMarker(createLogstashField(key, value, serializer))
    }
  }

  /**
   * Adds a [log field][LogField] (structured key-value data) to the log, with the given
   * pre-serialized JSON value.
   *
   * When outputting logs as JSON, this becomes a field in the logged JSON object (see example
   * below). This allows you to filter and query on the field in the log analysis tool of your
   * choice, in a more structured manner than if you were to just use string concatenation.
   *
   * By default, this function checks that the given JSON string is actually valid JSON. The reason
   * for this is that giving raw JSON to our log encoder when it is not in fact valid JSON can break
   * our logs. So if the given JSON string is not valid JSON, we escape it as a string. If you are
   * 100% sure that the given JSON string is valid, you can set [validJson] to true.
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.Logger
   *
   * private val log = Logger {}
   *
   * fun example() {
   *   val userJson = """{"id":1,"name":"John Doe"}"""
   *
   *   log.info {
   *     addRawJsonField("user", userJson)
   *     "Registered new user"
   *   }
   * }
   * ```
   *
   * This gives the following output (using `logstash-logback-encoder`):
   * ```json
   * {"message":"Registered new user","user":{"id":1,"name":"John Doe"},/* ...timestamp etc. */}
   * ```
   */
  fun addRawJsonField(key: String, json: String, validJson: Boolean = false) {
    if (!keyAdded(key)) {
      logEvent.addMarker(createRawJsonLogstashField(key, json, validJson))
    }
  }

  /**
   * Adds the given [log field][LogField] to the log. This is useful when you have a previously
   * constructed field from the [field][dev.hermannm.devlog.field]/[rawJsonField] functions.
   * - If you want to create a new field and add it to the log, you should instead call [addField]
   * - If you want to add the field to all logs within a scope, you should instead use
   *   [withLoggingContext]
   */
  fun addPreconstructedField(field: LogField) {
    if (!keyAdded(field.key)) {
      logEvent.addMarker(field.logstashField)
    }
  }

  @PublishedApi
  internal fun finalizeAndLog(message: String, logger: Slf4jLogger) {
    logEvent.setMessage(message)

    // Add fields from cause exception first, as we prioritize them over context fields
    addFieldsFromCauseException()
    addFieldsFromContext()

    logEvent.log(logger)
  }

  /** Adds log fields from [withLoggingContext]. */
  private fun addFieldsFromContext() {
    // Add context fields in reverse, so newest field shows first
    getLogFieldsFromContext().forEachReversed { logstashField ->
      // Don't add fields with keys that have already been added
      if (!keyAdded(logstashField.fieldName)) {
        logEvent.addMarker(logstashField)
      }
    }
  }

  /**
   * Checks if the log [cause] exception (or any of its own cause exceptions) implements the
   * [WithLogFields] interface, and if so, adds those fields.
   */
  private fun addFieldsFromCauseException() {
    // The `cause` here is the log event cause exception. But this exception may itself have a
    // `cause` exception, and that may have another one, and so on. We want to go through all these
    // exceptions to look for log field, so we re-assign this local variable as we iterate through.
    var exception = cause
    // Limit the depth of cause exceptions, so we don't expose ourselves to infinite loops.
    // This can happen if:
    // - exception1.cause -> exception2
    // - exception2.cause -> exception3
    // - exception3.cause -> exception1
    // We set max depth to 10, which should be high enough to not affect real users.
    var depth = 0
    while (exception != null && depth < 10) {
      if (exception is WithLogFields) {
        exception.logFields.forEach { field ->
          // Don't add fields with keys that have already been added
          if (!keyAdded(field.key)) {
            logEvent.addMarker(field.logstashField)
          }
        }
      }

      exception = exception.cause
      depth++
    }
  }

  @PublishedApi
  internal fun keyAdded(key: String): Boolean {
    val addedFields = logEvent.getMarkers() ?: return false

    return addedFields.any { logstashField ->
      logstashField is SingleFieldAppendingMarker && logstashField.fieldName == key
    }
  }

  internal companion object {
    /**
     * Passed to the [LogEvent] when logging to indicate which class made the log. The logger
     * implementation (e.g. Logback) can use this to set the correct location information on the
     * log, if the user has enabled caller data.
     */
    internal val FULLY_QUALIFIED_CLASS_NAME = LogBuilder::class.java.name
  }
}

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
 * SLF4J event to its own event format. Not only does this allocate a new event, it also
 * re-allocates the log marker list - this defeats the purpose of constructing our log event
 * in-place on `LogBuilder`.
 *
 * So to optimize for the common SLF4J + Logback combination, we construct the log event on
 * Logback's format in [LogEvent.Logback], so we can log it directly. However, we still want to be
 * compatible with alternative SLF4J implementations, so we implement SLF4J's format in
 * [LogEvent.Slf4j]. [LogEvent] is the common interface between the two, so that [LogBuilder] can
 * call this interface without having to care about the underlying implementation.
 */
@PublishedApi
internal sealed interface LogEvent {
  companion object {
    fun create(level: LogLevel, logger: Slf4jLogger): LogEvent {
      return when (logger) {
        is LogbackLogger -> Logback(level.logbackLevel, logger)
        else -> Slf4j(level.slf4jLevel, logger)
      }
    }
  }

  /** Already implemented by [LogbackEvent.setMessage] and [Slf4jEvent.setMessage]. */
  fun setMessage(message: String)

  /**
   * Already implemented by [Slf4jEvent.setThrowable], must be implemented for Logback using
   * [LogbackEvent.setThrowableProxy].
   */
  fun setThrowable(cause: Throwable?)

  /**
   * Already implemented by [Slf4jEvent.getThrowable], must be implemented for Logback using
   * [LogbackEvent.getThrowableProxy].
   */
  fun getThrowable(): Throwable?

  /** Already implemented by [LogbackEvent.addMarker] and [Slf4jEvent.addMarker]. */
  fun addMarker(marker: Marker)

  /**
   * Returns null if no markers have been added yet.
   *
   * Already implemented by [Slf4jEvent.getMarkers], must be implemented for Logback using
   * [LogbackEvent.getMarkerList].
   */
  fun getMarkers(): List<Marker>?

  /** Must be implemented for both Logback and SLF4J. */
  fun log(logger: Slf4jLogger)

  /** Extends Logback's custom log event class to implement [LogEvent]. */
  private class Logback(level: LogbackLevel, logger: LogbackLogger) :
      LogEvent,
      LogbackEvent(
          LogBuilder.FULLY_QUALIFIED_CLASS_NAME,
          logger,
          level,
          null, // message (we set this when finalizing the log)
          null, // throwable (may be set by LogBuilder)
          null, // argArray (we don't use this)
      ) {
    override fun setThrowable(cause: Throwable?) {
      /**
       * Passing null to [ThrowableProxy] will throw, so we must only call it if cause is not null.
       * We still want to allow passing null here, to support the case where the user has a cause
       * exception that may or not be null.
       *
       * Calling [LogbackEvent.setThrowableProxy] twice on the same event will also throw - and at
       * the time of writing, there is no way to just overwrite the previous throwableProxy. We
       * would rather ignore the second cause exception than throw an exception from our logger
       * method, so we only set throwableProxy here if it has not already been set.
       */
      if (cause != null && throwableProxy == null) {
        setThrowableProxy(ThrowableProxy(cause))
      }
    }

    override fun getThrowable(): Throwable? = (throwableProxy as? ThrowableProxy)?.throwable

    override fun getMarkers(): List<Marker>? = markerList

    override fun log(logger: Slf4jLogger) {
      // Safe to cast here, since we only construct this class when the logger is a LogbackLogger
      (logger as LogbackLogger).callAppenders(this)
    }
  }

  /** Extends SLF4J's log event class to implement [LogEvent]. */
  private class Slf4j(level: Slf4jLevel, logger: Slf4jLogger) :
      LogEvent,
      Slf4jEvent(
          level,
          logger,
      ) {
    init {
      super.setCallerBoundary(LogBuilder.FULLY_QUALIFIED_CLASS_NAME)
    }

    override fun log(logger: Slf4jLogger) {
      when (logger) {
        // If logger is LoggingEventAware, we can just log the event directly
        is LoggingEventAware -> logger.log(this)
        // If logger is LocationAware, we want to use that interface so the logger implementation
        // can show the correct file location of where the log was made
        is LocationAwareLogger -> logWithLocationAwareApi(logger)
        // Otherwise, we fall back to the base SLF4J Logger API
        else -> logWithBasicSlf4jApi(logger)
      }
    }

    private fun logWithLocationAwareApi(logger: LocationAwareLogger) {
      val message = mergeMessageAndMarkers()
      logger.log(
          null, // Marker (this old API only takes 1 marker, so we must merge them into message)
          callerBoundary, // Fully qualified class name of class making log (set in constructor)
          level.toInt(),
          message,
          null, // argArray (we don't use this)
          throwable,
      )
    }

    private fun logWithBasicSlf4jApi(logger: Slf4jLogger) {
      // Basic SLF4J API only takes 1 marker, so we must merge them into message
      val message = mergeMessageAndMarkers()
      // level should never be null here, since we pass it in the constructor
      when (level!!) {
        Level.INFO ->
            if (throwable == null) logger.info(message) else logger.info(message, throwable)
        Level.WARN ->
            if (throwable == null) logger.warn(message) else logger.warn(message, throwable)
        Level.ERROR ->
            if (throwable == null) logger.error(message) else logger.error(message, throwable)
        Level.DEBUG ->
            if (throwable == null) logger.debug(message) else logger.debug(message, throwable)
        Level.TRACE ->
            if (throwable == null) logger.trace(message) else logger.trace(message, throwable)
      }
    }

    private fun mergeMessageAndMarkers(): String {
      val markers = getMarkers()
      // If there are no markers, we can just return the message as-is
      if (markers.isNullOrEmpty()) {
        return message
      }

      val builder = StringBuilder()
      builder.append(message)

      builder.append(" [")
      markers.forEachIndexed { index, marker ->
        builder.append(marker)
        if (index != markers.size - 1) {
          builder.append(", ")
        }
      }
      builder.append(']')

      return builder.toString()
    }
  }
}

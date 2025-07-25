package dev.hermannm.devlog

/**
 * The purpose of [LogBuilder] is to build a log event. We could store intermediate state on
 * `LogBuilder`, and then use that to construct a log event when the builder is finished. But if we
 * instead construct the log event in-place on `LogBuilder`, we can avoid allocations on the hot
 * path.
 *
 * On the JVM, SLF4J has support for building log events, through the `LoggingEvent` interface,
 * `DefaultLoggingEvent` implementation, and `LoggingEventAware` logger interface. And Logback's
 * logger implements `LoggingEventAware` - great! Except Logback uses a different event format
 * internally, so in its implementation of `LoggingEventAware.log`, it has to map from the SLF4J
 * event to its own event format. This allocates a new event, defeating the purpose of constructing
 * our log event in-place on `LogBuilder`.
 *
 * So to optimize for the common SLF4J + Logback combination, we construct the log event on
 * Logback's format in `LogbackLogEvent`, so we can log it directly. However, we still want to be
 * compatible with alternative SLF4J implementations, so we implement SLF4J's format in
 * `Slf4jLogEvent`. [LogEvent] is the common interface between the two, so that [LogBuilder] can
 * call this interface without having to care about the underlying implementation.
 */
@PublishedApi
internal interface LogEvent {
  /**
   * @param logger We pass the logger so that the implementation has access to it if necessary (our
   *   `LogbackLogEvent` uses this to check if stack trace package data is configured).
   * @param logBuilder We the log builder so that the implementation may traverse the cause
   *   exception tree and add log fields if needed (see [handlesExceptionTreeTraversal]).
   */
  fun setCause(cause: Throwable, logger: PlatformLogger, logBuilder: LogBuilder)

  fun addStringField(key: String, value: String)

  fun addJsonField(key: String, json: String)

  fun isFieldKeyAdded(key: String): Boolean

  fun log(message: String, logger: PlatformLogger)

  /**
   * Normally, [LogBuilder.setCause] would traverse the tree of exceptions from a cause exception,
   * checking for [ExceptionWithLoggingContext] and [LoggingContextProvider]. But some `LogEvent`
   * implementations, namely our `LogbackLogEvent`, does its own exception traversal already.
   * Instead of traversing exceptions twice, we let the log event implementation set this flag to
   * true if it takes care of it itself.
   */
  fun handlesExceptionTreeTraversal(): Boolean
}

/**
 * Returns a platform-specific implementation of [LogEvent].
 *
 * On the JVM, this returns an SLF4J `LoggingEvent`, or a specialized optimized version for Logback
 * if Logback is used as the logging backend.
 */
@PublishedApi internal expect fun createLogEvent(level: LogLevel, logger: PlatformLogger): LogEvent

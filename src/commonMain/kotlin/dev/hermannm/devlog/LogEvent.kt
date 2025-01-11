@file:OptIn(InternalLoggingApi::class)

package dev.hermannm.devlog

/**
 * The purpose of [LogBuilder] is to build a log event. We could store intermediate state on
 * `LogBuilder`, and then use that to construct a log event when the builder is finished. But if we
 * instead construct the log event in-place on `LogBuilder`, we can avoid allocations on the hot
 * path.
 *
 * SLF4J has support for building log events, through the [org.slf4j.event.LoggingEvent] interface,
 * [org.slf4j.event.DefaultLoggingEvent] implementation, and [org.slf4j.spi.LoggingEventAware]
 * logger interface. And Logback's logger implements `LoggingEventAware` - great! Except Logback
 * uses a different event format internally, so in its implementation of `LoggingEventAware.log`, it
 * has to map from the SLF4J event to its own event format. This allocates a new event, defeating
 * the purpose of constructing our log event in-place on `LogBuilder`.
 *
 * So to optimize for the common SLF4J + Logback combination, we construct the log event on
 * Logback's format in `LogbackLogEvent`, so we can log it directly. However, we still want to be
 * compatible with alternative SLF4J implementations, so we implement SLF4J's format in
 * `Slf4jLogEvent`. [LogEvent] is the common interface between the two, so that [LogBuilder] can
 * call this interface without having to care about the underlying implementation.
 */
@PublishedApi
internal interface LogEvent {
  fun addStringField(key: String, value: String)

  fun addJsonField(key: String, json: String)

  fun isFieldKeyAdded(key: String): Boolean

  fun log(message: String, logger: PlatformLogger)
}

@PublishedApi
internal expect fun createLogEvent(
    level: LogLevel,
    cause: Throwable?,
    logger: PlatformLogger
): LogEvent

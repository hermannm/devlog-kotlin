package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.LoggingEvent as LogbackEvent
import org.slf4j.LoggerFactory

/**
 * A logger provides methods for logging at various log levels ([info], [warn], [error], [debug] and
 * [trace]). It has a given logger name, typically the same as the class that the logger is attached
 * to (e.g. `com.example.ExampleClass`), which is added to the log so you can see where it
 * originated from.
 *
 * The easiest way to construct a logger is by providing an empty lambda argument:
 * ```
 * private val log = Logger {}
 * ```
 *
 * This will automatically give the logger the name of its containing class. If it's at the top
 * level in a file, it will take the file name as if it were a class (e.g. a logger defined in
 * `Example.kt` in package `com.example` will get the name `com.example.Example`).
 *
 * Alternatively, you can provide a custom name to the logger:
 * ```
 * private val log = Logger(name = "com.example.Example")
 * ```
 */
@JvmInline // Use inline value class, to avoid redundant indirection when we just wrap Logback
value class Logger
internal constructor(
    @PublishedApi internal val logbackLogger: LogbackLogger,
) {
  constructor(name: String) : this(getLogbackLogger(name))

  constructor(function: () -> Unit) : this(name = getClassNameFromFunction(function))

  /**
   * Logs the message returned by the given function at the INFO log level, if it is enabled.
   *
   * You can add a cause exception by setting [cause][LogBuilder.cause] on the [LogBuilder] function
   * receiver, and add [log markers][LogMarker] by calling [LogBuilder.addMarker].
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.info {
   *     addMarker("user", user)
   *     "Registered new user"
   *   }
   * }
   * ```
   *
   * ### Note on file locations
   *
   * If you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then the log will show an incorrect line
   * number. This happens because [Logger]'s methods are `inline`, to avoid allocating a function
   * object for [buildLog]. Inline functions give incorrect line numbers, but we prioritize the
   * performance gain in this case. File, class and method names will still be correct.
   */
  inline fun info(buildLog: LogBuilder.() -> String) {
    logIfEnabled(LogLevel.INFO, buildLog)
  }

  /**
   * Logs the message returned by the given function at the INFO log level, if it is enabled.
   *
   * You can add a cause exception by setting [cause][LogBuilder.cause] on the [LogBuilder] function
   * receiver, and add [log markers][LogMarker] by calling [LogBuilder.addMarker].
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   try {
   *     sendWelcomeEmail(user)
   *   } catch (e: Exception) {
   *     log.warn {
   *       cause = e
   *       addMarker("user", user)
   *       "Failed to send welcome email to user"
   *     }
   *   }
   * }
   * ```
   *
   * ### Note on file locations
   *
   * If you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then the log will show an incorrect line
   * number. This happens because [Logger]'s methods are `inline`, to avoid allocating a function
   * object for [buildLog]. Inline functions give incorrect line numbers, but we prioritize the
   * performance gain in this case. File, class and method names will still be correct.
   */
  inline fun warn(buildLog: LogBuilder.() -> String) {
    logIfEnabled(LogLevel.WARN, buildLog)
  }

  /**
   * Logs the message returned by the given function at the INFO log level, if it is enabled.
   *
   * You can add a cause exception by setting [cause][LogBuilder.cause] on the [LogBuilder] function
   * receiver, and add [log markers][LogMarker] by calling [LogBuilder.addMarker].
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
   *     log.error {
   *       cause = e
   *       addMarker("user", user)
   *       "Failed to store user in database"
   *     }
   *   }
   * }
   * ```
   *
   * ### Note on file locations
   *
   * If you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then the log will show an incorrect line
   * number. This happens because [Logger]'s methods are `inline`, to avoid allocating a function
   * object for [buildLog]. Inline functions give incorrect line numbers, but we prioritize the
   * performance gain in this case. File, class and method names will still be correct.
   */
  inline fun error(buildLog: LogBuilder.() -> String) {
    logIfEnabled(LogLevel.ERROR, buildLog)
  }

  /**
   * Logs the message returned by the given function at the INFO log level, if it is enabled.
   *
   * You can add a cause exception by setting [cause][LogBuilder.cause] on the [LogBuilder] function
   * receiver, and add [log markers][LogMarker] by calling [LogBuilder.addMarker].
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.debug {
   *     addMarker("user", user)
   *     "Received new sign-up request"
   *   }
   * }
   * ```
   *
   * ### Note on file locations
   *
   * If you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then the log will show an incorrect line
   * number. This happens because [Logger]'s methods are `inline`, to avoid allocating a function
   * object for [buildLog]. Inline functions give incorrect line numbers, but we prioritize the
   * performance gain in this case. File, class and method names will still be correct.
   */
  inline fun debug(buildLog: LogBuilder.() -> String) {
    logIfEnabled(LogLevel.DEBUG, buildLog)
  }

  /**
   * Logs the message returned by the given function at the INFO log level, if it is enabled.
   *
   * You can add a cause exception by setting [cause][LogBuilder.cause] on the [LogBuilder] function
   * receiver, and add [log markers][LogMarker] by calling [LogBuilder.addMarker].
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.trace {
   *     addMarker("user", user)
   *     "Started processing user request"
   *   }
   * }
   * ```
   *
   * ### Note on file locations
   *
   * If you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then the log will show an incorrect line
   * number. This happens because [Logger]'s methods are `inline`, to avoid allocating a function
   * object for [buildLog]. Inline functions give incorrect line numbers, but we prioritize the
   * performance gain in this case. File, class and method names will still be correct.
   */
  inline fun trace(buildLog: LogBuilder.() -> String) {
    logIfEnabled(LogLevel.TRACE, buildLog)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the logger is enabled for
   * the given level.
   */
  @PublishedApi
  internal inline fun logIfEnabled(level: LogLevel, buildLog: LogBuilder.() -> String) {
    if (!logbackLogger.isEnabledFor(level.logbackLevel)) {
      return
    }

    // We want to call buildLog here in the inline method, to avoid allocating a function object for
    // it. But having too much code inline can be costly, so we use separate non-inline methods
    // for initialization and finalization of the log.
    val builder = initializeLogBuilder(level)
    builder.logEvent.message = builder.buildLog()
    log(builder)
  }

  @PublishedApi
  internal fun initializeLogBuilder(level: LogLevel): LogBuilder {
    return LogBuilder(
        logEvent =
            LogbackEvent(
                FULLY_QUALIFIED_CLASS_NAME,
                logbackLogger,
                level.logbackLevel,
                null, // message (we set this after calling buildLog)
                null, // throwable (may be set by buildLog)
                null, // argArray (we don't use this)
            ),
    )
  }

  /** Finalizes the log event from the given builder, and logs it. */
  @PublishedApi
  internal fun log(builder: LogBuilder) {
    builder.addMarkersFromContextAndCause()
    logbackLogger.callAppenders(builder.logEvent)
  }

  internal companion object {
    /**
     * Passed to the [LogbackEvent] when logging to indicate which class made the log. Logback uses
     * this to set the correct location information on the log, if the user has enabled caller data.
     */
    internal val FULLY_QUALIFIED_CLASS_NAME: String = Logger::class.java.name
  }
}

enum class LogLevel(
    @PublishedApi internal val logbackLevel: LogbackLevel,
) {
  INFO(LogbackLevel.INFO),
  WARN(LogbackLevel.WARN),
  ERROR(LogbackLevel.ERROR),
  DEBUG(LogbackLevel.DEBUG),
  TRACE(LogbackLevel.TRACE),
}

/**
 * Implementation based on the
 * [KLoggerNameResolver from kotlin-logging-jvm](https://github.com/oshai/kotlin-logging/blob/e9c6ec570cd503c626fca5878efcf1291d4125b7/src/jvmMain/kotlin/mu/internal/KLoggerNameResolver.kt).
 *
 * Licensed by Ohad Shai under
 * [Apache 2.0](https://github.com/oshai/kotlin-logging/blob/e9c6ec570cd503c626fca5878efcf1291d4125b7/LICENSE).
 */
internal fun getClassNameFromFunction(function: () -> Unit): String {
  val name = function.javaClass.name
  return when {
    name.contains("Kt$") -> name.substringBefore("Kt$")
    name.contains("$") -> name.substringBefore("$")
    else -> name
  }
}

/**
 * @throws IllegalStateException If [LoggerFactory.getLogger] does not return a Logback logger. This
 *   library is made to work with Logback only (since we use Logback-specific features such as raw
 *   JSON markers), so we want to fail loudly if Logback is not configured.
 */
internal fun getLogbackLogger(name: String): LogbackLogger {
  try {
    return LoggerFactory.getLogger(name) as LogbackLogger
  } catch (e: ClassCastException) {
    throw IllegalStateException(
        "Failed to get Logback logger - have you added logback-classic as a dependency?",
        e,
    )
  }
}

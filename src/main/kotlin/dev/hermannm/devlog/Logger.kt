package dev.hermannm.devlog

import ch.qos.logback.classic.Logger as LogbackLogger
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory
import org.slf4j.event.Level as Slf4jLogLevel
import org.slf4j.spi.LocationAwareLogger

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
class Logger
internal constructor(
    @PublishedApi // For use in inline functions
    internal val logbackLogger: LogbackLogger,
) {
  constructor(name: String) : this(getLogbackLogger(name))

  constructor(function: () -> Unit) : this(name = getClassNameFromFunction(function))

  /**
   * Logs the given message at the INFO log level (if enabled), along with any given
   * [log markers][LogMarker], and optionally a cause exception.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.info("Registered new user", marker("user", user))
   * }
   * ```
   */
  fun info(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    logIfEnabled(LogLevel.INFO, message, markers, cause)
  }

  /**
   * Logs the given message at the WARN log level (if enabled), along with any given
   * [log markers][LogMarker], and optionally a cause exception.
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
   *     log.warn(
   *         "Failed to send welcome email to user",
   *         marker("user", user),
   *         cause = e,
   *     )
   *   }
   * }
   * ```
   */
  fun warn(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    logIfEnabled(LogLevel.WARN, message, markers, cause)
  }

  /**
   * Logs the given message at the ERROR log level (if enabled), along with any given
   * [log markers][LogMarker], and optionally a cause exception.
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
   *     log.error(
   *         "Failed to store user in database",
   *         marker("user", user),
   *         cause = e,
   *     )
   *   }
   * }
   * ```
   */
  fun error(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    logIfEnabled(LogLevel.ERROR, message, markers, cause)
  }

  /**
   * Logs the given message at the DEBUG log level (if enabled), along with any given
   * [log markers][LogMarker], and optionally a cause exception.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.debug("Received new sign-up request", marker("user", user))
   * }
   * ```
   */
  fun debug(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    logIfEnabled(LogLevel.DEBUG, message, markers, cause)
  }

  /**
   * Logs the given message at the TRACE log level (if enabled), along with any given
   * [log markers][LogMarker], and optionally a cause exception.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.trace("Started processing user request", marker("user", user))
   * }
   * ```
   */
  fun trace(message: String, vararg markers: LogMarker, cause: Throwable? = null) {
    logIfEnabled(LogLevel.TRACE, message, markers, cause)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the INFO log level is
   * enabled. This can improve performance over [Logger.info] for cases where the log level may not
   * be enabled, and constructing the log message/markers is expensive.
   *
   * The log message is the string returned by the function. You can add [log markers][LogMarker]
   * and set a cause exception on the log by calling [addMarker][LogBuilder.addMarker] and
   * [setCause][LogBuilder.setCause] on the [LogBuilder] function receiver.
   *
   * Note that if you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then logs from lazy functions will show the
   * wrong line number. This is because we use an inline function, to avoid allocating an object for
   * the given function. We prioritize this performance gain over correct line numbers.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.infoLazy {
   *     addMarker("user", user)
   *     "Registered new user"
   *   }
   * }
   * ```
   */
  inline fun infoLazy(buildLog: LogBuilder.() -> String) {
    logLazy(LogLevel.INFO, buildLog)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the WARN log level is
   * enabled. This can improve performance over [Logger.warn] for cases where the log level may not
   * be enabled, and constructing the log message/markers is expensive.
   *
   * The log message is the string returned by the function. You can add [log markers][LogMarker]
   * and set a cause exception on the log by calling [addMarker][LogBuilder.addMarker] and
   * [setCause][LogBuilder.setCause] on the [LogBuilder] function receiver.
   *
   * Note that if you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then logs from lazy functions will show the
   * wrong line number. This is because we use an inline function, to avoid allocating an object for
   * the given function. We prioritize this performance gain over correct line numbers.
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
   *     log.warnLazy {
   *       setCause(e)
   *       addMarker("user", user)
   *       "Failed to send welcome email to user"
   *     }
   *   }
   * }
   * ```
   */
  inline fun warnLazy(buildLog: LogBuilder.() -> String) {
    logLazy(LogLevel.WARN, buildLog)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the ERROR log level is
   * enabled. This can improve performance over [Logger.error] for cases where the log level may not
   * be enabled, and constructing the log message/markers is expensive.
   *
   * The log message is the string returned by the function. You can add [log markers][LogMarker]
   * and set a cause exception on the log by calling [addMarker][LogBuilder.addMarker] and
   * [setCause][LogBuilder.setCause] on the [LogBuilder] function receiver.
   *
   * Note that if you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then logs from lazy functions will show the
   * wrong line number. This is because we use an inline function, to avoid allocating an object for
   * the given function. We prioritize this performance gain over correct line numbers.
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
   *     log.errorLazy {
   *       setCause(e)
   *       addMarker("user", user)
   *       "Failed to store user in database"
   *     }
   *   }
   * }
   * ```
   */
  inline fun errorLazy(buildLog: LogBuilder.() -> String) {
    logLazy(LogLevel.ERROR, buildLog)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the DEBUG log level is
   * enabled. This can improve performance over [Logger.debug] for cases where the log level may not
   * be enabled, and constructing the log message/markers is expensive.
   *
   * The log message is the string returned by the function. You can add [log markers][LogMarker]
   * and set a cause exception on the log by calling [addMarker][LogBuilder.addMarker] and
   * [setCause][LogBuilder.setCause] on the [LogBuilder] function receiver.
   *
   * Note that if you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then logs from lazy functions will show the
   * wrong line number. This is because we use an inline function, to avoid allocating an object for
   * the given function. We prioritize this performance gain over correct line numbers.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.debugLazy {
   *     addMarker("user", user)
   *     "Received new sign-up request"
   *   }
   * }
   * ```
   */
  inline fun debugLazy(buildLog: LogBuilder.() -> String) {
    logLazy(LogLevel.DEBUG, buildLog)
  }

  /**
   * Calls the given function to build a log event and log it, but only if the TRACE log level is
   * enabled. This can improve performance over [Logger.trace] for cases where the log level may not
   * be enabled, and constructing the log message/markers is expensive.
   *
   * The log message is the string returned by the function. You can add [log markers][LogMarker]
   * and set a cause exception on the log by calling [addMarker][LogBuilder.addMarker] and
   * [setCause][LogBuilder.setCause] on the [LogBuilder] function receiver.
   *
   * Note that if you include file location information in your log encoder (such as enabling
   * `includeCallerData` in `logstash-logback-encoder`), then logs from lazy functions will show the
   * wrong line number. This is because we use an inline function, to avoid allocating an object for
   * the given function. We prioritize this performance gain over correct line numbers.
   *
   * ### Example
   *
   * ```
   * private val log = Logger {}
   *
   * fun example(user: User) {
   *   log.traceLazy {
   *     addMarker("user", user)
   *     "Started processing user request"
   *   }
   * }
   * ```
   */
  inline fun traceLazy(buildLog: LogBuilder.() -> String) {
    logLazy(LogLevel.TRACE, buildLog)
  }

  /**
   * Logs the message, markers and cause exception at the given log level, if enabled.
   *
   * This method is kept private for now. If we find a need from library users to have a function
   * like this where they can pass the log level as a parameter, we should:
   * - Keep this implementation private
   * - Make [LogLevel] public, but keep its fields internal
   * - Add a new `log` function that calls `logIfEnabled`, but makes [markers] a vararg instead of
   *   an array, and with a default value of `null` for [cause]
   *     - The reason we use an array here instead of a vararg is for performance: Kotlin copies
   *       varargs into a new array for each vararg function, so if `Logger.info` calls
   *       `Logger.log`, and both take varargs, we would allocate 2 arrays instead of just one. When
   *       `Logger.log` takes an array instead, the vararg array from `Logger.info` can be passed
   *       directly. See
   *       [issue KT-17043](https://youtrack.jetbrains.com/issue/KT-17043/Do-not-create-new-arrays-for-pass-through-vararg-parameters).
   */
  private fun logIfEnabled(
      level: LogLevel,
      message: String,
      markers: Array<out LogMarker>,
      cause: Throwable?
  ) {
    if (!logbackLogger.isEnabledForLevel(level.slf4jLevel)) {
      return
    }

    // Call markers.asList() to wrap the Array as a List without copying
    logInternal(level, message, markers.asList(), cause)
  }

  /**
   * Calls the given function to construct a log, but only if the given log level is enabled.
   *
   * This method is kept internal for now. If we find a need from library users to have a function
   * like this where they can pass the log level as a parameter, we should make this and [LogLevel]
   * public, and follow the instructions on [logIfEnabled].
   */
  @PublishedApi // For use in inline functions
  internal inline fun logLazy(level: LogLevel, buildLog: LogBuilder.() -> String) {
    if (!logbackLogger.isEnabledForLevel(level.slf4jLevel)) {
      return
    }

    val builder = LogBuilder()
    val message = builder.buildLog()
    logInternal(level, message, builder.markers ?: emptyList(), builder.causeException)
  }

  /**
   * Shared implementation for [logIfEnabled] and [logLazy]. Should be kept internal (see
   * [logIfEnabled]).
   */
  @PublishedApi // For use in inline functions
  internal fun logInternal(
      level: LogLevel,
      message: String,
      markers: List<LogMarker>,
      cause: Throwable?
  ) {
    /**
     * Logback can be configured to output file/line information of where in the source code a log
     * occurred. But if we just call the normal SLF4J logger methods here, that would show this
     * class (Logger) as where the logs occurred - but what we actually want is to show where in the
     * user's code the log was made!
     *
     * To solve this problem, SLF4J provides a [LocationAwareLogger] interface, which Logback
     * implements. The interface has a [LocationAwareLogger.log] method that takes a fully qualified
     * class name, which Logback can then use to omit it from the file/line info.
     */
    logbackLogger.log(
        combineLogMarkers(markers, cause),
        FULLY_QUALIFIED_CLASS_NAME,
        level.intValue,
        message,
        null,
        cause,
    )
  }

  internal companion object {
    internal val FULLY_QUALIFIED_CLASS_NAME: String = Logger::class.java.name
  }
}

@PublishedApi // For use in inline functions
internal enum class LogLevel(
    @PublishedApi // For use in inline functions
    internal val slf4jLevel: Slf4jLogLevel,
    internal val intValue: Int,
) {
  INFO(Slf4jLogLevel.INFO, LocationAwareLogger.INFO_INT),
  WARN(Slf4jLogLevel.WARN, LocationAwareLogger.WARN_INT),
  ERROR(Slf4jLogLevel.ERROR, LocationAwareLogger.ERROR_INT),
  DEBUG(Slf4jLogLevel.DEBUG, LocationAwareLogger.DEBUG_INT),
  TRACE(Slf4jLogLevel.TRACE, LocationAwareLogger.TRACE_INT),
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

/**
 * [LocationAwareLogger.log] takes just a single log marker, so to pass multiple markers, we have to
 * combine them using [LogstashMarker.add].
 */
internal fun combineLogMarkers(markers: List<LogMarker>, cause: Throwable?): LogstashMarker? {
  val contextMarkers = getLogMarkersFromContext()
  val exceptionMarkers = getLogMarkersFromException(cause)

  // We have to combine the markers for this log event with the markers from the logging
  // context, and the cause exception if it implements WithLogMarkers. But we can avoid doing
  // this combination if there are no log markers, or if there is only 1 log marker among the
  // log event/context/exception markers.
  return when {
    markers.isEmpty() && contextMarkers.isEmpty() && exceptionMarkers.isEmpty() -> {
      null
    }
    markers.size == 1 && contextMarkers.isEmpty() && exceptionMarkers.isEmpty() -> {
      markers.first().logstashMarker
    }
    markers.isEmpty() && contextMarkers.size == 1 && exceptionMarkers.isEmpty() -> {
      contextMarkers.first().logstashMarker
    }
    markers.isEmpty() && contextMarkers.isEmpty() && exceptionMarkers.size == 1 -> {
      exceptionMarkers.first().logstashMarker
    }
    else -> {
      /**
       * This is how [Markers.aggregate] combines markers: create an empty marker, then add to it.
       * But that function takes a vararg or a Collection, which would require an additional
       * allocation from us here, since we want to add both the log event markers and the context
       * markers. So instead we make the empty marker and add to it ourselves.
       */
      val combinedMarker = Markers.empty()

      markers.forEachIndexed { index, marker ->
        // If there are duplicate markers, we only include the first one in the log - otherwise we
        // would produce invalid JSON
        if (markers.anyBefore(index) { it.key == marker.key }) {
          return@forEachIndexed
        }

        combinedMarker.add(marker.logstashMarker)
      }

      exceptionMarkers.forEachIndexed { index, marker ->
        // Don't add marker keys that have already been added
        if (markers.any { it.key == marker.key } ||
            exceptionMarkers.anyBefore(index) { it.key == marker.key }) {
          return@forEachIndexed
        }

        combinedMarker.add(marker.logstashMarker)
      }

      // Add context markers in reverse, so newest marker shows first
      contextMarkers.forEachReversed { index, marker ->
        // Don't add marker keys that have already been added
        if (markers.any { it.key == marker.key } ||
            exceptionMarkers.any { it.key == marker.key } ||
            contextMarkers.anyBefore(index, reverse = true) { it.key == marker.key }) {
          return@forEachReversed
        }

        combinedMarker.add(marker.logstashMarker)
      }

      combinedMarker
    }
  }
}

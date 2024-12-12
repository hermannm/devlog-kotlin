package dev.hermannm.devlog

import ch.qos.logback.classic.Level as LogbackLevel
import ch.qos.logback.classic.Logger as LogbackLogger
import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory
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
@JvmInline // Use inline value class, to avoid redundant indirection when we just wrap Logback
value class Logger
internal constructor(
    @PublishedApi // For use in inline functions
    internal val logbackLogger: LogbackLogger,
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

  /** Calls the given function to build a log event and log it. */
  @PublishedApi // For use in inline functions
  internal inline fun logIfEnabled(level: LogLevel, buildLog: LogBuilder.() -> String) {
    if (!logbackLogger.isEnabledFor(level.logbackLevel)) {
      return
    }

    val builder = LogBuilder()
    val message = builder.buildLog()
    val marker = combineLogMarkers(builder.markers ?: emptyList(), builder.cause)

    logInternal(level, message, marker, builder.cause)
  }

  @PublishedApi
  internal fun logInternal(
      level: LogLevel,
      message: String,
      marker: LogstashMarker?,
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
        marker,
        FULLY_QUALIFIED_CLASS_NAME,
        level.intValue,
        message,
        null,
        cause,
    )
  }

  @PublishedApi // For use in inline functions
  internal companion object {
    @PublishedApi // For use in inline functions
    internal val FULLY_QUALIFIED_CLASS_NAME: String = Logger::class.java.name
  }
}

enum class LogLevel(
    @PublishedApi // For use in inline functions
    internal val logbackLevel: LogbackLevel,
    @PublishedApi // For use in inline functions
    internal val intValue: Int,
) {
  INFO(LogbackLevel.INFO, LocationAwareLogger.INFO_INT),
  WARN(LogbackLevel.WARN, LocationAwareLogger.WARN_INT),
  ERROR(LogbackLevel.ERROR, LocationAwareLogger.ERROR_INT),
  DEBUG(LogbackLevel.DEBUG, LocationAwareLogger.DEBUG_INT),
  TRACE(LogbackLevel.TRACE, LocationAwareLogger.TRACE_INT),
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
@PublishedApi // For use in inline functions
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

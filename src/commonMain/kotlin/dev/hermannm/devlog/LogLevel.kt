package dev.hermannm.devlog

/**
 * The severity of a log. From most to least severe:
 * - [LogLevel.ERROR]
 * - [LogLevel.WARN]
 * - [LogLevel.INFO]
 * - [LogLevel.DEBUG]
 * - [LogLevel.TRACE]
 *
 * When using Logback (on the JVM), you can enable/disable log levels for loggers based on their
 * package names (see
 * [Logback configuration docs](https://logback.qos.ch/manual/configuration.html#loggerElement)).
 * You also set a "root" (default) log level - if this level is `INFO`, then `DEBUG`/`TRACE` logs
 * will not produce any output unless explicitly enabled for a logger.
 */
public class LogLevel private constructor(private val name: String) {
  public companion object {
    /**
     * The highest log level, for errors in your system that may require immediate attention.
     *
     * See [LogLevel] for more on how to configure log levels.
     */
    public val ERROR: LogLevel = LogLevel("ERROR")
    /**
     * The second-highest log level - less severe than `ERROR`, more severe than `INFO`. Use this
     * when a fault has occurred in the system, but it doesn't necessarily require the immediate
     * attention that an `ERROR` would.
     *
     * See [LogLevel] for more on how to configure log levels.
     */
    public val WARN: LogLevel = LogLevel("WARN")
    /**
     * The median log level - less severe than `ERROR` and `WARN`, more severe than `DEBUG` and
     * `TRACE`. The standard log level to use for informational output, that most consumers of your
     * logs will be interested in, but that doesn't signal an error in your system.
     *
     * See [LogLevel] for more on how to configure log levels.
     */
    public val INFO: LogLevel = LogLevel("INFO")
    /**
     * The second-lowest log level - less severe than `INFO`, more severe than `TRACE`. This is used
     * for debug output, that you may not always have enabled, but that you may want to enable for
     * certain packages where you need more information for debugging.
     *
     * See [LogLevel] for more on how to configure log levels.
     */
    public val DEBUG: LogLevel = LogLevel("DEBUG")
    /**
     * The lowest log level, for tracing minute application details. This log level will typically
     * be disabled by default, and so will not produce any log output unless explicitly enabled for
     * a logger.
     *
     * See [LogLevel] for more on how to configure log levels.
     */
    public val TRACE: LogLevel = LogLevel("TRACE")
  }

  /**
   * Returns a string representation of the log level:
   * - `"ERROR"` for [LogLevel.ERROR]
   * - `"WARN"` for [LogLevel.WARN]
   * - `"INFO"` for [LogLevel.INFO]
   * - `"DEBUG"` for [LogLevel.DEBUG]
   * - `"TRACE"` for [LogLevel.TRACE]
   */
  override fun toString(): String = name

  /**
   * If we made [LogLevel] an enum or a sealed class, we could have exhaustive `when` expressions
   * without an `else` branch. However, that would freeze the current members of `LogLevel` as part
   * of our public API, as introducing new members would break compatibility. Library consumers
   * should only be using log levels as constants to pass to the library, so we don't want to expand
   * our public API needlessly like that.
   *
   * But since we now lose exhaustive `when` checks, we need an `else` branch. We use this method as
   * the exception in those branches, although it should always be unreachable. Our tests ensure
   * that we cover all cases.
   *
   * We may be able to use an enum/sealed class instead if this issue moves along:
   * https://youtrack.jetbrains.com/issue/KT-38750/Support-declaration-site-nonexhaustiveness-for-enums-and-sealed-classes
   */
  internal fun unrecognized(): Exception {
    return IllegalStateException("Unrecognized log level '${name}'")
  }
}

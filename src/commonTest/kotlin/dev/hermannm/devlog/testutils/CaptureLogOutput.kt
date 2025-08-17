package dev.hermannm.devlog.testutils

internal data class LogOutput(
    /** String of all JSON-encoded log-event-specific fields from log output, in order. */
    val logFields: String,
    /**
     * Map of context fields in the log output. The values here are either `String` or `JsonElement`
     * (we map string JSON values to just `String`, so we don't have to wrap values in
     * `JsonPrimitive` in all our tests).
     *
     * We don't use a String here and verify order, since SLF4J's MDC (which we use for our logging
     * context in the JVM implementation) uses a HashMap internally, which does not guarantee order.
     */
    val contextFields: Map<String, Any>,
)

/**
 * Captures stdout and stderr in the scope of the given lambda, and parses it to [LogOutput].
 *
 * @param block We mark this lambda `crossinline` to prevent accidental non-local returns.
 */
internal inline fun captureLogOutput(crossinline block: () -> Unit): LogOutput {
  val output = captureStdoutAndStderr(block)
  return parseLogOutput(output)
}

/**
 * How to capture standard output is platform-specific, so we declare an `expect` function here, to
 * be implemented in every platform-specific test module.
 *
 * @param block We mark this lambda `crossinline` to prevent accidental non-local returns.
 */
internal expect inline fun captureStdoutAndStderr(crossinline block: () -> Unit): String

/**
 * Parsing log output is also platform-specific (for example, we use the `logstash-logback-encoder`
 * JSON format on the JVM), so we declare this as an `expect` function as well.
 */
internal expect fun parseLogOutput(logOutput: String): LogOutput

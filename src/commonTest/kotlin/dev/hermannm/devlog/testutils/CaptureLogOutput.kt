package dev.hermannm.devlog.testutils

internal data class LogOutput(
    /** String of all JSON-encoded log-event-specific fields from log output, in order. */
    val logFields: String,
    /**
     * Map of context fields in the log output. The values here are either `String` or `JsonElement`
     * (we map string JSON values to just `String`, so we don't have to wrap our assertions in
     * `JsonPrimitive` in all our tests).
     *
     * We don't use a String here and verify order, since SLF4J's MDC (which we use for our logging
     * context) uses a HashMap internally, which does not guarantee order.
     */
    val contextFields: Map<String, Any>,
)

/**
 * How to capture log output is platform-specific, so we use an `expect` function here which we must
 * override in every platform-specific test module.
 */
internal expect fun captureLogOutput(block: () -> Unit): LogOutput

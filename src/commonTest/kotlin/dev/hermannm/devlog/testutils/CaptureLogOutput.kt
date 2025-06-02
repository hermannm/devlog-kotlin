package dev.hermannm.devlog.testutils

import kotlinx.serialization.json.JsonElement

internal data class LogOutput(
    /** String of all JSON-encoded log-event-specific fields from log output, in order. */
    val logFields: String,
    /**
     * Map of context fields in the log output. We don't use a String here and verify order, since
     * SLF4J's MDC (which we use for our logging context) uses a HashMap internally, which does not
     * guarantee order.
     */
    val contextFields: Map<String, JsonElement>,
)

/**
 * How to capture log output is platform-specific, so we use an `expect` function here which we must
 * override in every platform-specific test module.
 */
internal expect fun captureLogOutput(block: () -> Unit): LogOutput

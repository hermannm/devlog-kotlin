package dev.hermannm.devlog.testutils

import dev.hermannm.devlog.LoggingContext

internal expect fun createLoggingContext(fields: Map<String, String>): LoggingContext

internal expect fun loggingContextShouldContainExactly(expectedFields: Map<String, String>)

internal expect fun loggingContextShouldBeEmpty()

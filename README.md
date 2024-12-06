# devlog-kotlin

Logging library for Kotlin JVM, that thinly wraps SLF4J and Logback to provide a more ergonomic API, and to use
`kotlinx.serialization` for log marker serialization instead of Jackson.

Also provides a Logback encoder with a human-readable log format designed for development builds (using the same format
as the [devlog libraries for Go](https://github.com/hermannm/devlog) and
[Rust](https://github.com/hermannm/devlog-tracing)).

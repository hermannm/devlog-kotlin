# Module devlog-kotlin

Structured logging library for Kotlin, that aims to provide a developer-friendly API with near-zero
runtime overhead. Currently only supports the JVM platform, wrapping SLF4J.

Repository: <https://github.com/hermannm/devlog-kotlin>

This site hosts the docstrings for public classes and functions in `devlog-kotlin`. For a
higher-level introduction to the library, see
the ["Usage" section in the README on GitHub](https://github.com/hermannm/devlog-kotlin#usage).

# Package dev.hermannm.devlog

The main package of `devlog-kotlin`, providing the `Logger` API.

# Package dev.hermannm.devlog.output.logback

Provides extensions for formatting log output when using `devlog-kotlin` with the
[Logback](https://logback.qos.ch/) library on the JVM.

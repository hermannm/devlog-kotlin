# devlog-kotlin integration tests

We want to test that our library works as expected with different SLF4J implementations, and that
Logback is not added to the classpath unless we explicitly add it as a dependency.

To achieve this, we include separate Gradle sub-projects here under `integration-tests/`, with one
project for each SLF4J logger implementation we want to test. When running the `check` Gradle task
in the root project, the integration tests for these sub-projects will be run as well.

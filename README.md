# devlog-kotlin

Structured logging library for Kotlin, that aims to provide a developer-friendly API with minimal
runtime overhead. Currently only supports the JVM platform, wrapping SLF4J.

**Docs:** [devlog-kotlin.hermannm.dev](https://devlog-kotlin.hermannm.dev)

**Published on:**

- klibs.io:
  [klibs.io/project/hermannm/devlog-kotlin](https://klibs.io/project/hermannm/devlog-kotlin)
- Maven Central:
  [central.sonatype.com/artifact/dev.hermannm/devlog-kotlin](https://central.sonatype.com/artifact/dev.hermannm/devlog-kotlin)

**Contents:**

- [Usage](#usage)
  - [Note on coroutines](#note-on-coroutines)
- [Adding to your project](#adding-to-your-project)
- [Implementation](#implementation)
  - [Performance](#performance)
  - [Automatic logger names](#automatic-logger-names)
- [Project Structure](#project-structure)
- [Why another logging library?](#why-another-logging-library)
- [Maintainer's guide](#maintainers-guide)
- [Credits](#credits)

## Usage

The `Logger` class is the entry point to `devlog-kotlin`'s logging API. You can get a `Logger` by
calling `getLogger()`, which automatically gives the logger the name of its containing class (or
file, if defined at the top level). See [Implementation](#automatic-logger-names) below for how this
works.

```kotlin
// File Example.kt
package com.example

import dev.hermannm.devlog.getLogger

// Gets the name "com.example.Example"
private val log = getLogger()
```

`Logger` provides methods for logging at various log levels (`info`, `warn`, `error`, `debug` and
`trace`). The methods take a lambda to construct the log, which is only called if the log level is
enabled (see [Implementation](#implementation) for how this is done efficiently).

```kotlin
fun example() {
  log.info { "Example message" }
}
```

You can also add _fields_ (structured key-value data) to your logs, by calling the `field` method in
the scope of a log lambda. It uses
[`kotlinx.serialization`](https://github.com/Kotlin/kotlinx.serialization) to serialize the value.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Event(val id: Long, val type: String)

fun example() {
  val event = Event(id = 1000, type = "ORDER_UPDATED")

  log.info {
    field("event", event)
    "Processing event"
  }
}
```

When outputting logs as JSON, the key/value given to `field` is added to the logged JSON object (see
below). This allows you to filter and query on the field in the log analysis tool of your choice, in
a more structured manner than if you were to just use string concatenation.

<!-- prettier-ignore -->

```jsonc
{
  "message": "Processing event",
  "event": {
    "id": 1000,
    "type": "ORDER_UPDATED"
  },
  // ...timestamp etc.
}
```

Sometimes, you may want to add fields to all logs in a scope. For example, you can add an event ID
to the logs when processing an event, so you can trace all the logs made in the context of that
event. To do this, you can use `withLoggingContext`:

```kotlin
import dev.hermannm.devlog.field
import dev.hermannm.devlog.withLoggingContext

fun processEvent(event: Event) {
  withLoggingContext(field("eventId", event.id)) {
    log.debug { "Started processing event" }
    // ...
    log.debug { "Finished processing event" }
  }
}
```

...giving the following output:

```jsonc
{ "message": "Started processing event", "eventId": "..." }
{ "message": "Finished processing event", "eventId": "..." }
```

If an exception is thrown from inside `withLoggingContext`, the logging context is attached to the
exception. That way, we don't lose context when an exception escapes from the context scope - which
is when we need it most! When the exception is logged, the fields from the exception's logging
context are included in the output.

You can log an exception like this:

```kotlin
fun example() {
  try {
    callExternalService()
  } catch (e: Exception) {
    log.error(e) { "Request to external service failed" }
  }
}
```

If you want to add log fields to an exception when it's thrown, you can use
`ExceptionWithLoggingContext`:

```kotlin
import dev.hermannm.devlog.ExceptionWithLoggingContext
import dev.hermannm.devlog.field

fun callExternalService() {
  val response = sendHttpRequest()
  if (!response.status.successful) {
    // When this exception is caught and logged, "statusCode" and "responseBody"
    // will be included as structured fields in the log output.
    // You can also extend this exception class for your own custom exceptions.
    throw ExceptionWithLoggingContext(
      "Received error response from external service",
      field("statusCode", response.status.code),
      field("responseBody", response.bodyString()),
    )
  }
}
```

This is useful when you are throwing an exception from somewhere down in the stack, but do logging
further up the stack, and you have structured data at the throw site that you want to attach to the
exception log. In this case, one may typically resort to string concatenation, but
`ExceptionWithLoggingContext` allows you to have the benefits of structured logging for exceptions
as well.

For more detailed documentation of the classes and functions provided by the library, see
<https://devlog-kotlin.hermannm.dev>.

### Note on coroutines

`withLoggingContext` uses a thread-local
([SLF4J's `MDC`](https://logback.qos.ch/manual/mdc.html)) to provide log fields to the scope, so it
won't work with Kotlin coroutines and `suspend` functions. If you use coroutines, you can solve this
with
[`MDCContext` from
`kotlinx-coroutines-slf4j`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-slf4j/kotlinx.coroutines.slf4j/-m-d-c-context/).

## Adding to your project

Like SLF4J, `devlog-kotlin` only provides a logging _API_, and you have to add a logging
_implementation_ to actually output logs. Any SLF4J logger implementation will work, but the
library is specially optimized for Logback.

To set up `devlog-kotlin` with
[Logback](https://mvnrepository.com/artifact/ch.qos.logback/logback-classic) and
[
`logstash-logback-encoder`](https://mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder)
for JSON output, add the following dependencies:

- **Gradle:**
  ```kotlin
  dependencies {
    // Logger API
    implementation("dev.hermannm:devlog-kotlin:${devlogVersion}")
    // Logger implementation
    runtimeOnly("ch.qos.logback:logback-classic:${logbackVersion}")
    // JSON encoding of logs
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:${logstashEncoderVersion}")
  }
  ```
- **Maven:**
  ```xml
  <dependencies>
    <!-- Logger API -->
    <dependency>
      <groupId>dev.hermannm</groupId>
      <artifactId>devlog-kotlin-jvm</artifactId>
      <version>${devlog-kotlin.version}</version>
    </dependency>
    <!-- Logger implementation -->
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>${logback.version}</version>
      <scope>runtime</scope>
    </dependency>
    <!-- JSON encoding of logs -->
    <dependency>
      <groupId>net.logstash.logback</groupId>
      <artifactId>logstash-logback-encoder</artifactId>
      <version>${logstash-logback-encoder.version}</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
  ```

Then, configure Logback with a `logback.xml` file under `src/main/resources`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <!-- Writes object values from logging context as actual JSON (not escaped) -->
      <mdcEntryWriter class="dev.hermannm.devlog.output.logback.JsonContextFieldWriter"/>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

For more configuration options, see:

- [The Configuration chapter of the Logback manual](https://logback.qos.ch/manual/configuration.html)
- [The Usage docs for
  `logstash-logback-encoder`](https://github.com/logfellow/logstash-logback-encoder#usage)

## Implementation

### Performance

- All the methods on `Logger` take a lambda to build the log, which is only called if the log level
  is enabled - so you only pay for message string concatenation and log field serialization if it's
  actually logged.
- `Logger`'s methods are also `inline`, so we avoid the cost of allocating a function object for the
  lambda parameter.
- Elsewhere in the library, we use inline value classes when wrapping SLF4J/Logback APIs, to get as
  close as possible to a zero-cost abstraction.

### Automatic logger names

In the JVM implementation, `getLogger()` calls `MethodHandles.lookup().lookupClass()`, which returns
the calling class. Since `getLogger` is inline, that will actually return the class that called
`getLogger`, so we can use it to get the name of the caller. When called at file scope, the calling
class will be the synthetic `Kt` class that Kotlin generates for the file, so we can use the file
name in that case.

This is the pattern that
[the SLF4J docs recommends](https://www.slf4j.org/faq.html#declaration_pattern) for getting loggers
for a class in a generic manner.

## Project Structure

`devlog-kotlin` is structured as a Kotlin Multiplatform project, although currently the only
supported platform is JVM. The library has been designed to keep as much code as possible in the
common (platform-neutral) module, to make it easier to add support for other platforms in the
future.

Directory structure:

- `src/commonMain` contains common, platform-neutral implementations.
  - This module implements the surface API of `devlog-kotlin`, namely `Logger`, `LogBuilder` and
    `LogField`.
  - It declares `expect` classes and functions for the underlying APIs that must be implemented by
    each platform, namely `PlatformLogger`, `LogEvent` and `LoggingContext`.
- `src/jvmMain` implements platform-specific APIs for the JVM.
  - It uses SLF4J, the de-facto standard JVM logging library, with extra optimizations for Logback.
  - It implements:
    - `PlatformLogger` as a typealias for `org.slf4j.Logger`.
    - `LoggingContext` using SLF4J's `MDC` (Mapped Diagnostic Context).
    - `LogEvent` with an SLF4J `DefaultLoggingEvent`, or a special-case optimization using
      Logback's `LoggingEvent` if Logback is on the classpath.
- `src/commonTest` contains the library's tests that apply to all platforms.
  - In order to keep as many tests as possible in the common module, we write most of our tests
    here, and delegate to platform-specific `expect` utilities where needed. This allows us to
    define a common test suite for all platforms, just switching out the parts where we need
    platform-specific implementations.
- `src/jvmTest` contains JVM-specific tests, and implements the test utilities expected by
  `commonTest` for the JVM.
- `integration-tests` contains Gradle subprojects that load various SLF4J logger backends (Logback,
  Log4j and `java.util.logging`, a.k.a. `jul`), and verify that they all work as expected with
  `devlog-kotlin`.
  - Since we do some special-case optimizations if Logback is loaded, this lets us test that these
    Logback-specific optimizations do not interfere with other logger backends.

## Why another logging library?

The inspiration for this library mostly came from some inconveniencies and limitations I've
experienced with the [`kotlin-logging`](https://github.com/oshai/kotlin-logging) library (it's a
great library, these are just my subjective opinions!). Here are some of the things I wanted to
improve with this library:

- **Structured logging**
  - In `kotlin-logging`, going from a log _without_ structured log fields to a log _with_ them
    requires you to switch your logger method (`info` -> `atInfo`), use a different syntax
    (`message = ` instead of returning a string), and construct a map for the fields.
  - Having to switch syntax becomes a barrier for developers to do structured logging. In my
    experience, the key to making structured logging work in practice is to reduce such barriers.
  - So in `devlog-kotlin`, I wanted to make this easier: you use the same logger methods whether you
    are adding fields or not, and adding structured data to an existing log is as simple as just
    calling `field` in the scope of the log lambda.
- **Using `kotlinx.serialization` for log field serialization**
  - `kotlin-logging` also wraps SLF4J in the JVM implementation. It passes structured log fields as
    `Map<String, Any?>`, and leaves it to the logger backend to serialize them. Since most SLF4J
    logger implementations are Java-based, they typically use Jackson to serialize these fields (if
    they support structured logging at all).
  - But in Kotlin, we often use `kotlinx.serialization` instead of Jackson. There can be subtle
    differences between how Jackson and `kotlinx` serialize objects, so we would prefer to use
    `kotlinx` for our log fields, so that they serialize in the same way as in the rest of our
    application.
  - In `devlog-kotlin`, we solve this by serializing log fields _before_ sending them to the logger
    backend, which allows us to control the serialization process with `kotlinx.serialization`.
  - Controlling the serialization process also lets us handle failures better. One of the issues
    I've experienced with Jackson serialization of log fields, is that `logstash-logback-encoder`
    would drop an entire log line in some cases when one of the custom fields on that log failed
    to serialize. `devlog-kotlin` never drops logs on serialization failures, instead defaulting to
    `toString()`.
- **Inline logger methods**
  - One of the classic challenges for a logging library is how to handle calls to a logger method
    when the log level is disabled. We want this to have as little overhead as possible, so that
    we don't pay a runtime cost for a log that won't actually produce any output.
  - In Kotlin, we have the opportunity to create such zero-cost abstractions, using `inline`
    functions with lambda parameters. This lets us implement logger methods that compile down to a
    simple `if` statement to check if the log level is enabled, and that do no work if the level is
    disabled. Great!
  - However, `kotlin-logging` does not use inline logger methods. This is partly because of how the
    library is structured: `KLogger` is an interface, with different implementations for various
    platforms - and interfaces can't have inline methods. So the methods that take lambdas won't be
    inlined, which means that they may allocate function objects, which are not zero-cost.
    [This `kotlin-logging` issue](https://github.com/oshai/kotlin-logging/issues/34) discusses some
    of the performance implications.
  - `devlog-kotlin` solves this by dividing up the problem: we make our `Logger` a concrete class,
    with a single implementation in the `common` module. It wraps an internal `PlatformLogger`
    interface (delegating to SLF4J in the JVM implementation). `Logger` provides the public API, and
    since it's a single concrete class, we can make its methods `inline`. We also make it a
    `value class`, so that it compiles down to just the underlying `PlatformLogger` at runtime. This
    makes the abstraction as close to zero-cost as possible.
  - One notable drawback of inline methods is that they don't work well with line numbers (i.e.,
    getting file location information inside an inlined lambda will show an incorrect line number).
    We deem this a worthy tradeoff for performance, because the class/file name + the log message is
    typically enough to find the source of a log. Also, `logstash-logback-encoder`
    [explicitly discourages enabling file locations](https://github.com/logfellow/logstash-logback-encoder/tree/logstash-logback-encoder-8.1#caller-info-fields),
    due to the runtime cost. Still, this is something to be aware of if you want line numbers
    included in your logs. This limitation is documented on all the methods on `Logger`.
- **Supporting arbitrary types for logging context values**
  - SLF4J's `MDC` has a limitation: values must be `String`. And the `withLoggingContext` function
    from `kotlin-logging`, which uses `MDC`, inherits this limitation.
  - But when doing structured logging, it can be useful to attach more than just strings in the
    logging context - for example, attaching the JSON of an event in the scope that it's being
    processed. If you pass serialized JSON to `MDC`, the resulting log output will include the JSON
    as an escaped string. This defeats the purpose, as an escaped string will not be parsed
    automatically by log analysis platforms - what we want is to include actual, unescaped JSON in
    the logging context, so that we can filter and query on its fields.
  - `devlog-kotlin` solves this limitation by instead taking a `LogField` type, which can have an
    arbitrary serializable value, as the parameter to our `withLoggingContext` function. We then
    provide `JsonContextFieldWriter` for interoperability with `MDC` when using Logback +
    `logstash-logback-encoder`.

## Maintainer's guide

### Updating dependencies

- Run:
  ```
  ./gradlew versionCatalogUpdate
  ```
- Also check for new versions of [`ktfmt`](https://github.com/facebook/ktfmt), and update the
  `ktfmt` entry under `spotless` in `build.gradle.kts`

### Checking binary compatibility

- We use the Kotlin
  [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) to
  avoid accidental breaking changes
- This plugin generates an `api/devlog-kotlin.api` file that contains all the public APIs of the
  library. When making changes to the library, any changes to the library's public API will be
  checked against this file (in the `apiCheck` Gradle task), to detect possible breaking changes
- When _adding_ new APIs (which should not be a breaking change), you must update this `.api` file
  by running the `apiDump` Gradle task

### Publishing a new release

- Bump version in `build.gradle.kts`
- Run tests:
  ```
  ./gradlew check
  ```
- Check that documentation is generated as expected:
  ```
  ./gradlew dokkaGeneratePublicationHtml
  ```
- Add an entry to `CHANGELOG.md` (with the current date)
  - Remember to update the link section, and bump the version for the `[Unreleased]` link
- Create commit and tag for the release (update `TAG` variable in below command):
  ```
  TAG=vX.Y.Z && git commit -m "Release ${TAG}" && git tag -a "${TAG}" -m "Release ${TAG}" && git log --oneline -2
  ```
- Run:
  ```
  ./gradlew publishToMavenCentral
  ```
  - This will create a deployment at
    [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments),
    and start verification
  - Once verification completes, click "Publish" on the deployment
  - If you have issues, see the following resources:
    - Gradle Maven Publish Plugin guide to Maven Central:
      https://vanniktech.github.io/gradle-maven-publish-plugin/central/
    - Sonatype guide to Maven Central publishing:
      https://central.sonatype.org/publish/publish-portal-guide/
    - Kotlin guide to publishing multiplatform libraries to Maven Central:
      https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
- Push the commit and tag:
  ```
  git push && git push --tags
  ```
  - Our release workflows will then create a GitHub release with the pushed tag's changelog entry,
    and deploy documentation to [devlog-kotlin.hermannm.dev](https://devlog-kotlin.hermannm.dev)

## Credits

Credits to the [`kotlin-logging` library by Ohad Shai](https://github.com/oshai/kotlin-logging)
(licensed under
[Apache 2.0](https://github.com/oshai/kotlin-logging/blob/c91fe6ab71b9d3470fae71fb28c453006de4e584/LICENSE)),
which was a great inspiration for this library.

Also credits to [kosiakk](https://github.com/kosiakk) for
[this `kotlin-logging` issue](https://github.com/oshai/kotlin-logging/issues/34), which inspired the
implementation using `inline` methods for minimal overhead.

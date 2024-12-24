# devlog-kotlin

Logging library for Kotlin JVM, that thinly wraps SLF4J and Logback to provide a more ergonomic API.

Published on Maven Central: https://central.sonatype.com/artifact/dev.hermannm/devlog-kotlin

**Contents:**

- [Usage](#usage)
  - [Setting up with Logback](#setting-up-with-logback)
- [Implementation](#implementation)
- [Credits](#credits)

## Usage

The `Logger` class is the entry point to `devlog-kotlin`'s logging API. You can construct a `Logger`
by providing an empty lambda, which automatically gives the logger the name of its containing class
(or file, if defined at the top level).

```kotlin
// File Example.kt
package com.example

import dev.hermannm.devlog.getLogger

// Gets the name "com.example.Example"
private val log = getLogger {}
```

`Logger` provides methods for logging at various log levels (`info`, `warn`, `error`, `debug` and
`trace`). The methods take a lambda to construct the log, which is only called if the log level is
enabled (see [Implementation](#implementation) for how this is done efficiently).

```kotlin
fun example() {
  log.info { "Example message" }
}
```

You can also add _fields_ (structured key-value data) to your logs. The `addField` method uses
`kotlinx.serialization` to serialize the value.

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Long, val name: String)

fun example() {
  val user = User(id = 1, name = "John Doe")

  log.info {
    addField("user", user)
    "Registered new user"
  }
}
```

When outputting logs as JSON (using [`logstash-logback-encoder`](#setting-up-with-logback)), the
key/value given to `addField` is added to the logged JSON object (see below). This allows you to
filter and query on the field in the log analysis tool of your choice, in a more structured manner
than if you were to just use string concatenation.

<!-- prettier-ignore -->
```jsonc
{
  "message": "Registered new user",
  "user": {
    "id": 1,
    "name": "John Doe"
  },
  // ...timestamp etc.
}
```

If you want to add fields to all logs within a scope, you can use `withLoggingContext`:

```kotlin
import dev.hermannm.devlog.field
import dev.hermannm.devlog.withLoggingContext

fun processEvent(event: Event) {
  withLoggingContext(field("event", event)) {
    log.debug { "Started processing event" }
    // ...
    log.debug { "Finished processing event" }
  }
}
```

...giving the following output:

```jsonc
{ "message": "Started processing event", "event": { /* ... */ } }
{ "message": "Finished processing event", "event": { /* ... */ } }
```

Note that `withLoggingContext` uses a thread-local to provide log fields to the scope, so it won't
work with Kotlin coroutines and `suspend` functions (though it does work with Java virtual threads).
An alternative that supports coroutines may be added in a future version of the library.

Finally, you can attach a `cause` exception to logs:

```kotlin
fun example() {
  try {
    callExternalService()
  } catch (e: Exception) {
    log.error {
      cause = e
      "Request to external service failed"
    }
  }
}
```

### Setting up with Logback

This library is primarily designed and optimized for working with Logback, though it will work with
other SLF4J logger implementations as well.

When using Logback, you can output logs as JSON with
[`logstash-logback-encoder`](https://github.com/logfellow/logstash-logback-encoder). This can be
configured by creating a `logback.xml` file under `src/main/resources`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

See the [Usage docs](https://github.com/logfellow/logstash-logback-encoder#usage) for
`logstash-logback-encoder` for more configuration options.

## Implementation

- All the methods on `Logger` take a lambda argument to build the log, which is only called if the
  log level is enabled - so you only pay for log field serialization and message concatenation if
  it's actually logged.
- `Logger`'s methods are also `inline`, so we avoid the cost of allocating a function object for the
  lambda argument.
- Elsewhere in the library, we use inline value classes when wrapping Logback APIs, to get as close
  as possible to a zero-cost abstraction.

## Credits

Credits to the [kotlin-logging library by Ohad Shai](https://github.com/oshai/kotlin-logging)
(licensed under
[Apache 2.0](https://github.com/oshai/kotlin-logging/blob/c91fe6ab71b9d3470fae71fb28c453006de4e584/LICENSE)),
which inspired the `getLogger {}` syntax using a lambda to get the logger name.
[This kotlin-logging issue](https://github.com/oshai/kotlin-logging/issues/34) (by
[kosiakk](https://github.com/kosiakk)) also inspired the implementation using `inline` methods for
minimal overhead.

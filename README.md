# devlog-kotlin

Logging library for Kotlin JVM, that thinly wraps SLF4J and Logback to provide a more ergonomic API,
and to use `kotlinx.serialization` for log marker serialization instead of Jackson.

**Contents:**

- [Usage](#usage)
- [Implementation](#implementation)
- [Credits](#credits)

## Usage

The `Logger` class is the entry point to `devlog-kotlin`'s logging API. You can construct a `Logger`
by providing an empty lambda, which automatically gives the logger the name of its containing class
(or file, if defined at the top level).

```kotlin
// File Example.kt
package com.example

import dev.hermannm.devlog.Logger

// Gets the name "com.example.Example"
private val log = Logger {}
```

`Logger` provides methods for logging at various log levels (`info`, `warn`, `error`, `debug` and
`trace`). The methods take a lambda to construct the log, which is only called if the log level is
enabled (see [Implementation](#implementation) for how this is done efficiently).

```kotlin
fun example() {
  log.info { "Example message" }
}
```

You can also add "log markers" (structured key-value data) to your logs. The `addMarker` method
uses `kotlinx.serialization` to serialize the value.

```kotlin
import kotlinx.serialization.Serializable

@Serializable data class User(val id: Long, val name: String)

fun example() {
  val user = User(id = 1, name = "John Doe")

  log.info {
    addMarker("user", user)
    "Registered new user"
  }
}
```

This will give the following log output (if outputting logs as JSON with
`logstash-logback-encoder`):

```jsonc
{ "message": "Registered new user", "user": { "id": 1, "name": "John Doe" } }
```

You can also use `withLoggingContext` to add markers to all logs within a given scope:

```kotlin
import dev.hermannm.devlog.marker
import dev.hermannm.devlog.withLoggingContext

fun processEvent(event: Event) {
  withLoggingContext(
      marker("event", event),
  ) {
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

Finally, you can attach a `cause` exception to logs:

```kotlin
fun example(user: User) {
  try {
    storeUser(user)
  } catch (e: Exception) {
    log.error {
      cause = e
      addMarker("user", user)
      "Failed to store user in database"
    }
  }
}
```

## Implementation

All the methods on `Logger` are `inline`, and don't do anything if the log level is disabled - so
you only pay for marker serialization and log message concatenation if it's actually logged.

Elsewhere in the library, we use inline value classes to wrap Logback APIs, to get as close as
possible to a zero-cost abstraction.

## Credits

Credits to the [kotlin-logging library by Ohad Shai](https://github.com/oshai/kotlin-logging)
(licensed under
[Apache 2.0](https://github.com/oshai/kotlin-logging/blob/c91fe6ab71b9d3470fae71fb28c453006de4e584/LICENSE)),
which inspired the `Logger {}` syntax using a lambda to get the logger name.
[This kotlin-logging issue](https://github.com/oshai/kotlin-logging/issues/34) (by
[kosiakk](https://github.com/kosiakk)) also inspired the implementation using `inline` methods for
minimal overhead.

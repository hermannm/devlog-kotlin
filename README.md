# devlog-kotlin

Logging library for Kotlin JVM, that thinly wraps SLF4J and Logback to provide a more ergonomic API,
and to use `kotlinx.serialization` for log marker serialization instead of Jackson.

## Usage

The `Logger` class is the entry point to `devlog-kotlin`'s logging API. You can construct a `Logger`
by providing an empty lambda, which automatically gives the logger the name of its containing class
(or file, if defined at the top level).

```kotlin
import dev.hermannm.devlog.Logger

private val log = Logger {}
```

`Logger` provides methods for logging at various log levels (`info`, `warn`, `error`, `debug` and
`trace`):

```kotlin
fun example() {
  log.info { "Example message" }
}
```

You can also add "log markers" (structured key-value data) to your logs. The `addMarker` function
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

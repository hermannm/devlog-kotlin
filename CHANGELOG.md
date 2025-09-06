# Changelog

## [Unreleased]

- Attach logging context to exceptions when they would escape the scope of
  `withLoggingContext`
  - Previously, when an exception was thrown inside a logging context scope, one would lose that
    logging context if the exception was logged further up the stack (which is typical). But
    unexpected exceptions is when we need this context the most! So now, `withLoggingContext`
    attaches its context fields to any encountered exception before re-throwing it up the stack, and
    `Logger` includes these fields when the exception is logged.
- Allow log fields to overwrite logging context fields for a single log
  - Previously, duplicate keys could appear in the log output if the same key was used on a
    single-log field as on a logging context field.
- Use
  [Kotlin Contracts](https://github.com/Kotlin/KEEP/blob/2b887d02fbf4cbf8b520cb2fbca11f4a807a353e/proposals/KEEP-0139-kotlin-contracts.md)
  in `withLoggingContext`
  - This gives users more flexibility in what they can do in the given lambda.
- **Breaking:** Rename `ExceptionWithLogFields` to `ExceptionWithLoggingContext`, and `HasLogFields`
  to `HasLoggingContext`
  - This makes the naming of these classes more consistent with the `withLoggingContext` function.
- **Breaking:** `ExceptionWithLoggingContext` no longer implements the `HasLoggingContext` interface
  - When it implemented this interface, it needlessly had to expose its `logFields` property, which
    should be an implementation detail.
- **Breaking:** Rename `getLoggingContext` to `getCopyOfLoggingContext`, change its return value to
  an opaque `LoggingContext` wrapper class, and add new `withLoggingContext` overload that accepts
  this new `LoggingContext` type
  - This new name is more explicit about what the function does, and also avoids competing with
    `getLogger` for autocomplete.
  - The `LoggingContext` wrapper class enables some performance optimizations, since we no longer
    need to turn the logging context into a `List`.
- **Breaking:** Use `Collection` instead of `List` in public APIs that accept or provide multiple
  log fields
  - `Collection` is a more flexible interface than `List`.
- **Breaking:** Change return type of `rawJson` function from
  `kotlinx.serialization.json.JsonElement` to custom `RawJson` wrapper type
  - This reduces the coupling of the library's API to `kotlinx.serialization`, while keeping the
    same serialization behavior. In the future, we may want to support e.g. Jackson serialization on
    the JVM, so this gives us more flexibility.
- Change how JSON fields in `withLoggingContext` are handled, to avoid internal implementation
  details leaking out
  - In the previous implementation, a key suffix was used to identify logging context fields with
    JSON values. But in certain log output configurations, this implementation detail could leak
    out. This implementation has now been reworked, both to avoid leaking implementation details and
    also to improve performance (by avoiding allocations).
- Minimize the amount of code in `inline` functions
  - Having a lot of code in `inline` functions increases code size (since that code is replicated
    wherever the `inline` function is called), which can negatively affect perforamnce. The library
    now only includes the most necessary code in `inline` blocks, delegating to normal functions
    where possible.

## [v0.7.0] - 2025-06-12

- Move `LoggingContextJsonFieldWriter` to `output.logback` subpackage, and rename to the more
  concise `JsonContextFieldWriter`
  - Moving this to a separate package makes it clearer that this is a Logback-specific extension

## [v0.6.1] - 2025-06-09

- Mark getters for `Logger.isInfoEnabled` and related properties as `inline` for minimal overhead
- Improve documentation

## [v0.6.0] - 2025-06-06

- Change the `getLogger {}` function using a lambda parameter to a zero-argument `getLogger()`
  function
  - This uses `MethodHandles.lookup().lookupClass()`,
    [as recommended by SLF4J](https://www.slf4j.org/faq.html#declaration_pattern)
- Mark the `buildLog` lambda parameter on logger methods as `crossinline`
  - This prevents users from accidentally doing a non-local return inside the lambda, which would
    drop the log
- Improve documentation

## [v0.5.0] - 2025-06-05

- Restructure library for Kotlin Multiplatform
  - JVM is still the only supported platform as of now, but this lays the groundwork for supporting
    more platforms in the future
- Add another `getLogger` overload that takes a `KClass`
- Allow using `field` function with non-reified generics when passing a custom serializer
- Rename `WithLogFields` interface to the more intuitive `HasLogFields`
- Add JVM-specific optimization annotations where appropriate, to reduce overhead
- Improve documentation
- Host documentation on <https://devlog-kotlin.hermannm.dev>

## [v0.4.0] - 2025-03-31

- Take `cause` exception as a normal argument on `Logger` methods, instead of as a property on
  `LogBuilder`
  - This makes the normal case of logging a message along with an exception more concise
- Use SLF4J's MDC for logging context instead of a custom thread-local
  - This allows our logging context to also apply to logs by libraries that use SLF4J
  - A new `LoggingContextJsonFieldWriter` is provided for Logback to enable JSON fields in MDC
- Rename `LogBuilder` methods to be more concise and intuitive
  - `addField` and `addRawJsonField` are now just `field` and `rawJsonField`, like their top-level
    counterparts
  - `addPreconstructedField` is now `addField`
- Add `LogBuilder.addFields` method to make it easier to pass a list of pre-constructed fields
- Add `ExecutorService.inheritLoggingContext` extension function to make it easier to inherit
  logging context in tasks submitted to an `ExecutorService`
- Add more constructor overloads to `ExceptionWithLogFields`, to allow passing log fields with less
  boilerplate
- Fix edge case of unquoted strings being allowed by `rawJsonField`
- Improve documentation

## [v0.3.0] - 2024-12-28

- Make library compatible with any SLF4J logger implementation (not just Logback)
  - The library is still optimized for Logback, but it's now an optional dependency
  - The dependency on `logstash-logback-encoder` has been dropped (we now use SLF4J's `KeyValuePair`
    instead of the `SingleFieldAppendingMarker` from `logstash-logback-encoder`)
- Replace `Logger` constructors with `getLogger` functions
  - Since there are so many classes named `Logger` in various libraries, calling `getLogger {}`
    instead of `Logger {}` makes it easier to auto-complete in your IDE
- Add `getLoggingContext` function to allow passing logging context between threads
- Improve performance of `withLoggingContext` by reducing array copies
- Improve test coverage
  - We now run integration tests for various SLF4J logger implementations, to make sure that the
    library works for implementations other than Logback

## [v0.2.1] - 2024-12-19

- Allow setting `LogBuilder.cause` to `null`
  - This is useful when you have a cause exception that may or may not be `null`
- Fix edge case in `LogBuilder.cause` setter that could throw an exception
- Remove needlessly restrictive `jvmTarget` setting from Kotlin compiler config

## [v0.2.0] - 2024-12-18

- Change use of word "marker" to "field" in logger APIs
  - See commit
    [48b0638](https://github.com/hermannm/devlog-kotlin/commit/48b063898b8d22378b0ede2c3717d0c608693c5e)
    for the rationale behind this
- Include log fields from `withLoggingContext` on `ExceptionWithLogFields` on construction

## [v0.1.0] - 2024-12-17

- Initial release

[Unreleased]: https://github.com/hermannm/devlog-kotlin/compare/v0.7.0...HEAD

[v0.7.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.6.1...v0.7.0

[v0.6.1]: https://github.com/hermannm/devlog-kotlin/compare/v0.6.0...v0.6.1

[v0.6.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.5.0...v0.6.0

[v0.5.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.4.0...v0.5.0

[v0.4.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.3.0...v0.4.0

[v0.3.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.2.1...v0.3.0

[v0.2.1]: https://github.com/hermannm/devlog-kotlin/compare/v0.2.0...v0.2.1

[v0.2.0]: https://github.com/hermannm/devlog-kotlin/compare/v0.1.0...v0.2.0

[v0.1.0]: https://github.com/hermannm/devlog-kotlin/compare/77067f0...v0.1.0

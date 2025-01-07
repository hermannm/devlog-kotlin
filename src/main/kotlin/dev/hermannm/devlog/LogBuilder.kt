package dev.hermannm.devlog

import kotlinx.serialization.SerializationStrategy

/**
 * Class used in the logging methods on [Logger], allowing you to add structured key-value data to
 * the log by calling the [field] and [rawJsonField] methods.
 *
 * This class is given as a function receiver to the lambda arguments on `Logger`'s methods, which
 * lets you call its methods directly in the scope of that lambda. This is a common technique for
 * creating _type-safe builders_ in Kotlin. See
 * [Kotlin docs](https://kotlinlang.org/docs/lambdas.html#function-literals-with-receiver) for more
 * on this.
 *
 * ### Example
 *
 * ```
 * private val log = getLogger {}
 *
 * fun example(user: User) {
 *   try {
 *     storeUser(user)
 *   } catch (e: Exception) {
 *     // The lambda argument passed to this logger method has a LogBuilder as its receiver, which
 *     // means that you can call `LogBuilder.field` directly in this scope.
 *     log.error(e) {
 *       field("user", user)
 *       "Failed to store user in database"
 *     }
 *   }
 * }
 * ```
 */
@JvmInline // Inline value class, since we just wrap a log event
public value class LogBuilder
@PublishedApi
internal constructor(
    @PublishedApi internal val logEvent: LogEvent,
) {
  /**
   * Adds a [log field][LogField] (structured key-value data) to the log.
   *
   * When outputting logs as JSON, this becomes a field in the logged JSON object (see example
   * below). This allows you to filter and query on the field in the log analysis tool of your
   * choice, in a more structured manner than if you were to just use string concatenation.
   *
   * The value is serialized using `kotlinx.serialization`, so if you pass an object here, you
   * should make sure it is annotated with [@Serializable][kotlinx.serialization.Serializable].
   * Alternatively, you can pass your own serializer for the value. If serialization fails, we fall
   * back to calling `toString()` on the value.
   *
   * If you have a value that is already serialized, you should use [rawJsonField] instead.
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.getLogger
   * import kotlinx.serialization.Serializable
   *
   * private val log = getLogger {}
   *
   * fun example() {
   *   val user = User(id = 1, name = "John Doe")
   *
   *   log.info {
   *     field("user", user)
   *     "Registered new user"
   *   }
   * }
   *
   * @Serializable
   * data class User(val id: Long, val name: String)
   * ```
   *
   * This gives the following output (using `logstash-logback-encoder`):
   * ```json
   * {
   *   "message": "Registered new user",
   *   "user": {
   *     "id": "1",
   *     "name": "John Doe"
   *   },
   *   // ...timestamp etc.
   * }
   * ```
   */
  public inline fun <reified ValueT : Any> field(
      key: String,
      value: ValueT?,
      serializer: SerializationStrategy<ValueT>? = null,
  ) {
    if (!logEvent.isFieldKeyAdded(key)) {
      encodeFieldValue(
          value,
          serializer,
          onJson = { jsonValue -> logEvent.addJsonField(key, jsonValue) },
          onString = { stringValue -> logEvent.addStringField(key, stringValue) },
      )
    }
  }

  /**
   * Adds a [log field][LogField] (structured key-value data) to the log, with the given
   * pre-serialized JSON value.
   *
   * When outputting logs as JSON, this becomes a field in the logged JSON object (see example
   * below). This allows you to filter and query on the field in the log analysis tool of your
   * choice, in a more structured manner than if you were to just use string concatenation.
   *
   * By default, this function checks that the given JSON string is actually valid JSON. The reason
   * for this is that giving raw JSON to our log encoder when it is not in fact valid JSON can break
   * our logs. So if the given JSON string is not valid JSON, we escape it as a string. If you are
   * 100% sure that the given JSON string is valid, you can set [validJson] to true.
   *
   * ### Example
   *
   * ```
   * import dev.hermannm.devlog.getLogger
   *
   * private val log = getLogger {}
   *
   * fun example() {
   *   val userJson = """{"id":1,"name":"John Doe"}"""
   *
   *   log.info {
   *     addRawJsonField("user", userJson)
   *     "Registered new user"
   *   }
   * }
   * ```
   *
   * This gives the following output (using `logstash-logback-encoder`):
   * ```json
   * {"message":"Registered new user","user":{"id":1,"name":"John Doe"},/* ...timestamp etc. */}
   * ```
   */
  public fun rawJsonField(key: String, json: String, validJson: Boolean = false) {
    if (!logEvent.isFieldKeyAdded(key)) {
      validateRawJson(
          json,
          validJson,
          onValidJson = { jsonValue -> logEvent.addJsonField(key, jsonValue) },
          onInvalidJson = { stringValue -> logEvent.addStringField(key, stringValue) },
      )
    }
  }

  /**
   * Adds the given [log field][LogField] to the log. This is useful when you have a previously
   * constructed field from the
   * [field][dev.hermannm.devlog.field]/[rawJsonField][dev.hermannm.devlog.rawJsonField] functions.
   * - If you want to create a new field and add it to the log, you should instead call
   *   [LogBuilder.field]
   * - If you want to add the field to all logs within a scope, you should instead use
   *   [withLoggingContext]
   */
  public fun existingField(field: LogField) {
    addField(field)
  }

  /**
   * Checks if the log [cause] exception (or any of its own cause exceptions) implements the
   * [WithLogFields] interface, and if so, adds those fields to the log.
   */
  @PublishedApi
  internal fun addFieldsFromCauseException(cause: Throwable) {
    // The `cause` here is the log event cause exception. But this exception may itself have a
    // `cause` exception, and that may have another one, and so on. We want to go through all these
    // exceptions to look for log fields, so we re-assign this local variable as we iterate through.
    var exception: Throwable? = cause
    // Limit the depth of cause exceptions, so we don't expose ourselves to infinite loops.
    // This can happen if:
    // - exception1.cause -> exception2
    // - exception2.cause -> exception3
    // - exception3.cause -> exception1
    // We set max depth to 10, which should be high enough to not affect real users.
    var depth = 0
    while (exception != null && depth < 10) {
      if (exception is WithLogFields) {
        exception.logFields.forEach(::addField)
      }

      exception = exception.cause
      depth++
    }
  }

  private fun addField(field: LogField) {
    // Don't add fields with keys that have already been added
    if (!logEvent.isFieldKeyAdded(field.key) && field.includeInLog()) {
      when (field) {
        is JsonLogField -> logEvent.addJsonField(field.key, field.value)
        is StringLogField -> logEvent.addStringField(field.key, field.value)
      }
    }
  }
}

package dev.hermannm.devlog

/**
 * Base exception class to allow you to attach [log fields][LogField] to exceptions. When passing a
 * `cause` exception to one of the methods on [Logger], it will check if the given exception is an
 * instance of this class, and if it is, these fields will be added to the log.
 *
 * The exception also includes any log fields from [withLoggingContext], from the scope in which the
 * exception is constructed. This way, we don't lose any logging context if the exception escapes
 * the context it was thrown from. If you don't want this behavior, you can create a custom
 * exception and implement the [WithLogFields] interface.
 *
 * This is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the
 * exception log. In this case, one may typically resort to string concatenation, but this class
 * allows you to have the benefits of structured logging for exceptions as well.
 *
 * ### Example
 *
 * ```
 * fun storeUser(user: User) {
 *   withLoggingContext(field("user", user)) {
 *     if (usernameTaken(user.name)) {
 *       throw ExceptionWithLogFields(
 *           "Invalid user data",
 *           logFields = listOf(field("reason", "Username taken")),
 *       )
 *     }
 *   }
 * }
 *
 * private val log = getLogger {}
 *
 * fun example(user: User) {
 *   try {
 *     storeUser(user)
 *   } catch (e: Exception) {
 *     log.error(e) { "Failed to store user" }
 *   }
 * }
 * ```
 *
 * The `log.error` would then give the following log output (using `logstash-logback-encoder`), with
 * both the `reason` field from the exception and the `user` field from the logging context:
 * ```
 * {
 *   "message": "Failed to store user",
 *   "reason": "Username taken",
 *   "user": {
 *     "id": 1,
 *     "name": "John Doe"
 *   },
 *   "stack_trace": "...",
 *   // ...timestamp etc.
 * }
 * ```
 */
public open class ExceptionWithLogFields(
    override val message: String?,
    logFields: List<LogField> = emptyList(),
    override val cause: Throwable? = null,
) : RuntimeException(), WithLogFields {
  // Final, since we want to ensure that fields from logging context are included
  final override val logFields: List<LogField> = combineFieldsWithLoggingContext(logFields)
}

/** Combines the given log fields with any fields from [withLoggingContext]. */
private fun combineFieldsWithLoggingContext(logFields: List<LogField>): List<LogField> {
  val contextFields = LoggingContext.getFieldMap()

  // If logging context is empty, we just use the given field list, to avoid allocating an
  // additional list
  if (contextFields.isNullOrEmpty()) {
    return logFields
  }

  val combinedFields =
      ArrayList<LogField>(
          // Initialize capacity for both exception fields and context fields
          logFields.size + LoggingContext.getNonNullFieldCount(contextFields),
      )
  // Add exception log fields first, so they show first in the log output
  combinedFields.addAll(logFields)
  LoggingContext.mapFieldMapToList(contextFields, target = combinedFields)
  return combinedFields
}

/**
 * Interface to allow you to attach [log fields][LogField] to exceptions. When passing a `cause`
 * exception to one of the methods on [Logger], it will check if the given exception implements this
 * interface, and if it does, these fields will be added to the log.
 *
 * This is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the
 * exception log. In this case, one may typically resort to string concatenation, but this interface
 * allows you to have the benefits of structured logging for exceptions as well.
 *
 * If you want to include log fields from [withLoggingContext] on the exception, you should instead
 * throw or extend the [ExceptionWithLogFields] base class.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.WithLogFields
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 *
 * class InvalidUserData(user: User) : RuntimeException(), WithLogFields {
 *   override val message = "Invalid user data"
 *   override val logFields = listOf(field("user", user))
 * }
 *
 * fun storeUser(user: User) {
 *   if (!user.isValid()) {
 *     throw InvalidUserData(user)
 *   }
 * }
 *
 * private val log = getLogger {}
 *
 * fun example(user: User) {
 *   try {
 *     storeUser(user)
 *   } catch (e: Exception) {
 *     log.error(e) { "Failed to store user" }
 *   }
 * }
 * ```
 *
 * The `log.error` would then give the following log output (using `logstash-logback-encoder`), with
 * the `user` log field from `InvalidUserData` attached:
 * ```
 * {
 *   "message": "Failed to store user",
 *   "user": {
 *     "id": 1,
 *     "name": "John Doe"
 *   },
 *   "stack_trace": "...",
 *   // ...timestamp etc.
 * }
 * ```
 */
public interface WithLogFields {
  /** Will be attached to the log when passed through `cause` to one of [Logger]'s methods. */
  public val logFields: List<LogField>
}

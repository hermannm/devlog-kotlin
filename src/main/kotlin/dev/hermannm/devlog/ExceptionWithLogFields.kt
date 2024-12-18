package dev.hermannm.devlog

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
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.WithLogFields
 * import dev.hermannm.devlog.field
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
 * private val log = Logger {}
 *
 * fun example(user: User) {
 *   try {
 *     storeUser(user)
 *   } catch (e: Exception) {
 *     log.error {
 *       cause = e
 *       "Failed to store user"
 *     }
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
interface WithLogFields {
  /** Will be attached to the log when passed through `cause` to one of [Logger]'s methods. */
  val logFields: List<LogField>
}

/**
 * Base exception class implementing the [WithLogFields] interface for attaching structured data to
 * the exception when it's logged. If you don't want to create a custom exception and implement
 * [WithLogFields] on it, you can use this class instead.
 */
open class ExceptionWithLogFields(
    override val message: String?,
    override val logFields: List<LogField>,
    override val cause: Throwable? = null,
) : RuntimeException(), WithLogFields

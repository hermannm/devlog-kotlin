package dev.hermannm.devlog

import kotlin.concurrent.getOrSet
import net.logstash.logback.marker.SingleFieldAppendingMarker

@PublishedApi internal val loggingContext = ThreadLocal<ArrayList<SingleFieldAppendingMarker>>()

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = Logger {}
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * The field from `withLoggingContext` will then be attached to every log, like:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 *
 * ### Note on coroutines
 *
 * `withLoggingContext` uses a thread-local, so it won't work with Kotlin coroutines and `suspend`
 * functions (though it does work with Java virtual threads). An alternative that supports
 * coroutines may be added in a future version of the library.
 */
inline fun <ReturnT> withLoggingContext(vararg logFields: LogField, block: () -> ReturnT): ReturnT {
  return withLoggingContext(logFields.asList(), block)
}

/**
 * Adds the given [log fields][LogField] to every log made by a [Logger] in the context of the given
 * [block].
 *
 * An example of when this is useful is when processing an event, and you want the event to be
 * attached to every log while processing it. Instead of manually attaching the event to each log,
 * you can wrap the event processing in `withLoggingContext` with the event as a log field, and then
 * all logs inside that context will include the event.
 *
 * This overload of the function takes a list instead of varargs, for when you already have a list
 * of log fields available. This can be used together with [getLoggingContext] to pass context
 * fields between threads (see example in [getLoggingContext] docstring).
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.withLoggingContext
 *
 * private val log = Logger {}
 *
 * fun example(event: Event) {
 *   withLoggingContext(field("event", event)) {
 *     log.debug { "Started processing event" }
 *     // ...
 *     log.debug { "Finished processing event" }
 *   }
 * }
 * ```
 *
 * The field from `withLoggingContext` will then be attached to every log, like:
 * ```json
 * { "message": "Started processing event", "event": { /* ... */  } }
 * { "message": "Finished processing event", "event": { /* ... */  } }
 * ```
 *
 * ### Note on coroutines
 *
 * `withLoggingContext` uses a thread-local, so it won't work with Kotlin coroutines and `suspend`
 * functions (though it does work with Java virtual threads). An alternative that supports
 * coroutines may be added in a future version of the library.
 */
inline fun <ReturnT> withLoggingContext(logFields: List<LogField>, block: () -> ReturnT): ReturnT {
  // Passing empty log fields to this function would be strange, but it may happen if fields are
  // passed dynamically (or when using inheritLoggingContext). In these cases, we don't have to
  // touch loggingContext and can just call the block directly.
  if (logFields.isEmpty()) {
    return block()
  }

  val contextFields = loggingContext.getOrSet { ArrayList(logFields.size) }
  contextFields.ensureCapacity(contextFields.size + logFields.size)
  // We add context fields in reverse when adding them to log events, to show the newest field
  // first. But if we called `withLoggingContext` with multiple fields, this would cause these
  // fields to show in reverse order to how they were passed. So to counteract that, we add the
  // fields to the logging context here in reverse order.
  logFields.forEachReversed { field -> contextFields.add(field.logstashField) }

  try {
    return block()
  } finally {
    for (i in logFields.indices) {
      contextFields.removeLast()
    }
    // Reset thread-local if list is empty, to avoid keeping the allocation around forever
    if (contextFields.isEmpty()) {
      loggingContext.remove()
    }
  }
}

/**
 * Returns a copy of the log fields in the current thread's logging context (from
 * [withLoggingContext]). This can be used to pass logging context between threads (see example
 * below).
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.Logger
 * import dev.hermannm.devlog.getLoggingContext
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.withLoggingContext
 * import java.util.concurrent.ExecutorService
 *
 * private val log = Logger {}
 *
 * class UserService(
 *     private val userRepository: UserRepository,
 *     private val emailService: EmailService,
 *     private val executor: ExecutorService,
 * ) {
 *   fun registerUser(user: User) {
 *     withLoggingContext(field("user", user)) {
 *       userRepository.create(user)
 *       sendWelcomeEmail(user)
 *     }
 *   }
 *
 *   // In this hypothetical, we don't want sendWelcomeEmail to block registerUser, so we use an
 *   // ExecutorService to spawn a thread.
 *   //
 *   // But we want to log if it fails, and include the logging context from the parent thread.
 *   // This is where getLoggingContext comes in.
 *   private fun sendWelcomeEmail(user: User) {
 *     // We call getLoggingContext here, so that we get the context fields from the parent thread
 *     val loggingContext = getLoggingContext()
 *
 *     executor.execute {
 *       // We then pass the parent context to withLoggingContext here in the child thread
 *       withLoggingContext(loggingContext) {
 *         try {
 *           emailService.sendEmail(to = user.email, content = makeWelcomeEmailContent(user))
 *         } catch (e: Exception) {
 *           // This log will get the "user" field from the parent logging context
 *           log.error {
 *             cause = e
 *             "Failed to send welcome email to user"
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
fun getLoggingContext(): List<LogField> {
  val loggingContext = getLogFieldsFromContext()

  // If the logging context is empty, we can avoid a list allocation by returning emptyList, which
  // uses a singleton
  if (loggingContext.isEmpty()) {
    return emptyList()
  }

  // `map` copies the list
  return loggingContext.map { field -> LogField(field) }
}

/**
 * We use this instead of [getLoggingContext] internally, as we can avoid copying the list when we
 * know that we don't pass it between threads.
 */
internal fun getLogFieldsFromContext(): List<SingleFieldAppendingMarker> {
  // loggingContext list will be null if withLoggingContext has not been called in this thread
  return loggingContext.get() ?: emptyList()
}

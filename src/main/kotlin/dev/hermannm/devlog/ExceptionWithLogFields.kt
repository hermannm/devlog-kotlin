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
 * This class is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the
 * exception log. In this case, one may typically resort to string concatenation, but this class
 * allows you to have the benefits of structured logging for exceptions as well.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.ExceptionWithLogFields
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 *
 * private val log = getLogger {}
 *
 * fun example(event: OrderUpdateEvent) {
 *   try {
 *     processOrderUpdate(event)
 *   } catch (e: Exception) {
 *     log.error(e) { "Failed to process order update event" }
 *   }
 * }
 *
 * fun processOrderUpdate(event: OrderUpdateEvent) {
 *   withLoggingContext(field("event", event)) {
 *     val order = getOrder(event.orderId)
 *
 *     if (!order.canBeUpdated()) {
 *       throw ExceptionWithLogFields(
 *           "Received update event for finalized order",
 *           logFields = listOf(field("order", order)),
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * The `log.error` would then give the following log output (using `logstash-logback-encoder`), with
 * both the `order` field from the exception and the `event` field from the logging context:
 * ```
 * {
 *   "message": "Failed to process order update event",
 *   "order": { ... },
 *   "event": { ... },
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

  /**
   * Alternative [ExceptionWithLogFields] constructor that takes [log fields][LogField] as varargs,
   * so you don't have to wrap them in `listOf()`.
   *
   * To pass [cause], use a named parameter.
   */
  public constructor(
      message: String?,
      vararg logFields: LogField,
      cause: Throwable? = null,
  ) : this(message, logFields.asList(), cause)

  /**
   * Alternative [ExceptionWithLogFields] constructor that defaults [message] to `cause.message` (if
   * any). This lets you:
   * - Wrap a cause exception with log fields, and use the cause exception's message
   * - Extend `ExceptionWithLogFields` and override `message`, without having to pass it through the
   *   constructor
   */
  public constructor(
      logFields: List<LogField> = emptyList(),
      cause: Throwable? = null,
  ) : this(message = cause?.message, logFields, cause)

  /**
   * Alternative [ExceptionWithLogFields] constructor that both:
   * - Takes [log fields][LogField] as varargs, so you don't have to wrap them in `listOf()`
   * - Defaults [message] to `cause.message`. This lets you:
   *     - Wrap a cause exception with log fields, and use the cause exception's message
   *     - Extend `ExceptionWithLogFields` and override `message`, without having to pass it through
   *       the constructor
   *
   * To pass [cause], use a named parameter.
   */
  public constructor(
      vararg logFields: LogField,
      cause: Throwable? = null,
  ) : this(message = cause?.message, logFields.asList(), cause)
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
 * private val log = getLogger {}
 *
 * fun example(order: Order) {
 *   try {
 *     updateOrder(order)
 *   } catch (e: Exception) {
 *     log.error(e) { "Failed to update order" }
 *   }
 * }
 *
 * fun updateOrder(order: Order) {
 *   if (!order.canBeUpdated()) {
 *     throw InvalidOrderState("Cannot update finalized order", order)
 *   }
 * }
 *
 * class InvalidOrderState(
 *     override val message: String,
 *     order: Order,
 * ) : RuntimeException(), WithLogFields {
 *   override val logFields = listOf(field("order", order))
 * }
 * ```
 *
 * The `log.error` would then give the following log output (using `logstash-logback-encoder`), with
 * the `order` log field from `InvalidOrderState` attached:
 * ```
 * {
 *   "message": "Failed to update order",
 *   "order": { ... },
 *   "stack_trace": "...",
 *   // ...timestamp etc.
 * }
 * ```
 */
public interface WithLogFields {
  /** Will be attached to the log when passed through `cause` to one of [Logger]'s methods. */
  public val logFields: List<LogField>
}

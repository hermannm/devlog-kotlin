// `kotlin.jvm` is auto-imported on JVM, but for multiplatform we need to use fully-qualified name
@file:Suppress("RemoveRedundantQualifierName")

package dev.hermannm.devlog

/**
 * Base exception class to allow you to attach [log fields][LogField] to exceptions. When passing a
 * `cause` exception to one of the methods on [Logger], it will check if the given exception is an
 * instance of this class, and if it is, these fields will be added to the log.
 *
 * Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * The exception also includes any log fields from [withLoggingContext], from the scope in which the
 * exception is constructed. This way, we don't lose any logging context if the exception escapes
 * the context it was thrown from. If you don't want this behavior, you can create a custom
 * exception and implement the [HasLogFields] interface.
 *
 * This class is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the
 * exception log. In this case, one may typically resort to string concatenation, but this class
 * allows you to have the benefits of structured logging for exceptions as well.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.ExceptionWithLoggingContext
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 *
 * private val log = getLogger()
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
 *       throw ExceptionWithLoggingContext(
 *           "Received update event for finalized order",
 *           field("order", order),
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
 *   "stack_trace": "...ExceptionWithLoggingContext: Received update event for finalized order...",
 *   "order": { ... },
 *   "event": { ... },
 *   // ...timestamp etc.
 * }
 * ```
 *
 * ### Constructors
 *
 * `ExceptionWithLoggingContext` provides 4 constructor overloads:
 * - `(message: String?, logFields: Collection<LogField>, cause: Throwable?)`
 *     - Primary constructor taking an exception message, a collection of log fields and an optional
 *       cause exception
 * - `(message: String?, vararg logFields: LogField, cause: Throwable?)`
 *     - Takes log fields as varargs, so you don't have to wrap them in a list
 *     - To pass `cause`, use a named argument
 * - `(logFields: Collection<LogField>, cause: Throwable?)`
 *     - Defaults `message` to `cause.message`. This lets you:
 *         - Wrap a cause exception with log fields, and use the cause exception's message
 *         - Extend `ExceptionWithLoggingContext` and override `message`, without having to pass it
 *           through the constructor
 * - `(vararg logFields: LogField, cause: Throwable?)`
 *     - Combines the two previous constructors, to let you extend `ExceptionWithLoggingContext` and
 *       override `message` while also passing log fields as varargs
 */
public open class ExceptionWithLoggingContext(
    /** The exception message. */
    message: String?,
    override val logFields: Collection<LogField> = emptyList(),
    /**
     * The cause of the exception. If you're throwing this exception after catching another, you
     * should include the original exception here.
     */
    override val cause: Throwable? = null,
) : RuntimeException(), HasLogFields {
  public constructor(
      message: String?,
      vararg logFields: LogField,
      cause: Throwable? = null,
  ) : this(message, logFields.asList(), cause)

  public constructor(
      logFields: Collection<LogField> = emptyList(),
      cause: Throwable? = null,
  ) : this(message = null, logFields, cause)

  public constructor(
      vararg logFields: LogField,
      cause: Throwable? = null,
  ) : this(message = null, logFields.asList(), cause)

  internal val loggingContext: LoggingContext = getLoggingContextForException(cause)

  private val messageField: String? = message
  override val message: String?
    get() {
      val cause = this.cause
      return when {
        messageField != null -> messageField
        cause != null -> getMessageFromCauseException(cause)
        else -> null
      }
    }

  private companion object {
    @kotlin.jvm.JvmStatic
    private fun getLoggingContextForException(cause: Throwable?): LoggingContext {
      if (cause != null && hasContextForException(cause)) {
        return EMPTY_LOGGING_CONTEXT
      } else {
        return getLoggingContext()
      }
    }

    @kotlin.jvm.JvmStatic
    private fun getMessageFromCauseException(cause: Throwable): String? {
      val className = cause::class.simpleName
      val message = cause.message
      return when {
        className != null && message != null -> "${className}: ${message}"
        className == null && message != null -> message
        className != null && message == null -> className
        else -> null
      }
    }
  }
}

/**
 * Interface to allow you to attach [log fields][LogField] to exceptions. When passing a `cause`
 * exception to one of the methods on [Logger], it will check if the given exception implements this
 * interface, and if it does, these fields will be added to the log.
 *
 * Use the [field]/[rawJsonField] functions to construct log fields.
 *
 * This is useful when you are throwing an exception from somewhere down in the stack, but do
 * logging further up the stack, and you have structured data that you want to attach to the
 * exception log. In this case, one may typically resort to string concatenation, but this interface
 * allows you to have the benefits of structured logging for exceptions as well.
 *
 * If you want to include log fields from [withLoggingContext] on the exception, you should instead
 * throw or extend the [ExceptionWithLoggingContext] base class.
 *
 * ### Example
 *
 * ```
 * import dev.hermannm.devlog.HasLogFields
 * import dev.hermannm.devlog.field
 * import dev.hermannm.devlog.getLogger
 *
 * private val log = getLogger()
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
 *     throw OrderUpdateException("Cannot update finalized order", order)
 *   }
 * }
 *
 * class OrderUpdateException(
 *     override val message: String,
 *     order: Order,
 * ) : RuntimeException(), HasLogFields {
 *   override val logFields = listOf(field("order", order))
 * }
 * ```
 *
 * The `log.error` would then give the following log output (using `logstash-logback-encoder`), with
 * the `order` log field from `OrderUpdateException` attached:
 * ```
 * {
 *   "message": "Failed to update order",
 *   "stack_trace": "...OrderUpdateException: Cannot update finalized order...",
 *   "order": { ... },
 *   // ...timestamp etc.
 * }
 * ```
 */
public interface HasLogFields {
  /** Will be attached to the log when passed through `cause` to one of [Logger]'s methods. */
  public val logFields: Collection<LogField>
}

internal expect class LoggingContextProvider : RuntimeException {
  constructor(loggingContext: LoggingContext)

  val loggingContext: LoggingContext
}

/**
 * Traverses the exception tree from the given root exception: Its cause exceptions and suppressed
 * exceptions, and any of their own cause or suppressed exceptions. The traversal is
 * [pre-order (depth-first)](https://en.wikipedia.org/wiki/Tree_traversal#Depth-first_search),
 * visiting the root exception first, then cause exceptions, then suppressed exceptions in order.
 *
 * The implementation tries to keep allocations to a minimum, prioritizing the happy path (see
 * [ExceptionParent] for how we do this). We also set a [MAX_EXCEPTION_TRAVERSAL_DEPTH], so we don't
 * fall victim to malicious cyclical cause exceptions.
 */
internal inline fun traverseExceptionTree(root: Throwable, action: (Throwable) -> Unit) {
  var currentException: Throwable = root
  var parent: ExceptionParent? = null
  var depth = 0

  exceptionLoop@ while (true) {
    action(currentException)

    // If we are not at the max depth, traverse child exceptions (cause + suppressed exceptions)
    if (depth < MAX_EXCEPTION_TRAVERSAL_DEPTH - 1) {
      val suppressedExceptions = getSuppressedExceptions(currentException)
      val hasSuppressedExceptions = !suppressedExceptions.isNullOrEmpty()

      // First, check if we have a cause exception. If so, set that as the next to traverse
      val causeException = currentException.cause
      if (causeException != null) {
        // We only have to set the parent if there are sibling exceptions to traverse. If we already
        // have a parent but no siblings, we can leave the parent as-is (as a "grandparent"), since
        // `ExceptionParent.depth` allows us to jump up multiple levels in the tree
        if (hasSuppressedExceptions) {
          parent =
              ExceptionParent(
                  currentException,
                  depth = depth,
                  suppressedExceptions,
                  // Since we traverse the cause exception now, the next child exception to traverse
                  // is the first suppressed exception
                  nextSuppressedExceptionIndex = 0,
                  parent,
              )
        }
        currentException = causeException
        depth++
        continue@exceptionLoop
      }

      // If there's no cause exception, look for suppressed exceptions. If we have suppressed
      // exceptions, set the first suppressed exception as the next to traverse
      if (hasSuppressedExceptions) {
        // We only have to initialize the parent if there are more suppressed exceptions to
        // traverse afterwards
        if (suppressedExceptions.size > 1) {
          parent =
              ExceptionParent(
                  currentException,
                  depth = depth,
                  suppressedExceptions,
                  // Since we traverse the first suppressed exception now, the next to traverse is
                  // the second suppressed exception
                  nextSuppressedExceptionIndex = 1,
                  parent,
              )
        }
        currentException = suppressedExceptions.first()
        depth++
        continue@exceptionLoop
      }
    }

    // If there were no child exceptions to traverse, move up to the parent to check if there are
    // sibling exceptions left further up the tree that we need to traverse
    while (parent != null) {
      val index = parent.nextSuppressedExceptionIndex
      if (index < parent.suppressedExceptions.size) {
        currentException = parent.suppressedExceptions[index]
        // The tree depth of a child exception is one deeper than its parent
        depth = parent.depth + 1
        // Increment the index for the next suppressed exception to traverse
        parent.nextSuppressedExceptionIndex = index + 1
        continue@exceptionLoop
      }

      // If we get here, there were no siblings left to traverse, so we move further up the tree
      parent = parent.parent
    }

    // If we get here, then we have traversed all cause and suppressed exceptions
    return
  }
}

/**
 * State object to allow us to move back up the exception tree in [traverseExceptionTree]. We need
 * this in order to move between sibling exceptions (cause + suppressed exceptions).
 *
 * This is essentially a singly linked list. We use this instead of pre-allocating a collection data
 * structure, so that the base case of there not being multiple children (i.e. both cause and
 * suppressed exceptions) remains allocation-free.
 */
internal class ExceptionParent(
    /** The parent exception. */
    @JvmField val exception: Throwable,
    /**
     * The depth in the exception tree we're currently traversing where this exception is located.
     */
    @JvmField val depth: Int,
    /**
     * The suppressed exceptions of [exception]. We store this here, because getting suppressed
     * exceptions potentially does an array copy, so we can avoid that allocation by keeping the
     * result here.
     *
     * This is non-null, since we only need to allocate an `ExceptionParent` if we have suppressed
     * exceptions to traverse on it.
     */
    @JvmField val suppressedExceptions: List<Throwable>,
    /**
     * The index of the next exception in [suppressedExceptions] that should be traversed.
     *
     * We include this here in order to avoid exception cycles between sibling exceptions. One can
     * imagine a malicious actor constructing an exception with both the cause and a suppressed
     * exception set to the same exception instance, which may cause an infinite loop in a naive
     * implementation of exception traversal.
     */
    @JvmField var nextSuppressedExceptionIndex: Int,
    /** The parent of this parent exception (i.e., the grandparent). */
    @JvmField val parent: ExceptionParent?,
)

/**
 * The maximum depth of child exceptions to traverse in [traverseExceptionTree]. We expect a depth
 * of 10 exceptions to cover most realistic cases of exception wrapping.
 */
private const val MAX_EXCEPTION_TRAVERSAL_DEPTH = 10

/**
 * Kotlin provides a platform-independent [Throwable.suppressedExceptions] extension function, which
 * call's Java's `Throwable.getSuppressed` method, and wraps the returned array in a list. But this
 * allocates a wrapper object for the normal case of there being no suppressed exceptions! Since we
 * check suppressed exceptions in [traverseExceptionTree], we want to avoid these redundant
 * allocations. So we write our own platform-specific function to return `null` when Java's
 * `Throwable.getSuppressed` returns an empty array.
 */
internal expect fun getSuppressedExceptions(exception: Throwable): List<Throwable>?

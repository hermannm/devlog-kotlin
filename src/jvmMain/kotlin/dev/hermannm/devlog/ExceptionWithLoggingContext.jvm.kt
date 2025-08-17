@file:JvmName("ExceptionWithLoggingContextJvm")

package dev.hermannm.devlog

// See docstring on the `expect` declaration of this class under `commonMain`
internal actual class LoggingContextProvider
actual constructor(
    @kotlin.concurrent.Volatile private var loggingContext: Array<out LogField>,
) : RuntimeException() {
  actual fun addLoggingContext(logFields: Array<out LogField>) {
    // We need to cast to `Array<LogField>` in order to use the `+` operator here (which we want to
    // use, since it uses optimized `System.arraycopy` on JVM).
    // We can safely cast from `Array<out LogField>` to `Array<LogField>`, since `LogField` has no
    // subclasses, so this doesn't break covariance.
    this.loggingContext = (this.loggingContext as Array<LogField>) + logFields
  }

  actual fun addFieldsToLog(logBuilder: LogBuilder) {
    logBuilder.addFields(loggingContext)
  }

  override val message: String?
    get() = "Added log fields from exception logging context"

  override fun fillInStackTrace(): Throwable {
    return this
  }

  override fun getStackTrace(): Array<out StackTraceElement> {
    return EMPTY_STACK_TRACE
  }

  override fun setStackTrace(stackTrace: Array<out StackTraceElement>) {
    return
  }
}

/**
 * `emptyArray` may allocate, so we initialize a static empty stack trace here, so it only allocates
 * once.
 */
private val EMPTY_STACK_TRACE: Array<out StackTraceElement> = emptyArray()

internal actual fun getSuppressedExceptions(exception: Throwable): List<Throwable>? {
  val suppressedExceptions: Array<Throwable> = exception.suppressed
  if (suppressedExceptions.isEmpty()) {
    return null
  } else {
    return suppressedExceptions.asList()
  }
}

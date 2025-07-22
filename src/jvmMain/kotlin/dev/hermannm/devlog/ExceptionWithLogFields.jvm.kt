package dev.hermannm.devlog

import java.io.PrintStream
import java.io.PrintWriter

internal actual class LoggingContextProvider
actual constructor(
    @kotlin.concurrent.Volatile actual override var logFields: Collection<LogField>,
) : Throwable(), HasLogFields {
  override val message: String?
    get() = "Added log fields from exception"

  override fun fillInStackTrace(): Throwable {
    return this
  }

  override fun getStackTrace(): Array<out StackTraceElement> {
    return EMPTY_STACK_TRACE
  }

  override fun setStackTrace(stackTrace: Array<out StackTraceElement>) {
    return
  }

  override fun printStackTrace() {
    return
  }

  override fun printStackTrace(s: PrintStream?) {
    return
  }

  override fun printStackTrace(s: PrintWriter?) {
    return
  }

  private companion object {
    /**
     * `emptyArray` may allocate, so we initialize the empty stack trace here on the companion
     * object, so it only allocates once.
     */
    private val EMPTY_STACK_TRACE: Array<out StackTraceElement> = emptyArray()
  }
}

internal actual fun getSuppressedExceptions(exception: Throwable): List<Throwable>? {
  val suppressedExceptions: Array<Throwable> = exception.suppressed
  if (suppressedExceptions.isEmpty()) {
    return null
  } else {
    return suppressedExceptions.asList()
  }
}

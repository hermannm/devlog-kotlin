@file:Suppress("RemoveRedundantQualifierName")

package dev.hermannm.devlog

import java.io.PrintStream
import java.io.PrintWriter

internal actual class WrappedException
actual constructor(
    @kotlin.jvm.JvmField internal actual val wrapped: Throwable,
    override val logFields: List<LogField>,
) : RuntimeException(), HasLogFields {
  actual override val message: String
    get() {
      propagateSuppressedExceptions()
      return "${wrapped.javaClass.simpleName}: ${wrapped.message}"
    }

  override val cause: Throwable?
    get() {
      propagateSuppressedExceptions()
      return wrapped.cause
    }

  override fun toString(): String {
    propagateSuppressedExceptions()
    return wrapped.toString()
  }

  override fun getLocalizedMessage(): String {
    propagateSuppressedExceptions()
    return "${wrapped.javaClass.simpleName}: ${wrapped.localizedMessage}"
  }

  override fun getStackTrace(): Array<out StackTraceElement> {
    propagateSuppressedExceptions()
    return wrapped.stackTrace
  }

  override fun fillInStackTrace(): Throwable {
    return this
  }

  override fun printStackTrace() {
    propagateSuppressedExceptions()
    wrapped.printStackTrace()
  }

  override fun printStackTrace(stream: PrintStream) {
    propagateSuppressedExceptions()
    wrapped.printStackTrace(stream)
  }

  override fun printStackTrace(writer: PrintWriter) {
    propagateSuppressedExceptions()
    wrapped.printStackTrace(writer)
  }

  override fun setStackTrace(stackTrace: Array<out StackTraceElement>) {
    propagateSuppressedExceptions()
    wrapped.stackTrace = stackTrace
  }

  override fun initCause(cause: Throwable?): Throwable {
    propagateSuppressedExceptions()
    wrapped.initCause(cause)
    return this
  }

  private fun propagateSuppressedExceptions() {
    val wrapperSuppressedExceptions = this.suppressed
    if (wrapperSuppressedExceptions.isEmpty()) {
      return
    }

    val wrappedSuppressedExceptions = wrapped.suppressed
    for (wrapperSuppressedException in wrapperSuppressedExceptions) {
      if (wrappedSuppressedExceptions.none { it === wrapperSuppressedException }) {
        wrapped.addSuppressed(wrapperSuppressedException)
      }
    }
  }
}

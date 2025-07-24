@file:JvmName("ExceptionWithLoggingContextJvm")

package dev.hermannm.devlog

internal actual fun getSuppressedExceptions(exception: Throwable): List<Throwable>? {
  val suppressedExceptions: Array<Throwable> = exception.suppressed
  if (suppressedExceptions.isEmpty()) {
    return null
  } else {
    return suppressedExceptions.asList()
  }
}

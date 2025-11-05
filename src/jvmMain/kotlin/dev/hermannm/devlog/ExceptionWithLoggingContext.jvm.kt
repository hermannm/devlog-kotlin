@file:JvmName("ExceptionWithLoggingContextJvm")

package dev.hermannm.devlog

// See docstring on the `expect` declaration in `ExceptionWithLoggingContext.kt` under `commonMain`.
internal actual class LoggingContextProvider
actual constructor(
    @kotlin.concurrent.Volatile private var contextFields: Array<out LogField>,
) : RuntimeException() {
  /**
   * We set this to true when [addFieldsToLog] is called.
   *
   * See [getLoggingContextProviderMessage] for why we want this state.
   */
  @kotlin.concurrent.Volatile private var fieldsAddedToLog = false

  actual fun addLoggingContext(newContextFields: Array<out LogField>) {
    // We need to cast to `Array<LogField>` in order to use the `+` operator here (which we want to
    // use, since it uses optimized `System.arraycopy` on JVM).
    // We can safely cast from `Array<out LogField>` to `Array<LogField>`, since `LogField` has no
    // subclasses, so this doesn't break covariance.
    @Suppress("UNCHECKED_CAST")
    this.contextFields = (this.contextFields as Array<LogField>) + newContextFields
  }

  actual fun addFieldsToLog(logBuilder: LogBuilder) {
    logBuilder.addFields(contextFields)
    this.fieldsAddedToLog = true
  }

  @Suppress("RedundantNullableReturnType") // Must have same signature as `expect` declaration
  override val message: String?
    get() = getLoggingContextProviderMessage(contextFields, fieldsAddedToLog)

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

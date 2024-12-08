package dev.hermannm.devlog

internal inline fun <T> List<T>.forEachReversed(action: (T) -> Unit) {
  // downTo returns an empty range if first argument is less than the second, so we don't need to
  // check bounds here
  for (i in (this.size - 1) downTo 0) {
    action(this[i])
  }
}

@PublishedApi // PublishedApi to use this in inline `withLoggingContext` function.
internal inline fun <T> Array<T>.forEachReversed(action: (T) -> Unit) {
  // downTo returns an empty range if first argument is less than the second, so we don't need to
  // check bounds here
  for (i in (this.size - 1) downTo 0) {
    action(this[i])
  }
}

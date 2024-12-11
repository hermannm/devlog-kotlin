// We unfortunately have to duplicate code between List and Array implementations, since they have
// no common interface, and we don't want to take on the runtime cost of converting between them.
@file:Suppress("DuplicatedCode")

package dev.hermannm.devlog

@PublishedApi // For use in inline functions
internal inline fun <T> List<T>.forEachReversed(action: (Int, T) -> Unit) {
  // downTo returns an empty range if first argument is less than the second, so we don't need to
  // check bounds here
  for (index in (this.size - 1) downTo 0) {
    action(index, this[index])
  }
}

/**
 * Returns true if the given [predicate] returns true for any elements in the list _before_
 * [beforeIndex]. If [reverse] is true, starts the iteration from the end of the list and goes down
 * to [beforeIndex].
 */
internal inline fun <T> List<T>.anyBefore(
    beforeIndex: Int,
    reverse: Boolean = false,
    predicate: (T) -> Boolean
): Boolean {
  val range =
      if (reverse) {
        (this.size - 1) downTo beforeIndex + 1
      } else {
        0 until beforeIndex
      }

  for (index in range) {
    if (predicate(this[index])) {
      return true
    }
  }

  return false
}

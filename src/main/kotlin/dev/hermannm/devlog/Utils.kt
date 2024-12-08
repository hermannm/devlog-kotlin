package dev.hermannm.devlog

internal inline fun <OldT, reified NewT> Array<OldT>.mapArray(
    transform: (OldT) -> NewT
): Array<NewT> {
  return Array(size = this.size) { index ->
    val old = this[index]
    transform(old)
  }
}

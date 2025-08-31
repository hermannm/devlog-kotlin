@file:JvmName("LogFieldJvm")

package dev.hermannm.devlog

internal actual fun fieldValueShouldUseToString(value: Any): Boolean {
  return when (value) {
    is java.time.Instant,
    is java.util.UUID,
    is java.net.URI,
    is java.net.URL,
    is java.math.BigDecimal -> true
    else -> false
  }
}

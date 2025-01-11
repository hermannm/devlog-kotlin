package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID

@PublishedApi
internal actual fun fieldValueShouldUseToString(value: Any): Boolean {
  return when (value) {
    is Instant,
    is UUID,
    is URI,
    is URL,
    is BigDecimal -> true
    else -> false
  }
}

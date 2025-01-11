package dev.hermannm.devlog

import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

@PublishedApi
internal actual fun fieldValueShouldUseToString(type: KClass<*>): Boolean {
  return when (type) {
    Instant::class,
    UUID::class,
    URI::class,
    URL::class,
    BigDecimal::class -> true
    else -> false
  }
}

@file:JvmName("LogFieldJvm")

package dev.hermannm.devlog

import kotlinx.serialization.Serializable
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JacksonSerializable
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.jsontype.TypeSerializer

// See docstring on the `expect` declaration in `LogField.kt` under `commonMain`.
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

// See docstring on the `expect` declaration in `LogField.kt` under `commonMain`.
@Serializable(with = RawJsonSerializer::class) public actual sealed interface RawJson

internal actual class ValidRawJson
internal actual constructor(
    @JvmField internal actual val json: String,
) : RawJson, JacksonSerializable {
  override fun toString() = json

  override fun serialize(generator: JsonGenerator, context: SerializationContext) {
    generator.writeRawValue(json)
  }

  override fun serializeWithType(
      generator: JsonGenerator,
      context: SerializationContext,
      typeSerializer: TypeSerializer,
  ) {
    // Since we don't know what type the raw JSON is, we can only redirect to normal serialization
    serialize(generator, context)
  }

  override fun equals(other: Any?): Boolean = other is ValidRawJson && this.json == other.json

  override fun hashCode(): Int = json.hashCode()
}

internal actual class NotValidJson
internal actual constructor(
    @JvmField internal actual val value: String,
) : RawJson, JacksonSerializable {
  override fun toString() = value

  override fun serialize(generator: JsonGenerator, context: SerializationContext) {
    generator.writeString(value)
  }

  override fun serializeWithType(
      generator: JsonGenerator,
      context: SerializationContext,
      typeSerializer: TypeSerializer,
  ) {
    serialize(generator, context)
  }

  override fun equals(other: Any?): Boolean = other is NotValidJson && this.value == other.value

  override fun hashCode(): Int = value.hashCode()
}

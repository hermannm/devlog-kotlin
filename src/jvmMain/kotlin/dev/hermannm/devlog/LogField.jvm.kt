@file:JvmName("LogFieldJvm")

package dev.hermannm.devlog

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializable
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import kotlinx.serialization.Serializable

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
) : RawJson, JsonSerializable {
  override fun toString() = json

  override fun serialize(generator: JsonGenerator, serializers: SerializerProvider) {
    generator.writeRawValue(json)
  }

  override fun serializeWithType(
      generator: JsonGenerator,
      serializers: SerializerProvider,
      typeSerializer: TypeSerializer,
  ) {
    // Since we don't know what type the raw JSON is, we can only redirect to normal serialization
    serialize(generator, serializers)
  }

  override fun equals(other: Any?): Boolean = other is ValidRawJson && this.json == other.json

  override fun hashCode(): Int = json.hashCode()
}

internal actual class NotValidJson
internal actual constructor(
    @JvmField internal actual val value: String,
) : RawJson, JsonSerializable {
  override fun toString() = value

  override fun serialize(generator: JsonGenerator, serializers: SerializerProvider) {
    generator.writeString(value)
  }

  override fun serializeWithType(
      generator: JsonGenerator,
      serializers: SerializerProvider,
      typeSerializer: TypeSerializer,
  ) {
    serialize(generator, serializers)
  }

  override fun equals(other: Any?): Boolean = other is NotValidJson && this.value == other.value

  override fun hashCode(): Int = value.hashCode()
}

package dev.vitngan.harness.core.hermes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull

/**
 * JSON helpers for the Hermes wire.
 *
 * Centralised so that no layer above this one has to know how tolerant the
 * parser is. Every accessor is total: a field of the wrong type reads as null
 * rather than throwing, because the gateway is an evolving peer and a version
 * skew must degrade, not crash.
 */
internal object HermesJson {

    val codec: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    fun emptyObject(): JsonObject = JsonObject(emptyMap())

    fun encodeToString(element: JsonElement): String = codec.encodeToString(JsonElement.serializer(), element)

    /** @throws IllegalArgumentException when the text is not a JSON object. */
    fun parseObject(text: String): JsonObject =
        codec.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("frame is not a JSON object")

    fun buildObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject(block)
}

/** The primitive's string form, or null when this is not a string primitive. */
internal fun JsonElement?.asString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

/**
 * The primitive's text, whatever its JSON type. Used for numeric ids.
 *
 * A JSON `null` reads as a null id rather than the literal string "null",
 * because `"id": null` means "no id" on the wire.
 */
internal fun JsonElement?.asRawString(): String? {
    if (this is JsonNull) return null
    return (this as? JsonPrimitive)?.content
}

/** The object behind this element, or null when it is not an object. */
internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonObject.stringOrNull(key: String): String? = this[key].asString()

internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.trim()?.toIntOrNull()

internal fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
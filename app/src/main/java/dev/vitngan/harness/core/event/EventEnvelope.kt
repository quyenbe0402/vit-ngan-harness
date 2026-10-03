package dev.vitngan.harness.core.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A structured event.
 *
 * [payload] is deliberately untyped: events cross a trust boundary and are
 * therefore treated as untrusted input (S7). The map is data, never authority.
 */
@Serializable
data class EventEnvelope(
    val id: String,
    val type: String,
    val timestampMillis: Long,
    val source: String,
    val payload: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "EventEnvelope.id must not be blank" }
        require(type.isNotBlank()) { "EventEnvelope.type must not be blank" }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        internal val json: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun fromJson(text: String): EventEnvelope = json.decodeFromString(serializer(), text)
    }
}
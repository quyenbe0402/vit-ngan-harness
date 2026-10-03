package dev.vitngan.harness.core.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One frame of the Hermes bridge protocol.
 *
 * Both directions are untrusted. [content] is model- or file-originated text
 * and never grants capability (S8).
 */
@Serializable
data class BridgeMessage(
    val id: String,
    val type: String,
    val content: String,
    val timestampMillis: Long,
    val inReplyTo: String? = null,
) {
    init {
        require(id.isNotBlank()) { "BridgeMessage.id must not be blank" }
        require(type.isNotBlank()) { "BridgeMessage.type must not be blank" }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        internal val json: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun fromJson(text: String): BridgeMessage = json.decodeFromString(serializer(), text)
    }
}
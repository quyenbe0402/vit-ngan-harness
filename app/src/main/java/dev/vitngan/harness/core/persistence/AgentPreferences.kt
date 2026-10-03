package dev.vitngan.harness.core.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Small durable preferences for the agent harness.
 *
 * Backed by a [PreferenceStore] seam rather than DataStore directly, so the
 * logic is testable on the JVM. On a device the seam is implemented over
 * `androidx.datastore.preferences`.
 *
 * Stored values are **untrusted** (S7): a preference is data, never a
 * capability grant. Nothing here can widen what policy permits.
 */
data class AgentPreferences(
    val lastWorkspaceId: String? = null,
    val eventLogCapacity: Int = 512,
    val contextMaxBytes: Int = 256 * 1024,
    val telemetryEnabled: Boolean = false,
) {
    init {
        require(eventLogCapacity > 0) { "eventLogCapacity must be positive" }
        require(contextMaxBytes > 0) { "contextMaxBytes must be positive" }
    }
}

/** Backing seam for [AgentPreferences]. */
interface PreferenceStore {
    fun read(): String
    fun write(serialised: String)
}

/** In-memory store for tests and pre-device runs. */
class InMemoryPreferenceStore(initial: String = "") : PreferenceStore {
    private var value = initial
    override fun read(): String = value
    override fun write(serialised: String) {
        value = serialised
    }
}

@Serializable
private data class PrefsRecord(
    val lastWorkspaceId: String? = null,
    val eventLogCapacity: Int = 512,
    val contextMaxBytes: Int = 262144,
    val telemetryEnabled: Boolean = false,
)

/**
 * Reads and writes [AgentPreferences].
 *
 * Decoding is total. Corrupt or absent stored data falls back to defaults
 * rather than throwing, because preferences must never be able to stop the
 * harness from starting.
 */
class AgentPreferencesStore(
    private val store: PreferenceStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun load(): AgentPreferences {
        val text = store.read()
        if (text.isBlank()) return AgentPreferences()
        return try {
            val record = json.decodeFromString(PrefsRecord.serializer(), text)
            AgentPreferences(
                lastWorkspaceId = record.lastWorkspaceId,
                eventLogCapacity = record.eventLogCapacity.coerceAtLeast(1),
                contextMaxBytes = record.contextMaxBytes.coerceAtLeast(1),
                telemetryEnabled = record.telemetryEnabled,
            )
        } catch (t: Throwable) {
            AgentPreferences()
        }
    }

    fun save(preferences: AgentPreferences) {
        store.write(
            json.encodeToString(
                PrefsRecord.serializer(),
                PrefsRecord(
                    lastWorkspaceId = preferences.lastWorkspaceId,
                    eventLogCapacity = preferences.eventLogCapacity,
                    contextMaxBytes = preferences.contextMaxBytes,
                    telemetryEnabled = preferences.telemetryEnabled,
                ),
            ),
        )
    }
}
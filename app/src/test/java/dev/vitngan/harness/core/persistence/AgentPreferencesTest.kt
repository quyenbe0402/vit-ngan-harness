package dev.vitngan.harness.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DataStore-backed preference round trips and corruption handling. */
class AgentPreferencesTest {

    @Test
    fun `defaults are returned when nothing is stored`() {
        val store = InMemoryPreferenceStore()
        val prefs = AgentPreferencesStore(store).load()
        assertEquals(512, prefs.eventLogCapacity)
        assertEquals(null, prefs.lastWorkspaceId)
    }

    @Test
    fun `preferences survive a round trip`() {
        val backing = InMemoryPreferenceStore()
        val store = AgentPreferencesStore(backing)
        store.save(
            AgentPreferences(
                lastWorkspaceId = "ws-7",
                eventLogCapacity = 64,
                contextMaxBytes = 1024,
                telemetryEnabled = true,
            ),
        )
        val loaded = AgentPreferencesStore(backing).load()
        assertEquals("ws-7", loaded.lastWorkspaceId)
        assertEquals(64, loaded.eventLogCapacity)
        assertEquals(1024, loaded.contextMaxBytes)
        assertTrue(loaded.telemetryEnabled)
    }

    @Test
    fun `corrupt stored preferences fall back to defaults`() {
        val backing = InMemoryPreferenceStore("<<<not json>>>")
        val prefs = AgentPreferencesStore(backing).load()
        assertEquals(512, prefs.eventLogCapacity)
    }

    @Test
    fun `non-positive capacity is coerced rather than propagated`() {
        val backing = InMemoryPreferenceStore(
            """{"lastWorkspaceId":null,"eventLogCapacity":0,"contextMaxBytes":-5,"telemetryEnabled":false}""",
        )
        val prefs = AgentPreferencesStore(backing).load()
        assertTrue(prefs.eventLogCapacity >= 1)
        assertTrue(prefs.contextMaxBytes >= 1)
    }

    @Test
    fun `invalid capacity is rejected at construction`() {
        var threw = false
        try {
            AgentPreferences(eventLogCapacity = 0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `unknown fields in stored preferences are ignored`() {
        val backing = InMemoryPreferenceStore(
            """{"lastWorkspaceId":"ws","eventLogCapacity":10,"contextMaxBytes":20,"telemetryEnabled":false,"future":1}""",
        )
        val prefs = AgentPreferencesStore(backing).load()
        assertEquals(10, prefs.eventLogCapacity)
    }
}
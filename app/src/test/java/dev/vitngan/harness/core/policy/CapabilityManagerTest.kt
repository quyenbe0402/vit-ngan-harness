package dev.vitngan.harness.core.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityManagerTest {

    private val pkg = "dev.vitngan.harness"

    private fun manager(policy: AppPolicy) = CapabilityManager(TrustedPolicyEngine({ policy }))

    private fun allowAll() = AppPolicy(pkg, Capability.entries.toSet())

    @Test
    fun `allowed request is not recorded as denied`() {
        val cm = manager(allowAll())
        val decision = cm.request(pkg, Capability.WORKSPACE_READ, "a.txt")
        assertTrue(decision.isAllowed)
        assertFalse(cm.isDenied(Capability.WORKSPACE_READ))
        assertTrue(cm.wasRequested(Capability.WORKSPACE_READ))
    }

    @Test
    fun `denied request is recorded`() {
        val cm = manager(AppPolicy.readOnly(pkg))
        cm.request(pkg, Capability.WORKSPACE_WRITE, "a.txt")
        assertTrue(cm.isDenied(Capability.WORKSPACE_WRITE))
        assertTrue(Capability.WORKSPACE_WRITE in cm.deniedCapabilities())
    }

    @Test
    fun `isGranted defers to the trusted engine`() {
        val cm = manager(AppPolicy.readOnly(pkg))
        assertTrue(cm.isGranted(pkg, Capability.WORKSPACE_READ))
        assertFalse(cm.isGranted(pkg, Capability.WORKSPACE_DELETE))
    }

    @Test
    fun `a later allow clears the denied flag`() {
        val cm = manager(allowAll())
        cm.request(pkg, Capability.PROCESS_EXECUTE)
        assertFalse(cm.isDenied(Capability.PROCESS_EXECUTE))
    }

    @Test
    fun `reset clears request history`() {
        val cm = manager(allowAll())
        cm.request(pkg, Capability.WORKSPACE_READ)
        cm.reset()
        assertFalse(cm.wasRequested(Capability.WORKSPACE_READ))
    }

    @Test
    fun `unknown package is denied through the manager too`() {
        val cm = manager(allowAll())
        val decision = cm.request("com.other.app", Capability.WORKSPACE_READ)
        assertFalse(decision.isAllowed)
        assertTrue(cm.isDenied(Capability.WORKSPACE_READ))
    }
}
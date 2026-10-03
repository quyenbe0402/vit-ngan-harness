package dev.vitngan.harness.core.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedPolicyEngineTest {

    private val pkg = "dev.vitngan.harness"

    private fun engine(policy: AppPolicy?) = TrustedPolicyEngine({ policy })

    private fun allowAll() = AppPolicy(
        packageName = pkg,
        enabledCapabilities = Capability.entries.toSet(),
    )

    @Test
    fun `unknown package is denied as not declared`() {
        val decision = engine(null).evaluate(pkg, Capability.WORKSPACE_READ)
        assertTrue(decision is PolicyDecision.Deny)
        assertEquals(DenyCode.NOT_DECLARED, (decision as PolicyDecision.Deny).code)
    }

    @Test
    fun `disabled capability is denied`() {
        val decision = engine(AppPolicy.readOnly(pkg)).evaluate(pkg, Capability.WORKSPACE_WRITE)
        assertTrue(decision is PolicyDecision.Deny)
        assertEquals(DenyCode.DISABLED, (decision as PolicyDecision.Deny).code)
    }

    @Test
    fun `enabled capability is allowed`() {
        val decision = engine(allowAll()).evaluate(pkg, Capability.WORKSPACE_READ)
        assertTrue(decision.isAllowed)
    }

    @Test
    fun `deny rule vetoes an otherwise enabled capability`() {
        val policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            denyRules = listOf(
                DenyRule("no-secrets", Capability.WORKSPACE_READ, "/secrets", "secret material"),
            ),
        )
        val decision = engine(policy).evaluate(pkg, Capability.WORKSPACE_READ, "secrets/a.txt")
        assertTrue(decision is PolicyDecision.Deny)
        assertEquals(DenyCode.RULE_DENIED, (decision as PolicyDecision.Deny).code)
    }

    @Test
    fun `deny rule does not fire outside its path prefix`() {
        val policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            denyRules = listOf(DenyRule("no-secrets", Capability.WORKSPACE_READ, "/secrets", "nope")),
        )
        assertTrue(engine(policy).evaluate(pkg, Capability.WORKSPACE_READ, "docs/a.txt").isAllowed)
    }

    @Test
    fun `deny rule matching is boundary aware`() {
        val rule = DenyRule("r", Capability.WORKSPACE_READ, "/ws/data", "no")
        assertTrue(rule.matches(Capability.WORKSPACE_READ, "/ws/data/x.txt"))
        // Prefix collision: "/ws/database" must NOT match prefix "/ws/data".
        assertTrue(!rule.matches(Capability.WORKSPACE_READ, "/ws/database/x.txt"))
    }

    @Test
    fun `deny rule with null capability matches all capabilities`() {
        val rule = DenyRule("all", null, "/etc", "no")
        assertTrue(rule.matches(Capability.PROCESS_EXECUTE, "/etc/passwd"))
        assertTrue(rule.matches(Capability.WORKSPACE_READ, "/etc/passwd"))
    }

    @Test
    fun `user auth is required before the capability is allowed`() {
        val policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            userAuthRequired = setOf(Capability.PROCESS_EXECUTE),
        )
        val e = engine(policy)

        val first = e.evaluate(pkg, Capability.PROCESS_EXECUTE, nowMillis = 1000)
        assertTrue(first is PolicyDecision.Deny)
        assertEquals(DenyCode.USER_AUTH_REQUIRED, (first as PolicyDecision.Deny).code)

        e.grantUserAuth(UserAuth(pkg, Capability.PROCESS_EXECUTE, grantedAtMillis = 900))
        val second = e.evaluate(pkg, Capability.PROCESS_EXECUTE, nowMillis = 1000)
        assertTrue("grant should permit the request", second.isAllowed)

        // Single-use: the grant is consumed, so a repeat is denied again.
        val third = e.evaluate(pkg, Capability.PROCESS_EXECUTE, nowMillis = 1000)
        assertTrue(third is PolicyDecision.Deny)
    }

    @Test
    fun `revoke removes stored grants`() {
        val policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            userAuthRequired = setOf(Capability.PROCESS_EXECUTE),
        )
        val e = engine(policy)
        e.grantUserAuth(UserAuth(pkg, Capability.PROCESS_EXECUTE, grantedAtMillis = 0))
        e.revoke(pkg)
        val decision = e.evaluate(pkg, Capability.PROCESS_EXECUTE, nowMillis = 10)
        assertTrue(decision is PolicyDecision.Deny)
    }

    @Test
    fun `deny rule outranks a user grant`() {
        val policy = AppPolicy(
            packageName = pkg,
            enabledCapabilities = Capability.entries.toSet(),
            userAuthRequired = setOf(Capability.PROCESS_EXECUTE),
            denyRules = listOf(DenyRule("never", Capability.PROCESS_EXECUTE, null, "forbidden")),
        )
        val e = engine(policy)
        e.grantUserAuth(UserAuth(pkg, Capability.PROCESS_EXECUTE, grantedAtMillis = 0))
        val decision = e.evaluate(pkg, Capability.PROCESS_EXECUTE, nowMillis = 10)
        assertTrue(decision is PolicyDecision.Deny)
        assertEquals(DenyCode.RULE_DENIED, (decision as PolicyDecision.Deny).code)
    }
}
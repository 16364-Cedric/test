package fr.dati.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdminPasswordTest {
    @Test
    fun `verifies the right password only`() {
        val stored = AdminPassword.hash("sécurité-2026", iterations = 1_000)
        assertTrue(AdminPassword.verify("sécurité-2026", stored))
        assertFalse(AdminPassword.verify("securite-2026", stored))
        assertFalse(stored.contains("sécurité"), "jamais en clair")
    }

    @Test
    fun `each hash uses its own salt`() {
        assertNotEquals(AdminPassword.hash("1234", 1_000), AdminPassword.hash("1234", 1_000))
    }

    @Test
    fun `too short passwords and corrupted hashes are refused`() {
        assertFailsWith<IllegalArgumentException> { AdminPassword.hash("123") }
        assertFalse(AdminPassword.verify("1234", ""))
        assertFalse(AdminPassword.verify("1234", "pbkdf2" + "$" + "x" + "$" + "abc" + "$" + "def"))
    }

    @Test
    fun `lockout after five failures, growing`() {
        val limiter = AttemptLimiter(maxAttempts = 5, baseLockoutMs = 60_000)
        repeat(4) { limiter.onFailure(0) }
        assertEquals(0, limiter.remainingLockoutMs(0))
        limiter.onFailure(0)
        assertEquals(60_000, limiter.remainingLockoutMs(0))
        assertEquals(0, limiter.remainingLockoutMs(60_000))
        repeat(5) { limiter.onFailure(60_000) }
        assertEquals(120_000, limiter.remainingLockoutMs(60_000))
        limiter.onSuccess()
        assertEquals(0, limiter.remainingLockoutMs(60_000))
    }
}

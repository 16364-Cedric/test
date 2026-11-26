package fr.dati.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Mot de passe administrateur, conservé sous forme d'empreinte PBKDF2 salée
 * (`pbkdf2$itérations$sel$empreinte`) : il n'est jamais stocké en clair.
 */
object AdminPassword {
    const val MIN_LENGTH = 4
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256
    private const val SEPARATOR = "$"

    fun hash(password: String, iterations: Int = ITERATIONS): String {
        require(password.length >= MIN_LENGTH) { "Le mot de passe doit faire au moins $MIN_LENGTH caractères" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        return listOf(
            "pbkdf2",
            iterations.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(derive(password, salt, iterations)),
        ).joinToString(SEPARATOR)
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** Limite les essais de mot de passe : après [maxAttempts] échecs, blocage croissant. */
class AttemptLimiter(
    private val maxAttempts: Int = 5,
    private val baseLockoutMs: Long = 60_000,
) {
    private var failures = 0
    private var lockedUntilMs = 0L

    /** Temps restant avant de pouvoir réessayer, 0 si un essai est permis. */
    fun remainingLockoutMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceAtLeast(0)

    fun onFailure(nowMs: Long) {
        failures++
        if (failures % maxAttempts == 0) {
            // 1 min, puis 2, 4… à chaque nouvelle série d'échecs (plafonné à 1 h).
            val series = failures / maxAttempts - 1
            lockedUntilMs = nowMs + (baseLockoutMs shl series.coerceAtMost(6)).coerceAtMost(MAX_LOCKOUT_MS)
        }
    }

    fun onSuccess() {
        failures = 0
        lockedUntilMs = 0
    }

    private companion object {
        const val MAX_LOCKOUT_MS = 3_600_000L
    }
}

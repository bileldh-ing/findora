package com.findora.app.core.data.auth

import com.findora.app.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/** A stored password: the encoded hash (algorithm, iterations and digest) and its Base64 salt. */
data class HashedPassword(
    val encoded: String,
    val salt: String,
)

/**
 * PBKDF2WithHmacSHA256 with a random 16-byte salt per password and at least [MIN_ITERATIONS] iterations.
 * The iteration count is stored inside the hash ("pbkdf2_sha256$120000$..."), so it can be raised later
 * without invalidating existing accounts. Hashing runs on [dispatcher] because it is deliberately slow.
 */
class PasswordHasher(
    private val iterations: Int,
    private val random: SecureRandom,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        random: SecureRandom,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(MIN_ITERATIONS, random, dispatcher)

    init {
        require(iterations > 0) { "iterations must be positive" }
    }

    suspend fun hash(password: String): HashedPassword =
        withContext(dispatcher) {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val digest = derive(password, salt, iterations)
            HashedPassword(
                encoded = "$ALGORITHM_ID$SEPARATOR$iterations$SEPARATOR${encoder.encodeToString(digest)}",
                salt = encoder.encodeToString(salt),
            )
        }

    /** Constant-time verification; malformed stored values never match. */
    suspend fun verify(
        password: String,
        stored: HashedPassword,
    ): Boolean =
        withContext(dispatcher) {
            val parsed = parse(stored) ?: return@withContext false
            MessageDigest.isEqual(parsed.digest, derive(password, parsed.salt, parsed.iterations))
        }

    /** Spends the same work as a real verification; used for unknown e-mails so timing does not reveal them. */
    suspend fun burn(password: String) {
        withContext(dispatcher) { derive(password, ByteArray(SALT_BYTES), iterations) }
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        rounds: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private class ParsedHash(
        val iterations: Int,
        val digest: ByteArray,
        val salt: ByteArray,
    )

    private fun parse(stored: HashedPassword): ParsedHash? {
        val parts = stored.encoded.split(SEPARATOR)
        if (parts.size != PARTS || parts[0] != ALGORITHM_ID) return null
        val rounds = parts[1].toIntOrNull()?.takeIf { it > 0 }
        val digest = decodeOrNull(parts[2])
        val salt = decodeOrNull(stored.salt)
        return if (rounds != null && digest != null && salt != null) ParsedHash(rounds, digest, salt) else null
    }

    private fun decodeOrNull(value: String): ByteArray? =
        try {
            decoder.decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val MIN_ITERATIONS = 120_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ALGORITHM_ID = "pbkdf2_sha256"
        private const val SEPARATOR = "$"
        private const val PARTS = 3
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()
    }
}

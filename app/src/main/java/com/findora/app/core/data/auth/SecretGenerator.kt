package com.findora.app.core.data.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/** Random secrets: opaque session tokens, six-digit reset codes and account ids. */
interface SecretGenerator {
    fun sessionToken(): String

    fun resetCode(): String

    fun userId(): String
}

class SecureRandomSecretGenerator
    @Inject
    constructor(
        private val random: SecureRandom,
    ) : SecretGenerator {
        override fun sessionToken(): String {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        override fun resetCode(): String = random.nextInt(RESET_CODE_BOUND).toString().padStart(RESET_CODE_DIGITS, '0')

        override fun userId(): String = UUID.randomUUID().toString()

        private companion object {
            const val TOKEN_BYTES = 32
            const val RESET_CODE_DIGITS = 6
            const val RESET_CODE_BOUND = 1_000_000
        }
    }

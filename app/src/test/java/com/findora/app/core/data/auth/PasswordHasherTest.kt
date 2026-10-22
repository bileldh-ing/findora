package com.findora.app.core.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class PasswordHasherTest {
    private fun hasher(iterations: Int = PasswordHasher.MIN_ITERATIONS) =
        PasswordHasher(iterations, SecureRandom(), Dispatchers.Unconfined)

    @Test
    fun productionDefaultsMeetTheSecurityRules() =
        runTest {
            val hasher = PasswordHasher(SecureRandom(), Dispatchers.Unconfined)
            val hashed = hasher.hash("Demo1234")
            val parts = hashed.encoded.split("$")
            assertEquals("pbkdf2_sha256", parts[0])
            assertTrue(parts[1].toInt() >= 120_000)
            assertEquals(16, Base64.getDecoder().decode(hashed.salt).size)
            assertEquals(32, Base64.getDecoder().decode(parts[2]).size)
            assertTrue(hasher.verify("Demo1234", hashed))
        }

    @Test
    fun hashIsNeverThePlainPasswordAndSaltsDiffer() =
        runTest {
            val hasher = hasher(iterations = 1_000)
            val first = hasher.hash("Secret123")
            val second = hasher.hash("Secret123")
            assertFalse(first.encoded.contains("Secret123"))
            assertNotEquals(first.salt, second.salt)
            assertNotEquals(first.encoded, second.encoded)
        }

    @Test
    fun verifyRejectsWrongPasswordsAndCase() =
        runTest {
            val hasher = hasher(iterations = 1_000)
            val hashed = hasher.hash("Secret123")
            assertTrue(hasher.verify("Secret123", hashed))
            assertFalse(hasher.verify("secret123", hashed))
            assertFalse(hasher.verify("Secret1234", hashed))
            assertFalse(hasher.verify("", hashed))
        }

    @Test
    fun storedIterationCountIsHonouredSoItCanBeRaisedLater() =
        runTest {
            val old = hasher(iterations = 1_000).hash("Secret123")
            assertTrue(hasher(iterations = 2_000).verify("Secret123", old))
        }

    @Test
    fun malformedStoredValuesNeverMatch() =
        runTest {
            val hasher = hasher(iterations = 1_000)
            val good = hasher.hash("Secret123")
            listOf(
                good.copy(encoded = "plain"),
                good.copy(encoded = good.encoded.replace("pbkdf2_sha256", "md5")),
                good.copy(encoded = "pbkdf2_sha256\$abc\$xyz"),
                good.copy(encoded = "pbkdf2_sha256\$0\$" + good.encoded.substringAfterLast('$')),
                good.copy(salt = "%%%not-base64%%%"),
            ).forEach { assertFalse(it.toString(), hasher.verify("Secret123", it)) }
        }

    @Test(expected = IllegalArgumentException::class)
    fun iterationsMustBePositive() {
        hasher(iterations = 0)
    }
}

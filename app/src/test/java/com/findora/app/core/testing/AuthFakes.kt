package com.findora.app.core.testing

import com.findora.app.core.data.auth.AesGcm
import com.findora.app.core.data.auth.SecretGenerator
import com.findora.app.core.data.auth.SessionStore
import com.findora.app.core.data.auth.StoredSession
import com.findora.app.core.data.auth.TokenCipher
import com.findora.app.core.security.BiometricAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import javax.crypto.spec.SecretKeySpec

/** Software AES-GCM with a fixed key: the JVM stand-in for the Android Keystore cipher. */
class FakeTokenCipher(
    keyByte: Byte = 7,
) : TokenCipher {
    private val key = SecretKeySpec(ByteArray(KEY_BYTES) { keyByte }, "AES")

    override fun encrypt(plaintext: ByteArray): ByteArray = AesGcm.encrypt(key, plaintext)

    override fun decrypt(ciphertext: ByteArray): ByteArray = AesGcm.decrypt(key, ciphertext)

    private companion object {
        const val KEY_BYTES = 32
    }
}

class InMemorySessionStore : SessionStore {
    var stored: StoredSession? = null

    override suspend fun read(): StoredSession? = stored

    override suspend fun write(session: StoredSession) {
        stored = session
    }

    override suspend fun clear() {
        stored = null
    }
}

/** Deterministic secrets: predictable ids, tokens and reset codes. */
class FakeSecretGenerator(
    var nextResetCode: String = "123456",
) : SecretGenerator {
    private var counter = 0

    override fun sessionToken(): String = "token-${++counter}"

    override fun resetCode(): String = nextResetCode

    override fun userId(): String = "user-${++counter}"
}

class FakeBiometricAvailability(
    var available: Boolean = true,
) : BiometricAvailability {
    override fun canAuthenticate(): Boolean = available
}

/** Replaces Dispatchers.Main for ViewModel tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

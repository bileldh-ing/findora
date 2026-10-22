package com.findora.app.core.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.findora.app.core.testing.FakeTokenCipher
import com.findora.app.core.testing.testJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedSessionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val session = StoredSession("user-42", "secret-token", 1_000, 2_000)

    private fun TestScope.store(
        file: File,
        cipher: TokenCipher = FakeTokenCipher(),
        scope: CoroutineScope = backgroundScope,
    ) = EncryptedSessionStore(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        cipher,
        testJson,
    )

    @Test
    fun roundTripsAndNeverWritesTheTokenInClear() =
        runTest(UnconfinedTestDispatcher()) {
            val file = File(tmp.root, "session.preferences_pb")
            val store = store(file)
            store.write(session)
            assertEquals(session, store.read())
            val onDisk = file.readBytes().decodeToString()
            assertFalse(onDisk.contains("secret-token"))
            assertFalse(onDisk.contains("user-42"))
        }

    @Test
    fun clearRemovesTheSession() =
        runTest(UnconfinedTestDispatcher()) {
            val store = store(File(tmp.root, "a.preferences_pb"))
            store.write(session)
            store.clear()
            assertNull(store.read())
        }

    @Test
    fun undecryptableDataIsTreatedAsSignedOutAndDeleted() =
        runTest(UnconfinedTestDispatcher()) {
            val file = File(tmp.root, "b.preferences_pb")
            val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            store(file, FakeTokenCipher(keyByte = 1), firstScope).write(session)
            firstScope.cancel()
            // A different key (for example a Keystore key lost after a device restore) cannot authenticate it.
            val other = store(File(tmp.root, "b.preferences_pb"), FakeTokenCipher(keyByte = 2))
            assertNull(other.read())
            assertNull(other.read())
        }
}

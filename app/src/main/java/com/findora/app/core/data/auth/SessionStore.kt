package com.findora.app.core.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** What is persisted for a remembered session. Only ever written encrypted. */
@Serializable
data class StoredSession(
    val userId: String,
    val token: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
)

interface SessionStore {
    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession)

    suspend fun clear()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionPreferences

/**
 * Remembered session in its own DataStore file, encrypted with [TokenCipher] (Android Keystore AES-GCM).
 * Unreadable data (corruption, a wiped Keystore key after a restore) is treated as "signed out".
 */
@Singleton
class EncryptedSessionStore
    @Inject
    constructor(
        @SessionPreferences private val dataStore: DataStore<Preferences>,
        private val cipher: TokenCipher,
        private val json: Json,
    ) : SessionStore {
        override suspend fun read(): StoredSession? {
            val encoded =
                try {
                    dataStore.data.first()[KEY]
                } catch (_: IOException) {
                    null
                } ?: return null
            return try {
                val plaintext = cipher.decrypt(Base64.getDecoder().decode(encoded))
                json.decodeFromString<StoredSession>(plaintext.decodeToString())
            } catch (_: GeneralSecurityException) {
                clear()
                null
            } catch (_: IllegalArgumentException) {
                // Base64 or JSON (SerializationException extends IllegalArgumentException) could not be decoded.
                clear()
                null
            }
        }

        override suspend fun write(session: StoredSession) {
            val plaintext = json.encodeToString(StoredSession.serializer(), session).encodeToByteArray()
            val ciphertext = cipher.encrypt(plaintext)
            dataStore.edit { it[KEY] = Base64.getEncoder().encodeToString(ciphertext) }
        }

        override suspend fun clear() {
            dataStore.edit { it.remove(KEY) }
        }

        private companion object {
            val KEY = stringPreferencesKey("session_v1")
        }
    }

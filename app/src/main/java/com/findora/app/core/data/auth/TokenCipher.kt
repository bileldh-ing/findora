package com.findora.app.core.data.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the persisted session. Abstracted so JVM tests (which have no Android Keystore) use a software key.
 * Implementations must throw [GeneralSecurityException] when a ciphertext cannot be authenticated.
 */
interface TokenCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

/** AES-256-GCM helper shared by the Keystore cipher and the test cipher: output is IV (12 bytes) + ciphertext. */
object AesGcm {
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128

    fun encrypt(
        key: SecretKey,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected GCM IV size ${iv.size}" }
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(
        key: SecretKey,
        payload: ByteArray,
    ): ByteArray {
        if (payload.size <= IV_BYTES) throw GeneralSecurityException("Ciphertext too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        return cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
    }
}

/** AES-GCM with a non-exportable key generated inside the Android Keystore. */
@Singleton
class AndroidKeystoreTokenCipher
    @Inject
    constructor() : TokenCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = AesGcm.encrypt(key(), plaintext)

        override fun decrypt(ciphertext: ByteArray): ByteArray = AesGcm.decrypt(key(), ciphertext)

        @Synchronized
        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            return generator.generateKey()
        }

        private companion object {
            const val KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "findora_session_key"
            const val KEY_BITS = 256
        }
    }

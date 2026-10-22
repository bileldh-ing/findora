package com.findora.app.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.findora.app.core.data.auth.AndroidKeystoreTokenCipher
import com.findora.app.core.data.auth.EncryptedSessionStore
import com.findora.app.core.data.auth.LocalAuthRepository
import com.findora.app.core.data.auth.SecretGenerator
import com.findora.app.core.data.auth.SecureRandomSecretGenerator
import com.findora.app.core.data.auth.SessionPreferences
import com.findora.app.core.data.auth.SessionStore
import com.findora.app.core.data.auth.TokenCipher
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.dao.AccountDao
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.security.AndroidBiometricAvailability
import com.findora.app.core.security.BiometricAvailability
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    abstract fun bindAuthRepository(impl: LocalAuthRepository): AuthRepository

    @Binds
    abstract fun bindTokenCipher(impl: AndroidKeystoreTokenCipher): TokenCipher

    @Binds
    abstract fun bindSessionStore(impl: EncryptedSessionStore): SessionStore

    @Binds
    abstract fun bindSecretGenerator(impl: SecureRandomSecretGenerator): SecretGenerator

    @Binds
    abstract fun bindBiometricAvailability(impl: AndroidBiometricAvailability): BiometricAvailability
}

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    private const val SESSION_FILE = "session"

    @Provides
    @Singleton
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Provides
    fun provideAccountDao(db: FindoraDatabase): AccountDao = db.accountDao()

    @Provides
    @Singleton
    @SessionPreferences
    fun provideSessionDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(SESSION_FILE) },
        )
}

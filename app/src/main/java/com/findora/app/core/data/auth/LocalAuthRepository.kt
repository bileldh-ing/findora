package com.findora.app.core.data.auth

import android.database.sqlite.SQLiteConstraintException
import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.AccountDao
import com.findora.app.core.data.local.dao.UserDao
import com.findora.app.core.data.local.entity.LoginAttemptEntity
import com.findora.app.core.data.local.entity.UserEntity
import com.findora.app.core.domain.auth.CredentialValidator
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.PasswordResetTicket
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.domain.model.Session
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.AuthRepository.Companion.DEMO_EMAIL
import com.findora.app.core.domain.repository.AuthRepository.Companion.DEMO_NAME
import com.findora.app.core.domain.repository.AuthRepository.Companion.DEMO_PASSWORD
import com.findora.app.core.domain.repository.AuthRepository.Companion.GUEST_USER_ID
import com.findora.app.core.domain.repository.AuthRepository.Companion.LOCKOUT_SECONDS
import com.findora.app.core.domain.repository.AuthRepository.Companion.MAX_FAILED_ATTEMPTS
import com.findora.app.core.domain.repository.AuthRepository.Companion.REMEMBER_ME_DAYS
import com.findora.app.core.domain.repository.AuthRepository.Companion.RESET_CODE_MINUTES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local, offline authentication: accounts in Room (PBKDF2 hashes only), the remembered session encrypted with
 * the Android Keystore, and sign-in throttling persisted per e-mail. All writes are serialised by a mutex.
 */
@Suppress("TooManyFunctions") // One cohesive boundary; every public function is an AuthRepository operation.
@Singleton
class LocalAuthRepository
    @Inject
    constructor(
        private val userDao: UserDao,
        private val accountDao: AccountDao,
        private val hasher: PasswordHasher,
        private val sessionStore: SessionStore,
        private val clock: AppClock,
        private val secrets: SecretGenerator,
    ) : AuthRepository {
        private val mutex = Mutex()
        private val sessionState = MutableStateFlow<Session?>(null)
        private val resetTickets = mutableMapOf<String, PasswordResetTicket>()

        override val session: StateFlow<Session?> = sessionState.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeCurrentUser(): Flow<UserAccount?> =
            sessionState
                .flatMapLatest { current ->
                    if (current == null || current.isGuest) {
                        flowOf(null)
                    } else {
                        userDao.observeById(current.userId).map { it?.toAccount() }
                    }
                }.distinctUntilChanged()

        override suspend fun restoreSession(): Session? =
            mutex.withLock {
                val now = clock.now()
                sessionState.value?.takeIf { it.isValidAt(now) }?.let { return@withLock it }
                val stored = sessionStore.read() ?: return@withLock null
                val expiresAt = Instant.ofEpochMilli(stored.expiresAtMillis)
                val userExists = userDao.findById(stored.userId) != null
                if (!now.isBefore(expiresAt) || !userExists) {
                    sessionStore.clear()
                    sessionState.value = null
                    return@withLock null
                }
                Session(
                    userId = stored.userId,
                    isGuest = false,
                    issuedAt = Instant.ofEpochMilli(stored.issuedAtMillis),
                    expiresAt = expiresAt,
                ).also { sessionState.value = it }
            }

        override suspend fun signUp(
            displayName: String,
            email: String,
            password: String,
            rememberMe: Boolean,
        ): AuthResult<UserAccount> {
            val invalid =
                AuthError.InvalidInput(
                    name = CredentialValidator.validateName(displayName),
                    email = CredentialValidator.validateEmail(email),
                    password = CredentialValidator.unmetPasswordRules(password),
                )
            if (invalid != AuthError.InvalidInput()) return AuthResult.Failure(invalid)
            return mutex.withLock {
                val user = createUser(displayName.trim(), CredentialValidator.normalizeEmail(email), password)
                if (user == null) {
                    AuthResult.Failure(AuthError.EmailTaken)
                } else {
                    startSession(user.id, rememberMe)
                    AuthResult.Success(user.toAccount())
                }
            }
        }

        override suspend fun login(
            email: String,
            password: String,
            rememberMe: Boolean,
        ): AuthResult<UserAccount> {
            val normalized = CredentialValidator.normalizeEmail(email)
            val invalid =
                AuthError.InvalidInput(
                    email = CredentialValidator.validateEmail(normalized),
                    password = if (password.isEmpty()) setOf(PasswordRule.MinLength) else emptySet(),
                )
            if (invalid != AuthError.InvalidInput()) return AuthResult.Failure(invalid)
            if (normalized == DEMO_EMAIL) ensureDemoAccount()
            return mutex.withLock { verifyAndSignIn(normalized, password, rememberMe) }
        }

        override suspend fun loginWithDemoAccount(): AuthResult<UserAccount> =
            login(DEMO_EMAIL, DEMO_PASSWORD, rememberMe = true)

        override suspend fun continueAsGuest(): Session =
            mutex.withLock {
                // Each guest visit starts clean; guest data never mixes with an account's data.
                accountDao.deleteGuestData(GUEST_USER_ID)
                sessionStore.clear()
                Session(GUEST_USER_ID, isGuest = true, issuedAt = clock.now(), expiresAt = null)
                    .also { sessionState.value = it }
            }

        override suspend fun requestPasswordReset(email: String): AuthResult<PasswordResetTicket> {
            val emailError = CredentialValidator.validateEmail(email)
            if (emailError != null) return AuthResult.Failure(AuthError.InvalidInput(email = emailError))
            val normalized = CredentialValidator.normalizeEmail(email)
            if (normalized == DEMO_EMAIL) ensureDemoAccount()
            return mutex.withLock {
                if (userDao.findByEmail(normalized) == null) {
                    return@withLock AuthResult.Failure(AuthError.AccountNotFound)
                }
                val ticket =
                    PasswordResetTicket(
                        email = normalized,
                        code = secrets.resetCode(),
                        expiresAt = clock.now().plus(Duration.ofMinutes(RESET_CODE_MINUTES)),
                    )
                resetTickets[normalized] = ticket
                AuthResult.Success(ticket)
            }
        }

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ): AuthResult<Unit> {
            val rules = CredentialValidator.unmetPasswordRules(newPassword)
            if (rules.isNotEmpty()) return AuthResult.Failure(AuthError.InvalidInput(password = rules))
            val normalized = CredentialValidator.normalizeEmail(email)
            return mutex.withLock {
                val ticket = resetTickets[normalized]
                val error =
                    when {
                        ticket == null -> AuthError.InvalidResetCode
                        !clock.now().isBefore(ticket.expiresAt) -> AuthError.ResetCodeExpired
                        !constantTimeEquals(ticket.code, code.trim()) -> AuthError.InvalidResetCode
                        else -> null
                    }
                if (error == AuthError.ResetCodeExpired) resetTickets.remove(normalized)
                val user = userDao.findByEmail(normalized)
                when {
                    error != null -> AuthResult.Failure(error)
                    user == null -> AuthResult.Failure(AuthError.AccountNotFound)
                    else -> {
                        val hashed = hasher.hash(newPassword)
                        userDao.updatePassword(user.id, hashed.encoded, hashed.salt)
                        resetTickets.remove(normalized)
                        accountDao.clearLoginAttempts(normalized)
                        AuthResult.Success(Unit)
                    }
                }
            }
        }

        override suspend fun logout() {
            mutex.withLock { endSession() }
        }

        override suspend fun deleteAccount(): AuthResult<Unit> =
            mutex.withLock {
                val current = sessionState.value
                val user = current?.takeUnless { it.isGuest }?.let { userDao.findById(it.userId) }
                if (user == null) return@withLock AuthResult.Failure(AuthError.NotSignedIn)
                accountDao.deleteAccount(user.id, user.email)
                resetTickets.remove(user.email)
                endSession()
                AuthResult.Success(Unit)
            }

        /** Caller holds [mutex]. */
        private suspend fun verifyAndSignIn(
            email: String,
            password: String,
            rememberMe: Boolean,
        ): AuthResult<UserAccount> {
            val now = clock.now()
            val attempts = accountDao.loginAttempts(email)
            val lockedUntil = attempts?.lockedUntilMillis?.let(Instant::ofEpochMilli)
            if (lockedUntil != null && now.isBefore(lockedUntil)) {
                return AuthResult.Failure(AuthError.LockedOut(lockedUntil))
            }
            val user = userDao.findByEmail(email)
            val verified =
                if (user == null) {
                    hasher.burn(password)
                    false
                } else {
                    hasher.verify(password, HashedPassword(user.passwordHash, user.passwordSalt))
                }
            return if (!verified || user == null) {
                AuthResult.Failure(registerFailure(email, attempts, now))
            } else {
                accountDao.clearLoginAttempts(email)
                startSession(user.id, rememberMe)
                AuthResult.Success(user.toAccount())
            }
        }

        /** Counts a failed attempt; the fifth in a row locks the e-mail for [LOCKOUT_SECONDS]. */
        private suspend fun registerFailure(
            email: String,
            previous: LoginAttemptEntity?,
            now: Instant,
        ): AuthError {
            // A lockout that has expired starts a fresh series of attempts.
            val base = if (previous?.lockedUntilMillis != null) 0 else previous?.failedCount ?: 0
            val failed = base + 1
            return if (failed >= MAX_FAILED_ATTEMPTS) {
                val until = now.plusSeconds(LOCKOUT_SECONDS)
                accountDao.upsertLoginAttempts(LoginAttemptEntity(email, failed, until.toEpochMilli()))
                AuthError.LockedOut(until)
            } else {
                accountDao.upsertLoginAttempts(LoginAttemptEntity(email, failed, null))
                AuthError.InvalidCredentials(attemptsRemaining = MAX_FAILED_ATTEMPTS - failed)
            }
        }

        /** Caller holds [mutex]. Returns null when the e-mail is already registered. */
        private suspend fun createUser(
            displayName: String,
            email: String,
            password: String,
        ): UserEntity? {
            if (userDao.findByEmail(email) != null) return null
            val hashed = hasher.hash(password)
            val user =
                UserEntity(
                    id = secrets.userId(),
                    email = email,
                    displayName = displayName,
                    passwordHash = hashed.encoded,
                    passwordSalt = hashed.salt,
                    createdAtMillis = clock.now().toEpochMilli(),
                )
            return try {
                userDao.insert(user)
                user
            } catch (_: SQLiteConstraintException) {
                // Lost a race with another insert of the same e-mail (the unique index is the real guard).
                null
            }
        }

        private suspend fun ensureDemoAccount() {
            mutex.withLock {
                if (userDao.findByEmail(DEMO_EMAIL) == null) createUser(DEMO_NAME, DEMO_EMAIL, DEMO_PASSWORD)
            }
        }

        /** Caller holds [mutex]. Remembered sessions are persisted (encrypted) for 30 days; others live in memory. */
        private suspend fun startSession(
            userId: String,
            rememberMe: Boolean,
        ) {
            val now = clock.now()
            val session =
                if (rememberMe) {
                    val expiresAt = now.plus(Duration.ofDays(REMEMBER_ME_DAYS))
                    sessionStore.write(
                        StoredSession(userId, secrets.sessionToken(), now.toEpochMilli(), expiresAt.toEpochMilli()),
                    )
                    Session(userId, isGuest = false, issuedAt = now, expiresAt = expiresAt)
                } else {
                    sessionStore.clear()
                    Session(userId, isGuest = false, issuedAt = now, expiresAt = null)
                }
            sessionState.value = session
        }

        /** Caller holds [mutex]. */
        private suspend fun endSession() {
            if (sessionState.value?.isGuest == true) accountDao.deleteGuestData(GUEST_USER_ID)
            sessionStore.clear()
            sessionState.value = null
        }

        private fun constantTimeEquals(
            expected: String,
            actual: String,
        ): Boolean = MessageDigest.isEqual(expected.encodeToByteArray(), actual.encodeToByteArray())
    }

fun UserEntity.toAccount(): UserAccount = UserAccount(id = id, email = email, displayName = displayName)

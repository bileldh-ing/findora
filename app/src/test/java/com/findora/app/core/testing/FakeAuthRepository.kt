package com.findora.app.core.testing

import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.PasswordResetTicket
import com.findora.app.core.domain.model.Session
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Scriptable AuthRepository for ViewModel tests: set the next results, inspect the calls. */
class FakeAuthRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : AuthRepository {
    val account = UserAccount("user-1", "ada@findora.app", "Ada Lovelace")
    private val sessionState = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = sessionState

    var restoredSession: Session? = null
    var loginResult: AuthResult<UserAccount> = AuthResult.Success(account)
    var signUpResult: AuthResult<UserAccount> = AuthResult.Success(account)
    var resetRequestResult: AuthResult<PasswordResetTicket> =
        AuthResult.Success(PasswordResetTicket("ada@findora.app", "654321", Instant.EPOCH))
    var resetResult: AuthResult<Unit> = AuthResult.Success(Unit)
    val loginCalls = mutableListOf<Triple<String, String, Boolean>>()
    var demoLogins = 0
    var logouts = 0
    var deletions = 0

    fun signIn(remembered: Boolean = true) {
        sessionState.value = Session(account.id, false, now(), if (remembered) now().plusSeconds(60) else null)
    }

    override fun observeCurrentUser(): Flow<UserAccount?> =
        sessionState.map { if (it == null || it.isGuest) null else account }

    override suspend fun restoreSession(): Session? = restoredSession.also { sessionState.value = it }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
        rememberMe: Boolean,
    ): AuthResult<UserAccount> = signUpResult.also { if (it is AuthResult.Success) signIn(rememberMe) }

    override suspend fun login(
        email: String,
        password: String,
        rememberMe: Boolean,
    ): AuthResult<UserAccount> {
        loginCalls += Triple(email, password, rememberMe)
        return loginResult.also { if (it is AuthResult.Success) signIn(rememberMe) }
    }

    override suspend fun loginWithDemoAccount(): AuthResult<UserAccount> {
        demoLogins++
        return loginResult.also { if (it is AuthResult.Success) signIn() }
    }

    override suspend fun continueAsGuest(): Session =
        Session(AuthRepository.GUEST_USER_ID, true, now(), null).also { sessionState.value = it }

    override suspend fun requestPasswordReset(email: String): AuthResult<PasswordResetTicket> = resetRequestResult

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit> = resetResult

    override suspend fun logout() {
        logouts++
        sessionState.value = null
    }

    override suspend fun deleteAccount(): AuthResult<Unit> {
        deletions++
        sessionState.value = null
        return AuthResult.Success(Unit)
    }

    companion object {
        fun lockedOut(until: Instant): AuthResult<UserAccount> = AuthResult.Failure(AuthError.LockedOut(until))
    }
}

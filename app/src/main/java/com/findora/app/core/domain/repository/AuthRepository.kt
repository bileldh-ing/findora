package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.PasswordResetTicket
import com.findora.app.core.domain.model.Session
import com.findora.app.core.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication boundary. The default implementation is local (Room + Keystore); a Firebase, Supabase or
 * REST implementation can replace it without touching UI or ViewModels.
 */
interface AuthRepository {
    /** The current session, or null when signed out. */
    val session: StateFlow<Session?>

    /** The signed-in account, or null for guests and signed-out users. */
    fun observeCurrentUser(): Flow<UserAccount?>

    /** Loads a persisted session at launch. Expired or orphaned sessions are deleted and null is returned. */
    suspend fun restoreSession(): Session?

    suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
        rememberMe: Boolean = true,
    ): AuthResult<UserAccount>

    suspend fun login(
        email: String,
        password: String,
        rememberMe: Boolean,
    ): AuthResult<UserAccount>

    /** Signs in to the pre-seeded demo account, creating it on first use. */
    suspend fun loginWithDemoAccount(): AuthResult<UserAccount>

    /** Starts a guest session (process-only, never persisted). */
    suspend fun continueAsGuest(): Session

    /** Local demo flow: issues a short-lived reset code that the UI displays instead of e-mailing it. */
    suspend fun requestPasswordReset(email: String): AuthResult<PasswordResetTicket>

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): AuthResult<Unit>

    suspend fun logout()

    /** Deletes the signed-in account and every row keyed by its id, then signs out. */
    suspend fun deleteAccount(): AuthResult<Unit>

    companion object {
        const val DEMO_EMAIL = "demo@findora.app"
        const val DEMO_PASSWORD = "Demo1234"
        const val DEMO_NAME = "Demo Explorer"

        /** Owner id for data created while browsing as a guest. */
        const val GUEST_USER_ID = "guest"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_SECONDS = 30L
        const val REMEMBER_ME_DAYS = 30L
        const val RESET_CODE_MINUTES = 10L
    }
}

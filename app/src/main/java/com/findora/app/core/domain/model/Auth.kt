package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.util.Locale

/** A signed-in account as the UI sees it. Never carries credentials. */
@Immutable
data class UserAccount(
    val id: String,
    val email: String,
    val displayName: String,
) {
    /** Up to two initials for the avatar fallback ("Bilel Dhahri" -> "BD", "demo" -> "D"). */
    val initials: String
        get() =
            displayName
                .trim()
                .split(Regex("\\s+"))
                .filter(String::isNotEmpty)
                .take(2)
                .joinToString("") { it.first().toString() }
                .uppercase(Locale.ROOT)
                .ifEmpty { email.take(1).uppercase(Locale.ROOT) }
}

/**
 * The current session. [expiresAt] is null for a session that lives only as long as the app process
 * ("Remember me" off, or guest browsing); such sessions are never written to disk.
 */
@Immutable
data class Session(
    val userId: String,
    val isGuest: Boolean,
    val issuedAt: Instant,
    val expiresAt: Instant?,
) {
    val isPersistent: Boolean get() = expiresAt != null

    fun isValidAt(now: Instant): Boolean = expiresAt == null || now.isBefore(expiresAt)
}

/** A password reset requested through the local demo flow. No e-mail is sent: the code is shown in-app. */
@Immutable
data class PasswordResetTicket(
    val email: String,
    val code: String,
    val expiresAt: Instant,
)

/** Why an auth operation failed. Validation errors are reported field by field so the UI can show them inline. */
sealed interface AuthError {
    data class InvalidInput(
        val name: NameError? = null,
        val email: EmailError? = null,
        val password: Set<PasswordRule> = emptySet(),
    ) : AuthError

    /** Wrong e-mail or password (deliberately not saying which). */
    data class InvalidCredentials(
        val attemptsRemaining: Int,
    ) : AuthError

    /** Too many failed attempts: sign-in is refused until [until]. */
    data class LockedOut(
        val until: Instant,
    ) : AuthError

    data object EmailTaken : AuthError

    data object AccountNotFound : AuthError

    data object InvalidResetCode : AuthError

    data object ResetCodeExpired : AuthError

    /** The operation needs a signed-in (non-guest) account. */
    data object NotSignedIn : AuthError
}

sealed interface AuthResult<out T> {
    data class Success<T>(
        val value: T,
    ) : AuthResult<T>

    data class Failure(
        val error: AuthError,
    ) : AuthResult<Nothing>
}

enum class NameError { Blank, TooLong }

enum class EmailError { Blank, Malformed }

/** A password rule; validation returns the rules that are not met yet. */
enum class PasswordRule { MinLength, Letter, Digit }

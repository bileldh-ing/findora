package com.findora.app.core.domain.auth

import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.NameError
import com.findora.app.core.domain.model.PasswordRule
import java.util.Locale

/** Pure validation rules shared by the UI (inline messages) and the repository (the real gate). */
object CredentialValidator {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 60
    private const val MAX_EMAIL_LENGTH = 254

    // Pragmatic address check: local part, "@", a domain with at least one dot and a 2+ letter TLD.
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    /** E-mails are compared case-insensitively, so they are stored trimmed and lower-cased. */
    fun normalizeEmail(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    fun validateEmail(raw: String): EmailError? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> EmailError.Blank
            email.length > MAX_EMAIL_LENGTH || !emailPattern.matches(email) -> EmailError.Malformed
            else -> null
        }
    }

    fun unmetPasswordRules(password: String): Set<PasswordRule> =
        buildSet {
            if (password.length < MIN_PASSWORD_LENGTH) add(PasswordRule.MinLength)
            if (password.none(Char::isLetter)) add(PasswordRule.Letter)
            if (password.none(Char::isDigit)) add(PasswordRule.Digit)
        }

    fun validateName(raw: String): NameError? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameError.Blank
            name.length > MAX_NAME_LENGTH -> NameError.TooLong
            else -> null
        }
    }
}

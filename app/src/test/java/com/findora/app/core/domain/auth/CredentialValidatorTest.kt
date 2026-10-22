package com.findora.app.core.domain.auth

import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.NameError
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.domain.model.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialValidatorTest {
    @Test
    fun emailsAreTrimmedLowerCasedAndValidated() {
        assertEquals("ada@findora.app", CredentialValidator.normalizeEmail("  Ada@Findora.APP "))
        assertNull(CredentialValidator.validateEmail("ada.lovelace+test@mail.findora.app"))
        assertEquals(EmailError.Blank, CredentialValidator.validateEmail("   "))
        listOf("ada", "ada@", "@findora.app", "ada@findora", "ada@@findora.app", "a da@findora.app").forEach {
            assertEquals(it, EmailError.Malformed, CredentialValidator.validateEmail(it))
        }
    }

    @Test
    fun passwordNeedsEightCharactersALetterAndADigit() {
        assertEquals(emptySet<PasswordRule>(), CredentialValidator.unmetPasswordRules("Demo1234"))
        assertEquals(setOf(PasswordRule.MinLength), CredentialValidator.unmetPasswordRules("abc123"))
        assertEquals(setOf(PasswordRule.Digit), CredentialValidator.unmetPasswordRules("abcdefgh"))
        assertEquals(setOf(PasswordRule.Letter), CredentialValidator.unmetPasswordRules("12345678"))
        assertEquals(PasswordRule.entries.toSet(), CredentialValidator.unmetPasswordRules(""))
    }

    @Test
    fun namesMustBePresentAndShort() {
        assertNull(CredentialValidator.validateName("Ada Lovelace"))
        assertEquals(NameError.Blank, CredentialValidator.validateName("  "))
        assertEquals(NameError.TooLong, CredentialValidator.validateName("a".repeat(61)))
    }

    @Test
    fun initialsFallBackToTheEmail() {
        assertEquals("AL", UserAccount("1", "ada@findora.app", "ada lovelace byron").initials)
        assertEquals("D", UserAccount("1", "demo@findora.app", "Demo").initials)
        assertEquals("X", UserAccount("1", "x@findora.app", " ").initials)
    }
}

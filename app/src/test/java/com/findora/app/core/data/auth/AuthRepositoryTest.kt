package com.findora.app.core.data.auth

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.entity.FavoriteEntity
import com.findora.app.core.data.local.entity.RecentSearchEntity
import com.findora.app.core.domain.model.AuthError
import com.findora.app.core.domain.model.AuthResult
import com.findora.app.core.domain.model.PasswordRule
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeSecretGenerator
import com.findora.app.core.testing.InMemorySessionStore
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.security.SecureRandom
import java.time.Duration

/**
 * LocalAuthRepository against a real in-memory Room database. PBKDF2 runs with a reduced iteration count to
 * keep the suite fast; PasswordHasherTest covers the production parameters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AuthRepositoryTest {
    private lateinit var db: FindoraDatabase
    private lateinit var store: InMemorySessionStore
    private lateinit var clock: MutableClock
    private lateinit var secrets: FakeSecretGenerator

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        store = InMemorySessionStore()
        clock = MutableClock()
        secrets = FakeSecretGenerator()
    }

    @After
    fun tearDown() = db.close()

    private fun repository(sessionStore: SessionStore = store) =
        LocalAuthRepository(
            userDao = db.userDao(),
            accountDao = db.accountDao(),
            hasher = PasswordHasher(FAST_ITERATIONS, SecureRandom(), Dispatchers.Unconfined),
            sessionStore = sessionStore,
            clock = clock,
            secrets = secrets,
        )

    @Test
    fun signUpCreatesAHashedAccountAndARememberedSession() =
        runTest {
            val repo = repository()
            val result = repo.signUp("Ada Lovelace", " Ada@Findora.app ", "Secret123")
            val account = (result as AuthResult.Success).value
            assertEquals("ada@findora.app", account.email)
            val row = db.userDao().findByEmail("ada@findora.app")!!
            assertFalse(row.passwordHash.contains("Secret123"))
            assertTrue(row.passwordHash.startsWith("pbkdf2_sha256$"))
            val session = repo.session.value!!
            assertEquals(account.id, session.userId)
            assertEquals(clock.now().plus(Duration.ofDays(30)), session.expiresAt)
            assertEquals(account.id, store.stored?.userId)
        }

    @Test
    fun duplicateEmailsAreRejectedCaseInsensitively() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            val duplicate = repo.signUp("Other Ada", "ADA@FINDORA.APP", "Another123")
            assertEquals(AuthResult.Failure(AuthError.EmailTaken), duplicate)
        }

    @Test
    fun invalidSignUpReportsEveryFieldAtOnce() =
        runTest {
            val result = repository().signUp(" ", "not-an-email", "short")
            val error = (result as AuthResult.Failure).error as AuthError.InvalidInput
            assertNotNull(error.name)
            assertNotNull(error.email)
            assertEquals(setOf(PasswordRule.MinLength, PasswordRule.Digit), error.password)
        }

    @Test
    fun loginIsCaseInsensitiveOnEmailButNotOnPassword() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            repo.logout()
            assertTrue(repo.login("ADA@findora.APP", "Secret123", rememberMe = true) is AuthResult.Success)
            repo.logout()
            assertEquals(
                AuthResult.Failure(AuthError.InvalidCredentials(attemptsRemaining = 4)),
                repo.login("ada@findora.app", "secret123", rememberMe = true),
            )
        }

    @Test
    fun fiveFailuresLockTheEmailForThirtySecondsThenItUnlocks() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            repo.logout()
            repeat(4) { attempt ->
                val result = repo.login("ada@findora.app", "wrong-$attempt", rememberMe = false)
                assertEquals(AuthResult.Failure(AuthError.InvalidCredentials(4 - attempt)), result)
            }
            val lockedUntil = clock.now().plusSeconds(30)
            assertEquals(
                AuthResult.Failure(AuthError.LockedOut(lockedUntil)),
                repo.login("ada@findora.app", "wrong-5", rememberMe = false),
            )
            // Even the right password is refused during the lockout, and the lockout survives a new repository.
            clock.advance(Duration.ofSeconds(29))
            assertEquals(
                AuthResult.Failure(AuthError.LockedOut(lockedUntil)),
                repository().login("ada@findora.app", "Secret123", rememberMe = false),
            )
            clock.advance(Duration.ofSeconds(1))
            assertTrue(repo.login("ada@findora.app", "Secret123", rememberMe = false) is AuthResult.Success)
            // A successful sign-in resets the counter.
            repo.logout()
            assertEquals(
                AuthResult.Failure(AuthError.InvalidCredentials(4)),
                repo.login("ada@findora.app", "nope", rememberMe = false),
            )
        }

    @Test
    fun unknownEmailsAreThrottledLikeRealOnes() =
        runTest {
            val repo = repository()
            repeat(4) { repo.login("ghost@findora.app", "whatever1", rememberMe = false) }
            val result = repo.login("ghost@findora.app", "whatever1", rememberMe = false)
            assertTrue((result as AuthResult.Failure).error is AuthError.LockedOut)
        }

    @Test
    fun rememberedSessionIsRestoredUntilItExpires() =
        runTest {
            repository().signUp("Ada", "ada@findora.app", "Secret123", rememberMe = true)
            // A fresh repository (an app relaunch) restores the session from the store.
            val relaunched = repository()
            assertNotNull(relaunched.restoreSession())
            clock.advance(Duration.ofDays(30))
            val afterExpiry = repository()
            assertNull(afterExpiry.restoreSession())
            assertNull(store.stored)
        }

    @Test
    fun sessionWithoutRememberMeIsNeverPersisted() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            repo.logout()
            repo.login("ada@findora.app", "Secret123", rememberMe = false)
            assertNull(repo.session.value!!.expiresAt)
            assertNull(store.stored)
            assertNull(repository().restoreSession())
        }

    @Test
    fun restoredSessionOfADeletedUserIsDiscarded() =
        runTest {
            store.write(
                StoredSession(
                    "missing-user",
                    "t",
                    clock.now().toEpochMilli(),
                    clock.now().plusSeconds(60).toEpochMilli(),
                ),
            )
            assertNull(repository().restoreSession())
            assertNull(store.stored)
        }

    @Test
    fun demoAccountIsSeededOnFirstUseAndWorksWithTheFormToo() =
        runTest {
            val repo = repository()
            val demo = repo.loginWithDemoAccount() as AuthResult.Success
            assertEquals(AuthRepository.DEMO_EMAIL, demo.value.email)
            repo.logout()
            val viaForm = repository().login("demo@findora.app", "Demo1234", rememberMe = true)
            assertTrue(viaForm is AuthResult.Success)
        }

    @Test
    fun guestSessionsAreNotPersistedAndStartClean() =
        runTest {
            val repo = repository()
            db.favoriteDao().insert(FavoriteEntity(AuthRepository.GUEST_USER_ID, "p1", 0))
            val guest = repo.continueAsGuest()
            assertTrue(guest.isGuest)
            assertNull(store.stored)
            assertEquals(0, db.favoriteDao().count(AuthRepository.GUEST_USER_ID))
            repo.observeCurrentUser().test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun passwordResetWithTheDemoCodeChangesThePassword() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            repo.logout()
            val ticket = (repo.requestPasswordReset("ADA@findora.app") as AuthResult.Success).value
            assertEquals("123456", ticket.code)
            assertEquals(
                AuthResult.Failure(AuthError.InvalidResetCode),
                repo.resetPassword("ada@findora.app", "000000", "Brand123"),
            )
            assertEquals(AuthResult.Success(Unit), repo.resetPassword("ada@findora.app", "123456", "Brand123"))
            assertTrue(repo.login("ada@findora.app", "Brand123", rememberMe = false) is AuthResult.Success)
            // A used code cannot be replayed.
            assertEquals(
                AuthResult.Failure(AuthError.InvalidResetCode),
                repo.resetPassword("ada@findora.app", "123456", "Other123"),
            )
        }

    @Test
    fun resetCodesExpireAfterTenMinutesAndUnknownEmailsAreReported() =
        runTest {
            val repo = repository()
            repo.signUp("Ada", "ada@findora.app", "Secret123")
            assertEquals(AuthResult.Failure(AuthError.AccountNotFound), repo.requestPasswordReset("nobody@findora.app"))
            repo.requestPasswordReset("ada@findora.app")
            clock.advance(Duration.ofMinutes(10))
            assertEquals(
                AuthResult.Failure(AuthError.ResetCodeExpired),
                repo.resetPassword("ada@findora.app", "123456", "Brand123"),
            )
            assertEquals(
                AuthResult.Failure(AuthError.InvalidInput(password = setOf(PasswordRule.Digit))),
                repo.resetPassword("ada@findora.app", "123456", "nodigits"),
            )
        }

    @Test
    fun deleteAccountRemovesTheUserAndEveryRowKeyedByItsId() =
        runTest {
            val repo = repository()
            val userId = (repo.signUp("Ada", "ada@findora.app", "Secret123") as AuthResult.Success).value.id
            db.favoriteDao().insert(FavoriteEntity(userId, "p1", 0))
            db.favoriteDao().insert(FavoriteEntity("someone-else", "p1", 0))
            db.recentSearchDao().insert(
                RecentSearchEntity(userId = userId, query = "Kochi", normalizedQuery = "kochi", searchedAtMillis = 0),
            )
            assertEquals(AuthResult.Success(Unit), repo.deleteAccount())
            assertNull(db.userDao().findById(userId))
            assertEquals(0, db.favoriteDao().count(userId))
            assertEquals(1, db.favoriteDao().count("someone-else"))
            assertTrue(
                db
                    .recentSearchDao()
                    .observeRecent(userId, 10)
                    .first()
                    .isEmpty(),
            )
            assertNull(repo.session.value)
            assertNull(store.stored)
            // The e-mail can be registered again.
            assertTrue(repo.signUp("Ada", "ada@findora.app", "Secret123") is AuthResult.Success)
        }

    @Test
    fun deleteAccountNeedsASignedInUser() =
        runTest {
            val repo = repository()
            assertEquals(AuthResult.Failure(AuthError.NotSignedIn), repo.deleteAccount())
            repo.continueAsGuest()
            assertEquals(AuthResult.Failure(AuthError.NotSignedIn), repo.deleteAccount())
        }

    @Test
    fun currentUserFollowsTheSession() =
        runTest {
            val repo = repository()
            repo.observeCurrentUser().test {
                assertNull(awaitItem())
                repo.signUp("Ada Lovelace", "ada@findora.app", "Secret123")
                assertEquals("Ada Lovelace", awaitItem()?.displayName)
                repo.logout()
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val FAST_ITERATIONS = 1_000
    }
}

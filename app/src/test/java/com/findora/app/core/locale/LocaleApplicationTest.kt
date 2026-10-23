package com.findora.app.core.locale

import android.app.Application
import android.util.LayoutDirection
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.findora.app.R
import com.findora.app.core.data.chat.ResourceAgentReplyFormatter
import com.findora.app.core.domain.chat.AgentReply
import com.findora.app.core.domain.chat.PriceFlexibility
import com.findora.app.core.domain.chat.ReplyPart
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.model.AppLanguage
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.testRates
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.ZoneId

/**
 * The per-app language below Android 13 (API 32), where AppCompat, not the system, keeps it: applying a
 * language must reach AppCompat, resources resolved through [LocalizedContext] (notifications, worker, chat
 * replies) and the layout direction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocaleApplicationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val settings = FakeSettingsRepository()
    private val controller = AppCompatLanguageController(settings, TestScope(UnconfinedTestDispatcher()))
    private val localized = LocalizedContext(context, controller)

    @After
    fun resetLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun applyingALanguageSetsTheAppCompatApplicationLocales() {
        controller.apply(AppLanguage.French)
        assertEquals("fr", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertEquals(AppLanguage.French, controller.current())

        controller.apply(AppLanguage.System)
        assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty)
        assertEquals(AppLanguage.System, controller.current())
    }

    @Test
    fun localizedResourcesFollowTheChosenLanguage() {
        controller.apply(AppLanguage.French)
        assertEquals("Paramètres", localized.get().getString(R.string.settings_title))
        assertEquals("fr", localized.locale.language)

        controller.apply(AppLanguage.Arabic)
        val arabic = localized.get()
        assertEquals("الإعدادات", arabic.getString(R.string.settings_title))
        assertEquals(LayoutDirection.RTL, arabic.resources.configuration.layoutDirection)

        controller.apply(AppLanguage.English)
        assertEquals("Settings", localized.get().getString(R.string.settings_title))
        assertEquals(
            LayoutDirection.LTR,
            localized
                .get()
                .resources.configuration.layoutDirection,
        )
    }

    @Test
    fun withoutAnActivityTheSettingsCopyIsTheFallback() {
        // AppCompat knows nothing yet (fresh process, no activity): the DataStore language is used.
        assertEquals("Paramètres", localized.resolve(AppLanguage.French).getString(R.string.settings_title))
    }

    @Test
    fun chatRepliesAreWrittenInTheChosenLanguageWithTheDisplayUnitAndCurrency() {
        controller.apply(AppLanguage.French)
        val reply =
            AgentReply(
                listOf(
                    ReplyPart.Size(bedrooms = 2, bathrooms = 1, areaSqm = 100),
                    ReplyPart.Price(Money(9_000, Currency.INR), PriceFlexibility.Firm),
                ),
            )
        val text =
            ResourceAgentReplyFormatter(localized).format(
                reply,
                ZoneId.of("Africa/Tunis"),
                DisplayPreferences(currency = Currency.EUR, areaUnit = AreaUnit.Imperial, rates = testRates),
            )
        assertTrue(text, text.startsWith("Il compte 2 chambres, 1 salle de bain et "))
        assertTrue(text, text.contains("pi² habitables."))
        assertTrue(text, text.contains("1\u202F076") || text.contains("1\u00A0076") || text.contains("1 076"))
        // 9 000 INR at 90 per USD and 0.9 EUR per USD = 90 EUR, marked approximate.
        assertTrue(text, text.contains("Le loyer est de ≈\u00A090"))
        assertTrue(text, text.contains("€"))
    }
}

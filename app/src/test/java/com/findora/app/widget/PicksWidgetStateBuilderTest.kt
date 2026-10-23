package com.findora.app.widget

import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.ExchangeRates
import com.findora.app.core.domain.match.BehaviorAffinity
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class PicksWidgetStateBuilderTest {
    private val now = Instant.parse("2026-10-24T09:00:00Z")
    private val kerala = seedProperties.filter { it.regionId == "kerala" }

    private val texts =
        object : PicksWidgetTexts {
            override val perMonth = "Month"
            override val empty = "empty"
            override val signedOut = "signed out"

            override fun heading(source: PicksSource) = source.name

            override fun matchPercent(percent: Int) = "$percent% match"

            override fun nextViewing(
                title: String,
                startAt: Instant,
            ) = "Next: $title at $startAt"

            override fun pickDescription(
                title: String,
                locality: String,
                price: String,
                match: String?,
            ) = listOfNotNull(title, locality, price, match).joinToString(", ")
        }

    @Suppress("LongParameterList")
    private fun build(
        signedIn: Boolean = true,
        favorites: List<com.findora.app.core.domain.model.Property> = emptyList(),
        match: MatchContext? = null,
        viewings: List<Viewing> = emptyList(),
        display: DisplayPreferences = DisplayPreferences(),
    ) = PicksWidgetStateBuilder.build(
        signedIn = signedIn,
        listings = kerala,
        favorites = favorites,
        match = match,
        viewings = viewings,
        viewingTitles = kerala.associate { it.id to it.title },
        display = display,
        locale = Locale.ENGLISH,
        now = now,
        texts = texts,
    )

    @Test
    fun aSignedOutWidgetShowsNoHomes() {
        val model = build(signedIn = false)
        assertTrue(model.picks.isEmpty())
        assertEquals("signed out", model.emptyText)
        assertNull(model.viewingLine)
    }

    @Test
    fun withoutAProfileOrFavouritesItShowsTheRecommendedTopThree() {
        val model = build()
        assertEquals(PicksSource.Recommended, model.source)
        val expected =
            kerala
                .sortedWith(
                    compareByDescending<com.findora.app.core.domain.model.Property> { it.verified }
                        .thenByDescending { it.rating }
                        .thenByDescending { it.reviewsCount }
                        .thenBy { it.id },
                ).take(3)
        assertEquals(expected.map { it.id }, model.picks.map { it.propertyId })
        assertTrue(model.picks.all { it.matchText == null })
        val first = model.picks.first()
        assertEquals(
            expected
                .first()
                .images
                .first()
                .url,
            first.thumbnailUrl,
        )
        assertTrue(first.priceText.endsWith("/ Month"))
        assertTrue(first.contentDescription.startsWith(expected.first().title))
    }

    @Test
    fun favouritesComeBeforeRecommendationsWithoutAProfile() {
        val saved = kerala.takeLast(4)
        val model = build(favorites = saved)
        assertEquals(PicksSource.Saved, model.source)
        assertEquals(saved.take(3).map { it.id }, model.picks.map { it.propertyId })
    }

    @Test
    fun withAProfileItShowsBestMatchesWithTheirScore() {
        val context = MatchContext(PreferenceProfile(minBedrooms = 2), BehaviorAffinity.None, now)
        val model = build(match = context, favorites = kerala.take(1))
        assertEquals(PicksSource.Matches, model.source)
        assertEquals(3, model.picks.size)
        assertTrue(model.picks.all { it.matchText?.endsWith("% match") == true })
        val scores = model.picks.map { it.matchText!!.substringBefore('%').toInt() }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun pricesFollowTheDisplayCurrency() {
        val rates =
            ExchangeRates(
                asOf = LocalDate.of(2026, 10, 1),
                base = Currency.USD,
                perBase =
                    mapOf(
                        Currency.USD to BigDecimal.ONE,
                        Currency.INR to BigDecimal("84"),
                        Currency.EUR to BigDecimal("0.92"),
                        Currency.TND to BigDecimal("3.1"),
                    ),
            )
        val model = build(display = DisplayPreferences(currency = Currency.USD, rates = rates))
        assertTrue(model.picks.all { it.priceText.startsWith("≈") && it.priceText.contains("$") })
    }

    @Test
    fun theViewingLineIsTheNextUpcomingViewing() {
        val past = Viewing(1, "u", kerala[0].id, now.minus(Duration.ofHours(2)), 30, "", now)
        val later = Viewing(2, "u", kerala[1].id, now.plus(Duration.ofDays(2)), 30, "", now)
        val soon = Viewing(3, "u", kerala[2].id, now.plus(Duration.ofHours(3)), 30, "", now)
        val model = build(viewings = listOf(past, later, soon))
        assertEquals("Next: ${kerala[2].title} at ${soon.startAt}", model.viewingLine)
        assertNull(build(viewings = listOf(past)).viewingLine)
    }
}

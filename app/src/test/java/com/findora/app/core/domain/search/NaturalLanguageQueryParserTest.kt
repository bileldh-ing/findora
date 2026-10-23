package com.findora.app.core.domain.search

import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.testing.fileSeedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageQueryParserTest {
    private fun parse(query: String): ParsedIntent = NaturalLanguageQueryParser.parse(query, regions)

    /** One row of the table: a sentence and the filter fields it must produce (unlisted fields stay unset). */
    private data class Case(
        val query: String,
        val expected: PropertyFilter,
        val regionId: String? = null,
        val currency: Currency? = null,
    )

    private fun assertCases(cases: List<Case>) {
        val failures =
            cases.mapNotNull { case ->
                val intent = parse(case.query)
                val actual = intent.filter
                val problems =
                    buildList {
                        if (actual != case.expected) add("filter $actual")
                        if (case.regionId != null && intent.regionId != case.regionId) add("region ${intent.regionId}")
                        if (case.currency != null && intent.budget?.currency != case.currency) {
                            add("currency ${intent.budget?.currency}")
                        }
                    }
                problems.takeIf { it.isNotEmpty() }?.let { "\"${case.query}\" -> ${it.joinToString()} (unrecognized ${intent.unrecognized})" }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun englishBudgetsNumbersAndRanges() =
        assertCases(
            listOf(
                Case("under 2000", PropertyFilter(maxRent = 2000)),
                Case("below 15k", PropertyFilter(maxRent = 15_000)),
                Case("max 1.5k", PropertyFilter(maxRent = 1_500)),
                Case("less than 2,500", PropertyFilter(maxRent = 2_500)),
                Case("up to 18,000 rupees", PropertyFilter(maxRent = 18_000), currency = Currency.INR),
                Case("budget of $1200", PropertyFilter(maxRent = 1_200), currency = Currency.USD),
                Case("at least 900", PropertyFilter(minRent = 900)),
                Case("over 5000 aed", PropertyFilter(minRent = 5_000), regionId = "dubai"),
                Case("between 1 500 and 2 000 TND", PropertyFilter(minRent = 1_500, maxRent = 2_000), regionId = "tunis"),
                Case("between 2000 and 1500", PropertyFilter(minRent = 1_500, maxRent = 2_000)),
                Case("1.5-2k", PropertyFilter(minRent = 1_500, maxRent = 2_000)),
                Case("15k to 25k", PropertyFilter(minRent = 15_000, maxRent = 25_000)),
                Case("1200-1800", PropertyFilter(minRent = 1_200, maxRent = 1_800)),
                Case("over 1000 under 2000", PropertyFilter(minRent = 1_000, maxRent = 2_000)),
                Case("2 lakh", PropertyFilter(maxRent = 200_000)),
                Case("₹20000", PropertyFilter(maxRent = 20_000), regionId = "kerala"),
                Case("€1500", PropertyFilter(maxRent = 1_500), regionId = "paris"),
                Case("2000 dt", PropertyFilter(maxRent = 2_000), regionId = "tunis"),
                Case("around 3000", PropertyFilter(maxRent = 3_000)),
                Case("apartment 1800", PropertyFilter(type = PropertyType.Apartment, maxRent = 1_800)),
                Case("under 2 000 in dinars", PropertyFilter(maxRent = 2_000), currency = Currency.TND),
            ),
        )

    @Test
    fun englishRoomsTypesAndAreas() =
        assertCases(
            listOf(
                Case("2 bedroom apartment", PropertyFilter(type = PropertyType.Apartment, minBedrooms = 2)),
                Case("2BHK", PropertyFilter(minBedrooms = 2)),
                Case("3 bhk flat", PropertyFilter(type = PropertyType.Apartment, minBedrooms = 3)),
                Case("3+ beds", PropertyFilter(minBedrooms = 3)),
                Case("two bedrooms", PropertyFilter(minBedrooms = 2)),
                Case("2-bedroom villa", PropertyFilter(type = PropertyType.House, minBedrooms = 2)),
                Case("at least 2 bathrooms", PropertyFilter(minBathrooms = 2)),
                Case("more than 2 bedrooms", PropertyFilter(minBedrooms = 3)),
                Case("2-3 bedrooms", PropertyFilter(minBedrooms = 2)),
                Case("1 bed 1 bath", PropertyFilter(minBedrooms = 1, minBathrooms = 1)),
                Case("studio", PropertyFilter(type = PropertyType.Apartment)),
                Case("house", PropertyFilter(type = PropertyType.House)),
                Case("shared room", PropertyFilter(type = PropertyType.Room)),
                Case("100 m2", PropertyFilter(minAreaSqm = 100)),
                Case("80m²", PropertyFilter(minAreaSqm = 80)),
                Case("under 60 sqm", PropertyFilter(maxAreaSqm = 60)),
                Case("between 80 and 120 m2", PropertyFilter(minAreaSqm = 80, maxAreaSqm = 120)),
                Case("1000 sq ft", PropertyFilter(minAreaSqm = 93)),
            ),
        )

    @Test
    fun englishMustHavesAmenitiesAndSort() =
        assertCases(
            listOf(
                Case("pet friendly", PropertyFilter(petFriendlyOnly = true)),
                Case("pets ok with parking", PropertyFilter(petFriendlyOnly = true, parkingOnly = true)),
                Case("fully furnished", PropertyFilter(furnishedOnly = true)),
                Case("verified only", PropertyFilter(verifiedOnly = true)),
                Case(
                    "balcony, pool, gym and wi-fi",
                    PropertyFilter(amenities = setOf(Amenity.Balcony, Amenity.SwimmingPool, Amenity.Gym, Amenity.Wifi)),
                ),
                Case("sea view with lift", PropertyFilter(amenities = setOf(Amenity.SeaView, Amenity.Lift))),
                Case("cheapest", PropertyFilter(sort = SortOrder.PriceLowToHigh)),
                Case("newest", PropertyFilter(sort = SortOrder.Newest)),
                Case("top rated", PropertyFilter(sort = SortOrder.Rating)),
                Case("luxury villa", PropertyFilter(type = PropertyType.House, sort = SortOrder.PriceHighToLow)),
            ),
        )

    @Test
    fun placesExactAliasesAndTypos() =
        assertCases(
            listOf(
                Case("La Marsa", PropertyFilter(query = "La Marsa"), regionId = "tunis"),
                Case("la marsa", PropertyFilter(query = "La Marsa"), regionId = "tunis"),
                Case("marsa", PropertyFilter(query = "La Marsa"), regionId = "tunis"),
                Case("Fort Kochi", PropertyFilter(query = "Fort Kochi"), regionId = "kerala"),
                Case("kochi", PropertyFilter(query = "Kochi"), regionId = "kerala"),
                Case("Saint-Germain-des-Prés", PropertyFilter(query = "Saint-Germain-des-Prés"), regionId = "paris"),
                Case("saint germain des pres", PropertyFilter(query = "Saint-Germain-des-Prés"), regionId = "paris"),
                Case("Sidi Bou Saïd", PropertyFilter(query = "Sidi Bou Said"), regionId = "tunis"),
                Case("kowdiar", PropertyFilter(query = "Kowdiar, Thiruvananthapuram"), regionId = "kerala"),
                Case("Kakkanadd", PropertyFilter(query = "Kakkanad"), regionId = "kerala"),
                Case("Gamarth", PropertyFilter(query = "Gammarth"), regionId = "tunis"),
                Case("montmatre", PropertyFilter(query = "Montmartre"), regionId = "paris"),
                Case("Dubai Marina", PropertyFilter(query = "Dubai Marina"), regionId = "dubai"),
                Case("JVC", PropertyFilter(query = "Jumeirah Village Circle"), regionId = "dubai"),
                Case("Paris", PropertyFilter(), regionId = "paris"),
                Case("dubai", PropertyFilter(), regionId = "dubai"),
                Case("La Marsa Tunis", PropertyFilter(query = "La Marsa"), regionId = "tunis"),
            ),
        )

    @Test
    fun frenchSentences() =
        assertCases(
            listOf(
                Case(
                    "T3 meublé à La Marsa moins de 2 000 DT",
                    PropertyFilter(
                        type = PropertyType.Apartment,
                        minBedrooms = 2,
                        furnishedOnly = true,
                        query = "La Marsa",
                        maxRent = 2_000,
                    ),
                    regionId = "tunis",
                    currency = Currency.TND,
                ),
                Case("T1", PropertyFilter(type = PropertyType.Apartment)),
                Case("F4", PropertyFilter(type = PropertyType.Apartment, minBedrooms = 3)),
                Case("appartement 2 chambres", PropertyFilter(type = PropertyType.Apartment, minBedrooms = 2)),
                Case("maison 3 pièces", PropertyFilter(type = PropertyType.House, minBedrooms = 2)),
                Case("studio calme au Marais sous 1500 €", PropertyFilter(type = PropertyType.Apartment, query = "Le Marais", maxRent = 1_500)),
                Case("de 800 à 1200 euros", PropertyFilter(minRent = 800, maxRent = 1_200), currency = Currency.EUR),
                Case("au moins 2 salles de bain", PropertyFilter(minBathrooms = 2)),
                Case("villa avec piscine, animaux acceptés", PropertyFilter(type = PropertyType.House, petFriendlyOnly = true, amenities = setOf(Amenity.SwimmingPool))),
                Case("pas cher avec parking", PropertyFilter(parkingOnly = true, sort = SortOrder.PriceLowToHigh)),
                Case("climatisation et ascenseur", PropertyFilter(amenities = setOf(Amenity.AirConditioning, Amenity.Lift))),
                Case("budget max 1,5k", PropertyFilter(maxRent = 1_500)),
                Case("deux mille euros", PropertyFilter(maxRent = 2_000), currency = Currency.EUR),
            ),
        )

    @Test
    fun arabicSentencesAndDigits() =
        assertCases(
            listOf(
                Case(
                    "شقة غرفتين في المرسى أقل من ٢٠٠٠ دينار",
                    PropertyFilter(type = PropertyType.Apartment, minBedrooms = 2, query = "La Marsa", maxRent = 2_000),
                    regionId = "tunis",
                    currency = Currency.TND,
                ),
                Case("فيلا مفروشة مع مسبح", PropertyFilter(type = PropertyType.House, furnishedOnly = true, amenities = setOf(Amenity.SwimmingPool))),
                Case("ثلاث غرف وحمامين", PropertyFilter(minBedrooms = 3, minBathrooms = 2)),
                Case("٣ غرف نوم", PropertyFilter(minBedrooms = 3)),
                Case("أقل من ١٥٠٠", PropertyFilter(maxRent = 1_500)),
                Case("بين ١٠٠٠ و ٢٠٠٠ درهم", PropertyFilter(minRent = 1_000, maxRent = 2_000), regionId = "dubai"),
                Case("ألفين دينار", PropertyFilter(maxRent = 2_000), currency = Currency.TND),
                Case("٢ ألف", PropertyFilter(maxRent = 2_000)),
                Case("مع موقف سيارات وحيوانات أليفة", PropertyFilter(parkingOnly = true, petFriendlyOnly = true)),
                Case("شقة بالمرسى", PropertyFilter(type = PropertyType.Apartment, query = "La Marsa"), regionId = "tunis"),
                Case("فيلا في نخلة جميرا", PropertyFilter(type = PropertyType.House, query = "Palm Jumeirah"), regionId = "dubai"),
                Case("شقة هادئة قرب المترو في دبي مارينا", PropertyFilter(type = PropertyType.Apartment, query = "Dubai Marina")),
                Case("۱۲۰۰ يورو", PropertyFilter(maxRent = 1_200), regionId = "paris"),
            ),
        )

    @Test
    fun noFalsePositives() =
        assertCases(
            listOf(
                Case("", PropertyFilter()),
                Case("   ", PropertyFilter()),
                Case("hello there", PropertyFilter()),
                Case("something nice please", PropertyFilter()),
                Case("2", PropertyFilter()),
                Case("under 5", PropertyFilter()),
                Case("no pets", PropertyFilter()),
                Case("unfurnished", PropertyFilter()),
                Case("sans parking", PropertyFilter()),
                Case("pas de balcon", PropertyFilter()),
                Case("غير مفروش", PropertyFilter()),
                Case("max 2 bedrooms", PropertyFilter()),
                Case("colour", PropertyFilter()),
                Case("marine", PropertyFilter()),
                Case("kochi paris", PropertyFilter(query = "Kochi")),
                Case("la", PropertyFilter()),
            ),
        )

    /** The example prompts shown in the app (EN/FR/AR string resources) must be understood in full. */
    @Test
    fun examplePromptsAreFullyUnderstood() {
        val prompts =
            listOf(
                "2 bedroom apartment in La Marsa under 2000 TND with parking",
                "quiet furnished studio in Le Marais under €1500",
                "pet friendly villa with pool in Palm Jumeirah",
                "quiet 2 bedroom near Fort Kochi under 15k with parking",
                "T3 meublé à La Marsa moins de 2 000 DT",
                "studio calme au Marais sous 1500 €",
                "villa avec piscine à Palm Jumeirah, animaux acceptés",
                "maison 3 chambres à Fort Kochi moins de 20k ₹",
                "شقة غرفتين في المرسى أقل من ٢٠٠٠ دينار",
                "فيلا مفروشة مع مسبح في نخلة جميرا",
                "شقة هادئة قرب المترو في دبي مارينا",
                "منزل ٣ غرف في كوتشي أقل من ٢٠ ألف روبية",
            )
        prompts.forEach { prompt ->
            val intent = parse(prompt)
            assertEquals(prompt, emptyList<String>(), intent.unrecognized)
            assertTrue(prompt, intent.meanings.size >= 3)
            assertTrue(prompt, intent.regionId != null)
        }
        val kochi = parse("quiet 2 bedroom near Fort Kochi under 15k with parking").filter
        assertEquals(PropertyFilter(minBedrooms = 2, query = "Fort Kochi", maxRent = 15_000, parkingOnly = true), kochi)
    }

    @Test
    fun spansPointAtTheOriginalTextAndUnknownWordsAreReported() {
        val query = "Quiet 2 bedroom in La Marsa under 2k, spacious, with parking"
        val intent = parse(query)
        val texts = intent.tokens.map { query.substring(it.start, it.end) }
        assertEquals(listOf("Quiet", "2 bedroom", "La Marsa", "under 2k", "parking"), texts)
        assertEquals(texts, intent.tokens.map { it.text })
        assertEquals(listOf("spacious"), intent.unrecognized)
        assertEquals(setOf(LifestyleHint.Quiet), intent.lifestyle)
        assertEquals(null, intent.sort)
    }

    @Test
    fun negatedAndContradictoryPartsAreReportedNotApplied() {
        val intent = parse("furnished, no pets, under 2000 under 1500")
        assertEquals(PropertyFilter(furnishedOnly = true, maxRent = 2_000), intent.filter)
        assertEquals(listOf("no pets", "under 1500"), intent.unrecognized)
    }

    @Test
    fun arabicIndicDigitsKeepTheirSpans() {
        val query = "أقل من ٢٠٠٠"
        val token = parse(query).tokens.single()
        assertEquals(query, token.text)
        assertEquals(0, token.start)
        assertEquals(query.length, token.end)
    }

    @Test
    fun removingAChipDropsItFromTheFilterOnly() {
        val intent = parse("2 bedroom villa in La Marsa under 2000 TND with parking")
        val withoutBudget = intent.without(intent.budget!!)
        assertEquals(null, withoutBudget.filter.maxRent)
        assertEquals("La Marsa", withoutBudget.filter.query)
        assertTrue(withoutBudget.filter.parkingOnly)
        val withoutPlace = intent.without(intent.place!!)
        assertEquals("", withoutPlace.filter.query)
        assertEquals("tunis", withoutPlace.regionId) // still implied by the dinar budget
        assertFalse(intent.without(QueryMeaning.Parking).filter.parkingOnly)
    }

    @Test
    fun lifestyleWordsAreHintsNotFilters() {
        val intent = parse("quiet family home near metro")
        assertEquals(setOf(LifestyleHint.Quiet, LifestyleHint.Family, LifestyleHint.Transport), intent.lifestyle)
        assertEquals(PropertyFilter(), intent.filter)
    }

    @Test
    fun sameSentenceAlwaysGivesTheSameResult() {
        val query = "T2 meublé Ennasr 1200 DT parking"
        assertEquals(parse(query), parse(query))
    }

    @Test
    fun editDistanceStopsAtTheLimit() {
        assertEquals(1, PlaceIndex.editDistance("gamarth", "gammarth", 2))
        assertEquals(3, PlaceIndex.editDistance("abc", "xyzabc", 2))
        assertEquals(0, PlaceIndex.editDistance("marsa", "marsa", 1))
    }

    private companion object {
        val regions: List<RegionPack> by lazy {
            runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).regions() }
        }
    }
}

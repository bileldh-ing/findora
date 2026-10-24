package com.findora.app.widget

import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.match.BestMatchRanking
import com.findora.app.core.domain.match.MatchContext
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.Viewing
import java.time.Instant
import java.util.Locale

/** Which list the widget shows. */
enum class PicksSource {
    /** Best match order (the user has a preference profile). */
    Matches,

    /** The user's saved homes, newest first (no profile yet). */
    Saved,

    /** The Recommended order (no profile, nothing saved). */
    Recommended,
}

/** One home on the widget, texts ready to draw. [thumbnailUrl] is loaded (downscaled) by the widget loader. */
data class PickSummary(
    val propertyId: String,
    val title: String,
    val locality: String,
    val priceText: String,
    val matchText: String?,
    val thumbnailUrl: String?,
    val contentDescription: String,
)

/** What the widget draws, before thumbnails are attached. */
data class PicksWidgetModel(
    val signedIn: Boolean,
    val source: PicksSource,
    val heading: String,
    val picks: List<PickSummary>,
    /** "Next viewing: Georgie House · Tue 14:00", or null when nothing is booked ahead. */
    val viewingLine: String?,
    val emptyText: String,
)

/** Localized texts the builder needs; the Android implementation reads string resources in the chosen language. */
interface PicksWidgetTexts {
    val perMonth: String

    fun heading(source: PicksSource): String

    fun matchPercent(percent: Int): String

    fun nextViewing(
        title: String,
        startAt: Instant,
    ): String

    fun pickDescription(
        title: String,
        locality: String,
        price: String,
        match: String?,
    ): String

    val empty: String
    val signedOut: String
}

/**
 * Pure state builder for the "Findora picks" widget: the top [MAX_PICKS] homes (Best match with a profile, else the
 * user's saved homes, else Recommended), each priced in the display currency, plus the next upcoming viewing.
 */
object PicksWidgetStateBuilder {
    const val MAX_PICKS = 3

    @Suppress("LongParameterList")
    fun build(
        signedIn: Boolean,
        listings: List<Property>,
        favorites: List<Property>,
        match: MatchContext?,
        viewings: List<Viewing>,
        viewingTitles: Map<String, String>,
        display: DisplayPreferences,
        locale: Locale,
        now: Instant,
        texts: PicksWidgetTexts,
    ): PicksWidgetModel {
        if (!signedIn) {
            return PicksWidgetModel(
                signedIn = false,
                source = PicksSource.Recommended,
                heading = texts.heading(PicksSource.Recommended),
                picks = emptyList(),
                viewingLine = null,
                emptyText = texts.signedOut,
            )
        }
        val (source, homes) = pick(listings, favorites, match)
        val scores = match?.let { BestMatchRanking.scores(homes, it) }.orEmpty()
        val picks =
            homes.map { home ->
                val price =
                    PriceFormatter.formatDisplayPerMonth(
                        display.display(home.rent),
                        locale,
                        texts.perMonth,
                    )
                val matchText = scores[home.id]?.let(texts::matchPercent)
                PickSummary(
                    propertyId = home.id,
                    title = home.title,
                    locality = home.locality,
                    priceText = price,
                    matchText = matchText,
                    thumbnailUrl = home.images.firstOrNull()?.url,
                    contentDescription = texts.pickDescription(home.title, home.locality, price, matchText),
                )
            }
        val next = viewings.filter { it.startAt.isAfter(now) }.minByOrNull { it.startAt }
        return PicksWidgetModel(
            signedIn = true,
            source = source,
            heading = texts.heading(source),
            picks = picks,
            viewingLine = next?.let { texts.nextViewing(viewingTitles[it.propertyId] ?: it.propertyId, it.startAt) },
            emptyText = texts.empty,
        )
    }

    private fun pick(
        listings: List<Property>,
        favorites: List<Property>,
        match: MatchContext?,
    ): Pair<PicksSource, List<Property>> =
        when {
            match != null -> {
                val ranked = BestMatchRanking.rank(listings, PropertyFilter(), match).take(MAX_PICKS)
                if (ranked.isNotEmpty()) PicksSource.Matches to ranked else recommended(listings)
            }
            favorites.isNotEmpty() -> PicksSource.Saved to favorites.take(MAX_PICKS)
            else -> recommended(listings)
        }

    /** The Home feed's Recommended order: verified, then rating, then reviews (same as the SQL feed). */
    private fun recommended(listings: List<Property>): Pair<PicksSource, List<Property>> =
        PicksSource.Recommended to
            listings
                .sortedWith(
                    compareByDescending<Property> { it.verified }
                        .thenByDescending { it.rating }
                        .thenByDescending { it.reviewsCount }
                        .thenBy { it.id },
                ).take(MAX_PICKS)
}

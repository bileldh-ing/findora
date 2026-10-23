package com.findora.app.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.findora.app.R
import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.match.ObserveMatchContext
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.locale.LocalizedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

/** Everything the widget draws: the model plus thumbnails (already downscaled) by listing id. */
data class PicksWidgetUi(
    val model: PicksWidgetModel,
    val thumbnails: Map<String, Bitmap>,
    val isRtl: Boolean,
    val openAppLabel: String,
)

/**
 * Gathers the widget's data for the signed-in user (a widget never shows a signed-out user's homes), in the
 * language and display currency chosen in Settings. Thumbnails go through the app's Coil pipeline (disk cache and
 * bundled offline photos), downscaled to [THUMB_PX] and software bitmaps, so the RemoteViews stay small; a photo
 * that fails or takes too long simply falls back to the icon.
 */
class PicksWidgetLoader
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val currentRegion: CurrentRegion,
        private val properties: PropertyRepository,
        private val favorites: FavoritesRepository,
        private val viewings: ViewingRepository,
        private val matchContext: ObserveMatchContext,
        private val displayPreferences: ObserveDisplayPreferences,
        private val settings: SettingsRepository,
        private val localized: LocalizedContext,
        private val imageLoader: Provider<ImageLoader>,
        private val clock: AppClock,
        private val zone: ZoneId,
    ) {
        suspend fun load(): PicksWidgetUi {
            val prefs = settings.settings.first()
            val context = localized.resolve(prefs.language)
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val texts = AndroidPicksWidgetTexts(context, locale, zone)
            val session = auth.restoreSession()
            val model =
                if (session == null) {
                    PicksWidgetStateBuilder.build(
                        signedIn = false,
                        listings = emptyList(),
                        favorites = emptyList(),
                        match = null,
                        viewings = emptyList(),
                        viewingTitles = emptyMap(),
                        display = displayPreferences().first(),
                        locale = locale,
                        now = clock.now(),
                        texts = texts,
                    )
                } else {
                    val region = currentRegion.region.first().id
                    val listings = properties.observeProperties(region).first()
                    val upcoming = viewings.observeViewings(session.userId).first()
                    val titles =
                        upcoming.associate { viewing ->
                            viewing.propertyId to
                                (properties.observeProperty(viewing.propertyId).first()?.title ?: viewing.propertyId)
                        }
                    PicksWidgetStateBuilder.build(
                        signedIn = true,
                        listings = listings,
                        favorites = favorites.observeFavorites(session.userId).first(),
                        match = matchContext().first(),
                        viewings = upcoming,
                        viewingTitles = titles,
                        display = displayPreferences().first(),
                        locale = locale,
                        now = clock.now(),
                        texts = texts,
                    )
                }
            val thumbnails =
                model.picks
                    .mapNotNull { pick -> pick.thumbnailUrl?.let { url -> thumbnail(context, url)?.let { pick.propertyId to it } } }
                    .toMap()
            return PicksWidgetUi(
                model = model,
                thumbnails = thumbnails,
                isRtl = PriceFormatter.isRtl(locale),
                openAppLabel = context.getString(R.string.widget_open_app),
            )
        }

        private suspend fun thumbnail(
            context: Context,
            url: String,
        ): Bitmap? =
            try {
                withTimeoutOrNull(THUMB_TIMEOUT_MILLIS) {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(THUMB_PX, THUMB_PX)
                            .allowHardware(false)
                            .build()
                    (imageLoader.get().execute(request) as? SuccessResult)?.image?.toBitmap()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                // A broken photo must never break the widget: the icon is shown instead.
                null
            }

        companion object {
            /** About 64 dp at xxhdpi: sharp in a 56 dp slot, ~50 KB per bitmap. */
            const val THUMB_PX = 168
            private const val THUMB_TIMEOUT_MILLIS = 5_000L
        }
    }

/** [PicksWidgetTexts] from string resources in the Settings language. */
class AndroidPicksWidgetTexts(
    private val context: Context,
    private val locale: Locale,
    private val zone: ZoneId,
) : PicksWidgetTexts {
    override val perMonth: String = context.getString(R.string.per_month)
    override val empty: String = context.getString(R.string.widget_empty)
    override val signedOut: String = context.getString(R.string.widget_signed_out)

    override fun heading(source: PicksSource): String =
        context.getString(
            when (source) {
                PicksSource.Matches -> R.string.widget_heading_matches
                PicksSource.Saved -> R.string.widget_heading_saved
                PicksSource.Recommended -> R.string.widget_heading_recommended
            },
        )

    override fun matchPercent(percent: Int): String = context.getString(R.string.widget_match, percent)

    override fun nextViewing(
        title: String,
        startAt: Instant,
    ): String {
        val time =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .format(startAt.atZone(zone))
        return context.getString(R.string.widget_next_viewing, title, time)
    }

    override fun pickDescription(
        title: String,
        locality: String,
        price: String,
        match: String?,
    ): String =
        listOfNotNull(title, locality, price, match).joinToString(", ")
}

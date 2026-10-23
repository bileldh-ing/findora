package com.findora.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.findora.app.MainActivity
import com.findora.app.R
import com.findora.app.core.domain.repository.HomeWidgetRefresher
import com.findora.app.navigation.PropertyDeepLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Hilt access for the widget, which Android instantiates itself. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PicksWidgetEntryPoint {
    fun picksWidgetLoader(): PicksWidgetLoader

    fun homeWidgetRefresher(): HomeWidgetRefresher
}

/**
 * "Findora picks": the top three homes for the signed-in user (best matches, else saved homes, else recommended)
 * with photo, price in the display currency and match score, plus the next upcoming viewing. A tap on a home opens
 * its detail page through `findora://property/{id}` (and the session guard); a tap elsewhere opens the app.
 */
class FindoraPicksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val ui =
            EntryPointAccessors
                .fromApplication(context, PicksWidgetEntryPoint::class.java)
                .picksWidgetLoader()
                .load()
        provideContent { PicksWidgetContent(ui) }
    }
}

class FindoraPicksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FindoraPicksWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        EntryPointAccessors
            .fromApplication(context, PicksWidgetEntryPoint::class.java)
            .homeWidgetRefresher()
            .ensureScheduled()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManagerHomeWidgetRefresher.cancel(context)
    }
}

/** Light and dark colours (the launcher picks by system theme). */
internal object WidgetColors {
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF161616))
    val surface = ColorProvider(day = Color(0xFFF2F2F2), night = Color(0xFF262626))
    val onBackground = ColorProvider(day = Color(0xFF161616), night = Color(0xFFF5F5F5))
    val muted = ColorProvider(day = Color(0xFF6B6B6B), night = Color(0xFFB0B0B0))
    val accent = ColorProvider(day = Color(0xFF1E5EFF), night = Color(0xFF7FA6FF))
}

private val Thumb = 56.dp

@Composable
fun PicksWidgetContent(
    ui: PicksWidgetUi,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val model = ui.model
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(WidgetColors.background)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(openAppIntent(context)))
                .semantics { contentDescription = ui.openAppLabel },
    ) {
        Text(
            text = model.heading,
            style = TextStyle(color = WidgetColors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        if (model.picks.isEmpty()) {
            Text(text = model.emptyText, style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp), maxLines = 3)
        } else {
            model.picks.forEach { pick ->
                PickRow(pick, ui.thumbnails[pick.propertyId], context)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
        model.viewingLine?.let { line ->
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_viewing),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(WidgetColors.accent),
                    modifier = GlanceModifier.size(16.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(text = line, style = TextStyle(color = WidgetColors.onBackground, fontSize = 12.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PickRow(
    pick: PickSummary,
    thumbnail: android.graphics.Bitmap?,
    context: Context,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .background(WidgetColors.surface)
                .cornerRadius(16.dp)
                .padding(6.dp)
                .clickable(actionStartActivity(openPropertyIntent(context, pick.propertyId)))
                .semantics { contentDescription = pick.contentDescription },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(Thumb).cornerRadius(12.dp).background(WidgetColors.background),
        ) {
            if (thumbnail != null) {
                Image(
                    provider = ImageProvider(thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.size(Thumb).cornerRadius(12.dp),
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_home),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(WidgetColors.muted),
                    modifier = GlanceModifier.size(28.dp),
                )
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = pick.title,
                style = TextStyle(color = WidgetColors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(text = pick.locality, style = TextStyle(color = WidgetColors.muted, fontSize = 12.sp), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pick.priceText,
                    style =
                        TextStyle(color = WidgetColors.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                pick.matchText?.let { match ->
                    Spacer(GlanceModifier.width(8.dp))
                    Text(text = match, style = TextStyle(color = WidgetColors.accent, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}

internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

internal fun openPropertyIntent(
    context: Context,
    propertyId: String,
): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(PropertyDeepLink.uriFor(propertyId)))
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

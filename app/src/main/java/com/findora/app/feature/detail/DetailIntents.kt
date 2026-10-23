package com.findora.app.feature.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.findora.app.R
import com.findora.app.core.domain.model.GeoPoint
import java.util.Locale

/**
 * Hands a [DetailUiEffect.External] to another app: the share sheet, the dialer (ACTION_DIAL, no call permission),
 * an e-mail app (ACTION_SENDTO mailto:) or a maps app (geo: URI, no API key). Returns false when no
 * installed app can handle it, so the screen can say so instead of failing silently.
 */
internal fun Context.launchDetailEffect(effect: DetailUiEffect.External): Boolean {
    val intent =
        when (effect) {
            is DetailUiEffect.Share -> shareIntent(effect)
            is DetailUiEffect.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", effect.phone, null))
            is DetailUiEffect.Email -> emailIntent(effect)
            is DetailUiEffect.OpenMap -> Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(effect.point, effect.label)))
        }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun Context.shareIntent(effect: DetailUiEffect.Share): Intent {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, effect.title)
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.detail_share_text, effect.title, effect.link))
    return Intent.createChooser(send, getString(R.string.detail_share_chooser))
}

private fun Context.emailIntent(effect: DetailUiEffect.Email): Intent =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(effect.address))
        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.detail_email_subject, effect.listingTitle))
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.detail_email_body, effect.listingTitle, effect.link))

/** `geo:lat,lng?q=lat,lng(label)`: centres any maps app on the home and drops a labelled pin. */
internal fun geoUri(
    point: GeoPoint,
    label: String,
): String {
    val coordinates = formatCoordinates(point)
    return "geo:$coordinates?q=$coordinates(${Uri.encode(label)})"
}

/** Five decimals (about one metre), always with a dot so the value is the same in every language. */
internal fun formatCoordinates(point: GeoPoint): String =
    String.format(Locale.ROOT, "%.5f,%.5f", point.latitude, point.longitude)

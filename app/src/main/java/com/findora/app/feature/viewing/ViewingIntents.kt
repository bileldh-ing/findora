package com.findora.app.feature.viewing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.findora.app.R

/**
 * Opens the calendar app's "new event" screen pre-filled with the viewing (CalendarContract ACTION_INSERT, so no
 * calendar permission is needed). Returns false when no calendar app is installed.
 */
internal fun Context.launchAddToCalendar(effect: ScheduleUiEffect.AddToCalendar): Boolean {
    val description =
        listOf(effect.note.trim(), getString(R.string.calendar_event_description, effect.link))
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    val intent =
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, effect.begin.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, effect.end.toEpochMilli())
            .putExtra(CalendarContract.Events.TITLE, getString(R.string.calendar_event_title, effect.propertyTitle))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, effect.location)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

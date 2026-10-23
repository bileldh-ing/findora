package com.findora.app.core.domain.location

import com.findora.app.core.domain.model.GeoPoint

/**
 * The device's approximate position for the map's "My location" button. Implementations never request
 * updates or wake the GPS: they return the platform's last known fix, so the feature costs no battery.
 */
interface DeviceLocationSource {
    /** False when the device has no location provider at all; the button is then hidden. */
    val isSupported: Boolean

    /** The last known coarse fix, or null when permission is missing or no fix exists yet. */
    suspend fun lastKnownLocation(): GeoPoint?
}

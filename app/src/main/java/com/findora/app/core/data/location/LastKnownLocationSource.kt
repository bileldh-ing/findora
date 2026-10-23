package com.findora.app.core.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.location.DeviceLocationSource
import com.findora.app.core.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [DeviceLocationSource] on the framework [LocationManager] (no Google Play services dependency): the newest
 * last known fix among the enabled providers. Only ACCESS_COARSE_LOCATION is ever requested, so the fix is
 * approximate by design, which is all a "show homes near me" map needs.
 */
class LastKnownLocationSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : DeviceLocationSource {
        private val manager: LocationManager? = ContextCompat.getSystemService(context, LocationManager::class.java)

        override val isSupported: Boolean
            get() =
                manager != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

        override suspend fun lastKnownLocation(): GeoPoint? =
            withContext(io) {
                val locationManager = manager ?: return@withContext null
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted) return@withContext null
                newestFix(locationManager)?.let { GeoPoint(it.latitude, it.longitude) }
            }

        private fun newestFix(locationManager: LocationManager): Location? =
            try {
                locationManager
                    .getProviders(true)
                    .mapNotNull { provider -> lastKnown(locationManager, provider) }
                    .maxByOrNull { it.time }
            } catch (_: SecurityException) {
                // Permission revoked between the check and the call.
                null
            }

        private fun lastKnown(
            locationManager: LocationManager,
            provider: String,
        ): Location? =
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                // Some providers (GPS before Android 12) need the fine permission, which Findora never asks for.
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }

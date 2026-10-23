package com.findora.app.feature.owner.wizard

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.findora.app.R
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.feature.locations.map.createMapView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * An OpenStreetMap map (the same osmdroid setup as the Locations tab) where a tap places the listing's pin. The map is
 * described to TalkBack, whose users place the pin with the locality chips and "Place the pin at …" instead.
 */
@Composable
fun PinPickerMap(
    center: GeoPoint,
    zoom: Double,
    pin: GeoPoint?,
    onPlacePin: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDark = FindoraTheme.colors.isDark
    val currentOnPinPlaced by rememberUpdatedState(onPlacePin)
    val mapView = remember { createMapView(context) }
    val marker = remember(mapView) { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    val description = stringResource(R.string.owner_map_cd)

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        val tapOverlay = TapOverlay { point -> currentOnPinPlaced(GeoPoint(point.latitude, point.longitude)) }
        mapView.overlays.add(tapOverlay)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.remove(tapOverlay)
            mapView.onDetach()
        }
    }
    LaunchedEffect(mapView, center, zoom) {
        mapView.controller.setZoom(zoom)
        mapView.controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
    }
    LaunchedEffect(mapView, pin) {
        mapView.overlays.remove(marker)
        if (pin != null) {
            marker.position = OsmGeoPoint(pin.latitude, pin.longitude)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }
    LaunchedEffect(mapView, isDark) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (isDark) TilesOverlay.INVERT_COLORS else null)
    }
    AndroidView(
        factory = { mapView },
        modifier =
            modifier
                .clip(FindoraTheme.shapes.card)
                .semantics { contentDescription = description }
                .testTag("pinPickerMap"),
    )
}

/** Reports single taps on the map (drags and pinches still pan and zoom). */
private class TapOverlay(
    private val onTap: (OsmGeoPoint) -> Unit,
) : Overlay() {
    override fun onSingleTapConfirmed(
        event: MotionEvent,
        mapView: MapView,
    ): Boolean {
        val point = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as OsmGeoPoint
        onTap(point)
        return true
    }
}

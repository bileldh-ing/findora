package com.findora.app.feature.locations.map

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.findora.app.BuildConfig
import com.findora.app.R
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.map.MapAreas
import com.findora.app.core.domain.map.MapCluster
import com.findora.app.core.domain.map.MapViewport
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.image.CacheLocations
import com.findora.app.core.ui.LocalDisplayPreferences
import com.findora.app.feature.locations.CameraRequest
import com.findora.app.feature.locations.MapUiEvent
import com.findora.app.feature.locations.MapUiState
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.io.File
import java.util.Locale
import org.osmdroid.util.GeoPoint as OsmGeoPoint

private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0
private const val POINT_ZOOM = 17.0
private const val CAMERA_SETTLE_MILLIS = 250L
private const val OFFLINE_GRACE_MILLIS = 4_000L
private const val FIT_PADDING_DP = 64
private const val MIN_FIT_SPAN = 1e-4
private const val TILE_CACHE_MAX_BYTES = 64L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 48L * 1024 * 1024
private const val LRI = '\u2066'
private const val PDI = '\u2069'

/**
 * The OpenStreetMap map (osmdroid) behind the Locations tab, wrapped for Compose:
 * - lifecycle: the [MapView] follows the host's onResume/onPause and is detached when it leaves composition;
 * - camera: applies each new [CameraRequest] once and reports settled viewports ([MapUiEvent.CameraMoved]),
 *   flagged as user-driven when a touch started the move (that is what shows "Search this area");
 * - markers: price pills and cluster bubbles drawn from [MapUiState.clusters], the selection highlighted;
 * - offline: reports [MapUiEvent.TilesStatus] when no tile could be loaded for a few seconds;
 * - dark theme: inverted tiles; TalkBack: one summary plus a custom action per marker.
 */
@Composable
fun OsmListingsMap(
    state: MapUiState,
    onEvent: (MapUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val display = LocalDisplayPreferences.current
    val colors = FindoraTheme.colors
    val currentOnEvent by rememberUpdatedState(onEvent)
    val tracker = remember { MapTracker() }
    val mapView = remember { createMapView(context) }
    val palette =
        MarkerPalette(
            pill = colors.background.toArgb(),
            pillText = colors.onBackground.toArgb(),
            outline = colors.outline.toArgb(),
            selected = colors.accent.toArgb(),
            selectedText = colors.onPrimaryAction.toArgb(),
            cluster = colors.primaryAction.toArgb(),
            clusterText = colors.onPrimaryAction.toArgb(),
        )
    val bitmaps = remember(palette, density) { MarkerBitmaps(density, palette) }
    val markerLayer = remember(mapView) { FolderOverlay().also { mapView.overlays.add(it) } }

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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    DisposableEffect(mapView) {
        val dispose = mapView.installCallbacks(tracker) { currentOnEvent(it) }
        onDispose(dispose)
    }

    LaunchedEffect(mapView, state.camera?.id) {
        val request = state.camera ?: return@LaunchedEffect
        tracker.userGesture = false
        mapView.apply(request, (FIT_PADDING_DP * density).toInt())
    }

    LaunchedEffect(mapView, colors.isDark) {
        val tiles = mapView.overlayManager.tilesOverlay
        tiles.setColorFilter(if (colors.isDark) TilesOverlay.INVERT_COLORS else null)
        tiles.loadingBackgroundColor = colors.background.toArgb()
        tiles.loadingLineColor = colors.outline.toArgb()
        mapView.invalidate()
    }

    LaunchedEffect(mapView, bitmaps, state.clusters, state.selectedId, state.myLocation, locale, display) {
        markerLayer.render(mapView, state, bitmaps, locale, display) { currentOnEvent(it) }
        mapView.invalidate()
    }

    val description = pluralStringResource(R.plurals.map_description, state.listings.size, state.listings.size)
    val actions = markerActions(state.clusters, locale, display) { currentOnEvent(MapUiEvent.SelectCluster(it)) }
    AndroidView(
        factory = { mapView },
        modifier =
            modifier
                .semantics {
                    contentDescription = description
                    customActions = actions
                }.testTag("osmMap"),
    )
}

/** One TalkBack action per marker: "Show <listing>, <price>" or "<n> homes here, zoom in". */
@Composable
private fun markerActions(
    clusters: List<MapCluster<Property>>,
    locale: Locale,
    display: DisplayPreferences,
    onSelect: (String) -> Unit,
): List<CustomAccessibilityAction> {
    val resources = LocalContext.current.resources
    val perMonth = stringResource(R.string.per_month)
    return clusters.map { cluster ->
        val label =
            if (cluster.isSingle) {
                val home = cluster.items.first()
                resources.getString(
                    R.string.map_marker_action,
                    home.title,
                    PriceFormatter.formatDisplayPerMonth(display.display(home.rent), locale, perMonth),
                )
            } else {
                resources.getQuantityString(R.plurals.map_cluster_cd, cluster.size, cluster.size)
            }
        CustomAccessibilityAction(label) {
            onSelect(cluster.key)
            true
        }
    }
}

/**
 * Wires the map callbacks: touch tracking, settled-viewport reports and the offline detector. Returns the
 * cleanup to run when the map leaves composition.
 */
private fun MapView.installCallbacks(
    tracker: MapTracker,
    onEvent: (MapUiEvent) -> Unit,
): () -> Unit {
    val mapView = this
    overlays.add(GestureOverlay { tracker.userGesture = true })
    val report = { onEvent(MapUiEvent.CameraMoved(mapView.viewport(), tracker.userGesture)) }
    addMapListener(
        DelayedMapListener(
            object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean = true.also { report() }

                override fun onZoom(event: ZoomEvent?): Boolean = true.also { report() }
            },
            CAMERA_SETTLE_MILLIS,
        ),
    )
    addOnFirstLayoutListener { _, _, _, _, _ -> report() }
    val offline =
        Runnable {
            tracker.offlinePending = false
            tracker.reportTiles(available = false, onEvent)
        }
    val tileStates = overlayManager.tilesOverlay.tileStates
    tileStates.runAfters.add(
        Runnable {
            when {
                tileStates.total == 0 -> Unit
                tileStates.notFound < tileStates.total -> {
                    removeCallbacks(offline)
                    tracker.offlinePending = false
                    tracker.reportTiles(available = true, onEvent)
                }
                // Tiles still missing after a grace period (not just downloading): the map is offline.
                !tracker.offlinePending -> {
                    tracker.offlinePending = true
                    postDelayed(offline, OFFLINE_GRACE_MILLIS)
                }
            }
        },
    )
    return { removeCallbacks(offline) }
}

/** Redraws the markers: the location dot, then clusters and pills, the selected pill last (on top). */
private fun FolderOverlay.render(
    mapView: MapView,
    state: MapUiState,
    bitmaps: MarkerBitmaps,
    locale: Locale,
    display: DisplayPreferences,
    onEvent: (MapUiEvent) -> Unit,
) {
    items.clear()
    state.myLocation?.let { point ->
        add(
            Marker(mapView).apply {
                position = point.toOsm()
                icon = BitmapDrawable(mapView.resources, bitmaps.locationDot())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null as MarkerInfoWindow?)
                setOnMarkerClickListener { _, _ -> true }
            },
        )
    }
    state.clusters
        .sortedBy { cluster -> cluster.isSingle && cluster.key == state.selectedId }
        .forEach { cluster ->
            val selected = cluster.isSingle && cluster.key == state.selectedId
            add(
                clusterMarker(mapView, cluster, selected, bitmaps, locale, display) {
                    onEvent(
                        if (cluster.isSingle) {
                            MapUiEvent.SelectMarker(
                                cluster.key,
                            )
                        } else {
                            MapUiEvent.SelectCluster(cluster.key)
                        },
                    )
                },
            )
        }
}

private fun clusterMarker(
    mapView: MapView,
    cluster: MapCluster<Property>,
    selected: Boolean,
    bitmaps: MarkerBitmaps,
    locale: Locale,
    display: DisplayPreferences,
    onClick: () -> Unit,
): Marker =
    Marker(mapView).apply {
        position = cluster.center.toOsm()
        if (cluster.isSingle) {
            // The bitmap is one left-to-right run, so the RTL isolation marks are not needed (or drawable).
            val rent = display.display(cluster.items.first().rent)
            val price = PriceFormatter.formatDisplay(rent, locale).filterNot { it == LRI || it == PDI }
            icon = BitmapDrawable(mapView.resources, bitmaps.pricePill(price, selected))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = cluster.items.first().title
        } else {
            icon = BitmapDrawable(mapView.resources, bitmaps.clusterBubble(cluster.size))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        setInfoWindow(null as MarkerInfoWindow?)
        setOnMarkerClickListener { _, _ ->
            onClick()
            true
        }
    }

/** Configures osmdroid once (user agent = application id, app-private tile cache) and builds the view. */
internal fun createMapView(context: Context): MapView {
    val configuration = Configuration.getInstance()
    configuration.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    configuration.userAgentValue = BuildConfig.APPLICATION_ID
    val base = File(context.cacheDir, CacheLocations.MAP_BASE)
    configuration.osmdroidBasePath = base
    configuration.osmdroidTileCache = File(base, CacheLocations.MAP_TILES)
    configuration.tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
    configuration.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setMinZoomLevel(MIN_ZOOM)
        setMaxZoomLevel(MAX_ZOOM)
        isTilesScaledToDpi = true
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        // The Compose node above carries the description and per-marker actions for TalkBack.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}

private fun MapView.viewport(): MapViewport {
    val box = boundingBox
    return MapViewport(
        bounds = MapAreas.boundsOf(box.latNorth, box.lonEast, box.latSouth, box.lonWest),
        center = GeoPoint(mapCenter.latitude, mapCenter.longitude),
        zoom = zoomLevelDouble,
    )
}

private fun MapView.apply(
    request: CameraRequest,
    paddingPx: Int,
) {
    when (request) {
        is CameraRequest.Center ->
            if (request.zoom == null) {
                controller.animateTo(request.point.toOsm())
            } else {
                controller.setZoom(request.zoom)
                controller.setCenter(request.point.toOsm())
            }
        is CameraRequest.Fit -> {
            val bounds = request.bounds
            if (bounds.north - bounds.south < MIN_FIT_SPAN && bounds.east - bounds.west < MIN_FIT_SPAN) {
                controller.animateTo(GeoPoint(bounds.north, bounds.east).toOsm(), POINT_ZOOM, null)
            } else {
                zoomToBoundingBox(BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west), true, paddingPx)
            }
        }
    }
}

private fun GeoPoint.toOsm(): OsmGeoPoint = OsmGeoPoint(latitude, longitude)

/** Mutable bookkeeping shared by the map callbacks (main thread only). */
private class MapTracker {
    var userGesture = false
    var offlinePending = false
    private var lastTilesAvailable: Boolean? = null

    fun reportTiles(
        available: Boolean,
        onEvent: (MapUiEvent) -> Unit,
    ) {
        if (lastTilesAvailable == available) return
        lastTilesAvailable = available
        onEvent(MapUiEvent.TilesStatus(available))
    }
}

/** Notes that a finger touched the map, so the next settled viewport counts as moved by the user. */
private class GestureOverlay(
    private val onGesture: () -> Unit,
) : Overlay() {
    override fun onTouchEvent(
        event: MotionEvent,
        mapView: MapView,
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onGesture()
        return false
    }
}

# ADR 0005: OpenStreetMap through osmdroid for the Locations map

Status: accepted (Phase 6A)

## Context
Phase 6 turns the Locations tab into a map: price-pill markers that cluster when zoomed out, a card carousel
kept in sync with them, "Search this area", the shared Home filter, a region switcher, "My location", dark
styling and an accessible list alternative. Constraints from the project: no API keys and no billing account
(recruiters clone and run it), no Google Play services dependency, offline-first (the listings must still be
browsable without network), every rule unit-testable, and a small APK.

## Options considered
- **Google Maps Compose**: best Compose API, but needs an API key, a billing-enabled Cloud project and Play
  services on the device. Rejected for this project (it could be added later behind the same screen state).
- **MapLibre Native** with a keyless style (OpenFreeMap, demotiles): vector tiles and smooth rotation, but
  native libraries add several MB per ABI, the free keyless styles are community services without an SLA,
  and Robolectric cannot load the native renderer.
- **osmdroid** with the standard OpenStreetMap raster tiles: pure Java (about 0.9 MB AAR, no native code),
  Apache 2.0, works on API 26+, a built-in SQLite tile cache, overlays for markers and a tile colour filter.
  The project is in maintenance mode, which is acceptable for a stable raster map.

## Decision
Use **osmdroid 6.1.20** with `TileSourceFactory.MAPNIK` (tile.openstreetmap.org), wrapped in a Compose
`AndroidView` (`feature/locations/map/OsmListingsMap.kt`).

- **Tile usage policy**: the HTTP user agent is the application id (`BuildConfig.APPLICATION_ID`), the
  "© OpenStreetMap contributors" attribution is always visible over the map, and tiles are cached on disk
  (osmdroid's SQLite cache in the app's private `cacheDir/osmdroid`, capped at 64 MB, trimmed to 48 MB) so
  panning back and forth does not re-download. No bulk or offline pre-download is done (forbidden by the
  policy). A production release with real traffic should move to a commercial or self-hosted tile provider;
  only the tile source line changes.
- **Lifecycle**: the `MapView` is created once per composition, follows the host lifecycle
  (`onResume`/`onPause` through a `LifecycleEventObserver`) and is `onDetach`ed when it leaves composition.
- **Markers** are bitmaps drawn with `Canvas` from the theme colours (price pill formatted by
  `PriceFormatter`, the selected one in the accent colour and drawn on top; cluster bubbles with a count).
- **Clustering is ours, not a library's**: `GridClusterer` (pure Kotlin, `core/domain/map`) groups listings by
  Web Mercator pixel cells at the current zoom, so it is deterministic and covered by JVM tests, and the
  ViewModel owns which clusters exist (TalkBack actions and the carousel use the same list).
- **Camera** instructions are state (`CameraRequest` with an id), applied once by the view; the view reports
  settled viewports (debounced 250 ms) and whether a touch started the move, which is what offers
  "Search this area". Filtering by the visible box is `MapAreas.visibleListings` (pure Kotlin, tested).
- **Offline**: listings come from Room, so the carousel and list keep working; when no tile can be loaded for
  4 seconds a banner explains the blank map and offers the list view.
- **Dark theme**: osmdroid's `TilesOverlay.INVERT_COLORS` filter plus theme-coloured loading tiles.
- **Accessibility**: the map node carries a summary and one custom action per marker; the list view is the
  full alternative. In tests the map composable is replaced by a fake slot (Robolectric does not draw tiles).
- **My location** uses the framework `LocationManager` last known fix with ACCESS_COARSE_LOCATION only, asked
  when the button is tapped; no Play services, no background location, hidden when the device has no provider.

## Consequences
- No keys, no native code, works offline for listings, fully testable domain rules.
- Raster tiles look less crisp than vector maps when zooming between levels, and dark mode is an inverted
  light style rather than a designed dark style.
- The OSM Foundation's tile servers are for light use; heavy production traffic needs another provider.

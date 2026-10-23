package com.findora.app.core.image

/** App-private cache directories (under `cacheDir`), shared by the code that fills them and by "Clear cache". */
object CacheLocations {
    const val IMAGES = ImageLoaderConfig.DISK_CACHE_DIRECTORY
    const val MAP_BASE = "osmdroid"
    const val MAP_TILES = "tiles"
}

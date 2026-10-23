package com.findora.app.core.image

import android.content.Context
import coil3.ImageLoader
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.repository.CacheCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Empties Coil's memory and disk caches and osmdroid's tile cache. Listing photos and tiles download again on
 * demand, and the bundled offline photo pack (assets) is never touched.
 */
@Singleton
class AndroidCacheCleaner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: Provider<ImageLoader>,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : CacheCleaner {
        override suspend fun clear(): Long =
            withContext(io) {
                val tiles = File(File(context.cacheDir, CacheLocations.MAP_BASE), CacheLocations.MAP_TILES)
                val images = File(context.cacheDir, CacheLocations.IMAGES)
                val before = sizeOf(tiles) + sizeOf(images)
                val loader = imageLoader.get()
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                tiles.deleteRecursively()
                (before - sizeOf(tiles) - sizeOf(images)).coerceAtLeast(0L)
            }

        private fun sizeOf(directory: File): Long = directory.walkBottomUp().filter(File::isFile).sumOf(File::length)
    }

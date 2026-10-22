package com.findora.app.core.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/** Image pipeline settings, kept together so they are documented and testable. */
object ImageLoaderConfig {
    const val DISK_CACHE_BYTES = 250L * 1024 * 1024
    const val MEMORY_CACHE_PERCENT = 0.25
    const val CROSSFADE_MILLIS = 250
    const val DISK_CACHE_DIRECTORY = "image_cache"
}

/** Coil 3 loader: 250 MB disk cache, 25% memory cache, crossfade, OkHttp, offline-pack fallback. */
fun newFindoraImageLoader(
    context: Context,
    okHttpClient: OkHttpClient,
): ImageLoader =
    ImageLoader
        .Builder(context)
        .memoryCache {
            MemoryCache
                .Builder()
                .maxSizePercent(context, ImageLoaderConfig.MEMORY_CACHE_PERCENT)
                .build()
        }.diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve(ImageLoaderConfig.DISK_CACHE_DIRECTORY).toOkioPath())
                .maxSizeBytes(ImageLoaderConfig.DISK_CACHE_BYTES)
                .build()
        }.crossfade(ImageLoaderConfig.CROSSFADE_MILLIS)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            add(OfflineFallbackInterceptor())
        }.build()

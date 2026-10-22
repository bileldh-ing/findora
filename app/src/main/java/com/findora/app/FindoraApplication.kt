package com.findora.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class FindoraApplication :
    Application(),
    SingletonImageLoader.Factory {
    /** Provider keeps the image pipeline lazy: nothing is built until the first image request. */
    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}

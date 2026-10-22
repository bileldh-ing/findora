package com.findora.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class FindoraApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    /** Provider keeps the image pipeline lazy: nothing is built until the first image request. */
    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    /** Lets WorkManager build @HiltWorker workers (viewing reminders) with injected dependencies. */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

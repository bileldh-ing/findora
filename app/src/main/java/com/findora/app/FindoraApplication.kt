package com.findora.app

import android.app.Application
import android.os.StrictMode
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

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
    }

    /**
     * Debug builds log every main-thread disk/network access and leaked closeable, cursor or registration
     * (penaltyLog, never a crash), so regressions show up in Logcat while developing. Release builds skip it.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

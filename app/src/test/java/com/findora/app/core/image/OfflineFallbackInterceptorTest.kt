package com.findora.app.core.image

import android.app.Application
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OfflineFallbackInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val interceptor = OfflineFallbackInterceptor()
    private val remoteUrl = "https://images.pexels.com/photos/1/pexels-photo-1.jpeg"

    private fun request(fallback: String?) =
        ImageRequest
            .Builder(context)
            .data(remoteUrl)
            .offlineFallback(fallback)
            .build()

    private fun error(request: ImageRequest) =
        ErrorResult(image = null, request = request, throwable = IOException("offline"))

    @Test
    fun successIsReturnedUntouched() =
        runTest {
            val request = request("offline/pexels_1.webp")
            val success = SuccessResult(ColorDrawable(0).asImage(), request, DataSource.NETWORK)
            val chain =
                mockk<Interceptor.Chain> {
                    every { this@mockk.request } returns request
                    coEvery { proceed() } returns success
                }
            assertSame(success, interceptor.intercept(chain))
        }

    @Test
    fun failureWithoutFallbackIsReturned() =
        runTest {
            val request = request(null)
            val failure = error(request)
            val chain =
                mockk<Interceptor.Chain> {
                    every { this@mockk.request } returns request
                    coEvery { proceed() } returns failure
                }
            assertSame(failure, interceptor.intercept(chain))
        }

    @Test
    fun failureRetriesOnceWithTheBundledAsset() =
        runTest {
            val request = request("offline/pexels_1.webp")
            val retried = slot<ImageRequest>()
            val fallbackResult = SuccessResult(ColorDrawable(0).asImage(), request, DataSource.DISK)
            val fallbackChain = mockk<Interceptor.Chain> { coEvery { proceed() } returns fallbackResult }
            val chain =
                mockk<Interceptor.Chain> {
                    every { this@mockk.request } returns request
                    coEvery { proceed() } returns error(request)
                    every { withRequest(capture(retried)) } returns fallbackChain
                }
            assertSame(fallbackResult, interceptor.intercept(chain))
            assertEquals("file:///android_asset/offline/pexels_1.webp", retried.captured.data)
        }

    @Test
    fun assetUriIsNormalised() {
        assertEquals("file:///android_asset/offline/a.webp", assetUri("/offline/a.webp"))
    }

    @Test
    fun loaderUsesTheDocumentedCacheSizes() {
        val loader = newFindoraImageLoader(context, okhttp3.OkHttpClient())
        assertEquals(ImageLoaderConfig.DISK_CACHE_BYTES, loader.diskCache?.maxSize)
        assertEquals(250L * 1024 * 1024, ImageLoaderConfig.DISK_CACHE_BYTES)
        loader.shutdown()
    }
}

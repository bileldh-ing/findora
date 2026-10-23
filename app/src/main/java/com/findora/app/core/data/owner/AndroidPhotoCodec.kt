package com.findora.app.core.data.owner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.findora.app.core.domain.owner.PhotoSizing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/**
 * Decodes a picked image in two passes: bounds first, then subsampled ([PhotoSizing.sampleSize]) so a 50-megapixel
 * photo never needs 200 MB, then rotated upright from its EXIF orientation and scaled to [PhotoSizing.targetSize].
 */
class AndroidPhotoCodec
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PhotoCodec {
        override fun decodeScaled(
            uri: String,
            maxEdge: Int,
        ): ScaledPhoto? =
            try {
                decode(Uri.parse(uri), maxEdge)
            } catch (ignored: IOException) {
                null
            } catch (ignored: SecurityException) {
                null
            } catch (ignored: OutOfMemoryError) {
                null
            }

        private fun decode(
            uri: Uri,
            maxEdge: Int,
        ): ScaledPhoto? {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sampled =
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    val options =
                        BitmapFactory.Options().apply {
                            inSampleSize = PhotoSizing.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
                        }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                } else {
                    null
                }
            return sampled?.let { bitmap ->
                val orientation =
                    resolver.openInputStream(uri)?.use {
                        ExifInterface(
                            it,
                        ).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } ?: ExifInterface.ORIENTATION_NORMAL
                BitmapPhoto(bitmap.rotated(orientation).fitted(maxEdge))
            }
        }

        private fun Bitmap.fitted(maxEdge: Int): Bitmap {
            val (targetWidth, targetHeight) = PhotoSizing.targetSize(width, height, maxEdge)
            if (targetWidth == width && targetHeight == height) return this
            val scaled = scale(targetWidth, targetHeight)
            if (scaled !== this) recycle()
            return scaled
        }

        private fun Bitmap.rotated(orientation: Int): Bitmap {
            val degrees =
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER
                    ExifInterface.ORIENTATION_ROTATE_180 -> HALF
                    ExifInterface.ORIENTATION_ROTATE_270 -> THREE_QUARTERS
                    else -> return this
                }
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
            if (rotated !== this) recycle()
            return rotated
        }

        private class BitmapPhoto(
            private val bitmap: Bitmap,
        ) : ScaledPhoto {
            override val width: Int get() = bitmap.width
            override val height: Int get() = bitmap.height
            override val dominantColorArgb: Long by lazy {
                // A 1x1 downscale averages the photo: a cheap, stable placeholder colour.
                val pixel = bitmap.scale(1, 1)
                val colour = pixel.getPixel(0, 0).toLong() and ARGB_MASK or OPAQUE
                if (pixel !== bitmap) pixel.recycle()
                colour
            }

            override fun writeJpeg(
                out: OutputStream,
                quality: Int,
            ) {
                if (!bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        quality,
                        out,
                    )
                ) {
                    throw IOException("JPEG encoding failed")
                }
            }

            override fun recycle() = bitmap.recycle()
        }

        private companion object {
            const val QUARTER = 90f
            const val HALF = 180f
            const val THREE_QUARTERS = 270f
            const val ARGB_MASK = 0xFFFFFFFFL
            const val OPAQUE = 0xFF000000L
        }
    }

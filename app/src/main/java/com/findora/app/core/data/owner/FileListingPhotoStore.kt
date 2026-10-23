package com.findora.app.core.data.owner

import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.owner.ListingPhoto
import com.findora.app.core.domain.owner.ListingPhotoStore
import com.findora.app.core.domain.owner.PhotoSizing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** A decoded photo, already rotated upright and downscaled to fit [PhotoSizing.MAX_EDGE]. */
interface ScaledPhoto {
    val width: Int
    val height: Int
    val dominantColorArgb: Long

    /** Writes the photo as JPEG at [quality]. */
    fun writeJpeg(
        out: OutputStream,
        quality: Int,
    )

    /** Frees the pixels. */
    fun recycle()
}

/** Decodes a picked content URI; the Android implementation uses BitmapFactory and EXIF orientation. */
fun interface PhotoCodec {
    /** Returns null when [uri] cannot be opened or is not an image. */
    fun decodeScaled(
        uri: String,
        maxEdge: Int,
    ): ScaledPhoto?
}

/** Directory holding listing photos (app-private files, excluded from backups). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ListingPhotosDir

/**
 * Stores listing photos as `<root>/<listingId>/<uuid>.jpg` in app-private storage: decode and downscale with
 * [codec], compress to JPEG ([PhotoSizing.JPEG_QUALITY]) and write through a temporary file, so a failed import never
 * leaves a half-written photo behind. Picked files are never kept by reference: the Photo Picker grant is temporary.
 */
@Singleton
class FileListingPhotoStore
    @Inject
    constructor(
        @ListingPhotosDir private val root: File,
        private val codec: PhotoCodec,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ListingPhotoStore {
        override suspend fun import(
            listingId: String,
            sourceUri: String,
        ): ListingPhoto? =
            withContext(ioDispatcher) {
                val photo = codec.decodeScaled(sourceUri, PhotoSizing.MAX_EDGE) ?: return@withContext null
                val dir = directoryOf(listingId)
                val target = File(dir, "${UUID.randomUUID()}.jpg")
                val temp = File(dir, "${target.name}.tmp")
                try {
                    if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
                    temp.outputStream().use { photo.writeJpeg(it, PhotoSizing.JPEG_QUALITY) }
                    if (!temp.renameTo(target)) {
                        temp.delete()
                        return@withContext null
                    }
                    ListingPhoto(target.absolutePath, photo.width, photo.height, photo.dominantColorArgb)
                } catch (ignored: IOException) {
                    temp.delete()
                    null
                } finally {
                    photo.recycle()
                }
            }

        override suspend fun delete(photo: ListingPhoto) {
            withContext(ioDispatcher) {
                val file = File(photo.path)
                // Only files inside the photo directory may be removed.
                if (file.canonicalPath.startsWith(root.canonicalPath + File.separator)) file.delete()
            }
        }

        override suspend fun retainOnly(
            listingId: String,
            keep: List<ListingPhoto>,
        ) {
            withContext(ioDispatcher) {
                val kept = keep.map { File(it.path).canonicalPath }.toSet()
                directoryOf(listingId).listFiles()?.filter { it.canonicalPath !in kept }?.forEach(File::delete)
            }
        }

        override suspend fun deleteAll(listingId: String) {
            withContext(ioDispatcher) { directoryOf(listingId).deleteRecursively() }
        }

        private fun directoryOf(listingId: String): File {
            require(listingId.matches(SAFE_ID)) { "Unexpected listing id" }
            return File(root, listingId)
        }

        private companion object {
            val SAFE_ID = Regex("[A-Za-z0-9_-]+")
        }
    }

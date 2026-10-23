package com.findora.app.core.data.owner

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingPhoto
import com.findora.app.core.domain.owner.PhotoSizing
import com.findora.app.core.testing.completeDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** The photo import pipeline with a fake decoder, and the versioned draft JSON. */
class OwnerPhotoAndDraftTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakePhoto(
        override val width: Int,
        override val height: Int,
        private val failWrite: Boolean = false,
    ) : ScaledPhoto {
        override val dominantColorArgb: Long = 0xFF336699L
        var recycled = false
        var quality = -1

        override fun writeJpeg(
            out: OutputStream,
            quality: Int,
        ) {
            if (failWrite) throw IOException("disk full")
            this.quality = quality
            out.write(byteArrayOf(1, 2, 3))
        }

        override fun recycle() {
            recycled = true
        }
    }

    private val decoded = mutableListOf<FakePhoto>()
    private val requestedEdges = mutableListOf<Int>()

    private fun store(failWrite: Boolean = false): FileListingPhotoStore {
        val codec =
            PhotoCodec { uri, maxEdge ->
                requestedEdges += maxEdge
                if (uri.startsWith("content://bad")) null else FakePhoto(1600, 1200, failWrite).also { decoded += it }
            }
        return FileListingPhotoStore(File(folder.root, "photos"), codec, Dispatchers.Unconfined)
    }

    @Test
    fun importsDownscaledJpegsIntoTheListingDirectory() =
        runTest {
            val photo = requireNotNull(store().import("own-1", "content://media/picker/1"))
            val file = File(photo.path)
            assertTrue(file.isFile)
            assertEquals(File(folder.root, "photos/own-1").canonicalPath, file.parentFile?.canonicalPath)
            assertTrue(file.name.endsWith(".jpg"))
            assertEquals(1600 to 1200, photo.width to photo.height)
            assertEquals(0xFF336699L, photo.dominantColorArgb)
            assertEquals(listOf(PhotoSizing.MAX_EDGE), requestedEdges)
            assertEquals(PhotoSizing.JPEG_QUALITY, decoded.single().quality)
            assertTrue(decoded.single().recycled)
        }

    @Test
    fun unreadableItemsAndWriteFailuresLeaveNothingBehind() =
        runTest {
            assertNull(store().import("own-1", "content://bad/2"))
            assertNull(store(failWrite = true).import("own-1", "content://media/picker/3"))
            val dir = File(folder.root, "photos/own-1")
            assertTrue(dir.listFiles().orEmpty().isEmpty())
            assertTrue(decoded.single().recycled)
        }

    @Test
    fun deleteRetainAndDeleteAllOnlyTouchTheListingsPhotos() =
        runTest {
            val store = store()
            val first = requireNotNull(store.import("own-1", "content://a"))
            val second = requireNotNull(store.import("own-1", "content://b"))
            val other = requireNotNull(store.import("own-2", "content://c"))
            store.retainOnly("own-1", listOf(second))
            assertFalse(File(first.path).exists())
            assertTrue(File(second.path).exists())
            store.delete(second)
            assertFalse(File(second.path).exists())
            // A path outside the photo directory is never deleted.
            val outside = folder.newFile("keep.jpg")
            store.delete(ListingPhoto(outside.absolutePath, 1, 1, 0))
            assertTrue(outside.exists())
            store.deleteAll("own-2")
            assertFalse(File(other.path).exists())
        }

    @Test(expected = IllegalArgumentException::class)
    fun listingIdsCannotEscapeThePhotoDirectory() =
        runTest {
            store().deleteAll("../secrets")
        }

    @Test
    fun draftsRoundTripThroughVersionedJson() {
        val draft = completeDraft()
        assertEquals(draft, OwnerDraftJson.decode(OwnerDraftJson.encode(draft)))
        assertEquals(ListingDraft(), OwnerDraftJson.decode(OwnerDraftJson.encode(ListingDraft())))
        assertTrue(OwnerDraftJson.encode(draft).contains("\"version\":1"))
    }

    @Test
    fun unknownValuesAreDroppedAndBrokenDocumentsReadAsEmpty() {
        val json =
            """{"version":2,"type":"castle","title":"Old mill","amenities":["wifi","moat"],"currency":"XYZ",""" +
                """"future":true,"latitude":10.0}"""
        val draft = OwnerDraftJson.decode(json)
        assertNull(draft.type)
        assertEquals("Old mill", draft.title)
        assertEquals(setOf(Amenity.Wifi), draft.amenities)
        assertNull(draft.currency)
        // A latitude without a longitude is no pin.
        assertNull(draft.pin)
        assertEquals(ListingDraft(), OwnerDraftJson.decode("{not json"))
        assertNotNull(OwnerDraftJson.decode("{}"))
    }
}

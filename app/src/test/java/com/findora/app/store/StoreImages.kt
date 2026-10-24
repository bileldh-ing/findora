package com.findora.app.store

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.abs

/**
 * Turns a raw Roborazzi capture into a Google Play store image and records or verifies it.
 *
 * Play wants screenshots and the feature graphic as 24-bit PNG without alpha and the icon as a 32-bit PNG, so the
 * capture's pixels are written by [PngWriter] with exactly that colour type. The mode comes from the `findora.store`
 * system property, forwarded from the Gradle property of the same name: `record` writes the image to the target of
 * [process], `verify` compares the capture with the committed image, anything else only renders.
 */
internal object StoreImages {
    enum class Mode { Render, Record, Verify }

    /** A channel may differ by this much (anti-aliasing noise) before a pixel counts as changed. */
    private const val CHANNEL_TOLERANCE = 3

    /** At most this share of the pixels may change before verification fails. */
    private const val MAX_CHANGED_RATIO = 0.001

    private const val OPAQUE = 0xFF000000.toInt()
    private const val RGB = 0x00FFFFFF

    fun mode(): Mode =
        when (System.getProperty("findora.store")) {
            "record" -> Mode.Record
            "verify" -> Mode.Verify
            else -> Mode.Render
        }

    fun process(
        raw: File,
        target: File,
        keepAlpha: Boolean,
        mode: Mode,
    ) {
        val captured = Pixels.read(raw)
        // Store frames are drawn on opaque backgrounds; without alpha, the colour channels are kept as they are.
        val pixels =
            if (keepAlpha) {
                captured
            } else {
                Pixels(
                    captured.width,
                    captured.height,
                    IntArray(captured.argb.size) {
                        OPAQUE or
                            (captured.argb[it] and RGB)
                    },
                )
            }
        when (mode) {
            Mode.Render -> Unit
            Mode.Record -> {
                target.parentFile?.mkdirs()
                target.writeBytes(PngWriter.encode(pixels, keepAlpha))
            }
            Mode.Verify -> verify(pixels, target)
        }
    }

    private fun verify(
        actual: Pixels,
        target: File,
    ) {
        if (!target.exists()) fail("${target.path} is missing; record the store images with -Pfindora.store=record")
        val expected = Pixels.read(target)
        assertEquals("${target.name} size", expected.width to expected.height, actual.width to actual.height)
        val changed = actual.argb.indices.count { differs(actual.argb[it], expected.argb[it]) }
        assertTrue(
            "${target.name} no longer matches the app ($changed pixels changed); " +
                "re-record with -Pfindora.store=record if the change is intended",
            changed.toDouble() / actual.argb.size <= MAX_CHANGED_RATIO,
        )
    }

    private fun differs(
        first: Int,
        second: Int,
    ): Boolean =
        (0 until Int.SIZE_BITS step Byte.SIZE_BITS).any { shift ->
            abs((first ushr shift and 0xFF) - (second ushr shift and 0xFF)) > CHANNEL_TOLERANCE
        }
}

/** Image pixels as ARGB ints, row by row. */
internal class Pixels(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    companion object {
        fun read(file: File): Pixels {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path)) { "cannot decode ${file.absolutePath}" }
            val out = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return Pixels(bitmap.width, bitmap.height, out)
        }
    }
}

/** A minimal PNG encoder (8 bits per channel, RGB or RGBA, no filtering) with deterministic output. */
internal object PngWriter {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val BIT_DEPTH = 8
    private const val COLOR_RGB = 2
    private const val COLOR_RGBA = 6

    fun encode(
        pixels: Pixels,
        alpha: Boolean,
    ): ByteArray {
        val channels = if (alpha) 4 else 3
        val raw = ByteArrayOutputStream(pixels.height * (1 + pixels.width * channels))
        for (y in 0 until pixels.height) {
            raw.write(0) // filter type: none
            for (x in 0 until pixels.width) {
                val argb = pixels.argb[y * pixels.width + x]
                raw.write(argb ushr 16 and 0xFF)
                raw.write(argb ushr 8 and 0xFF)
                raw.write(argb and 0xFF)
                if (alpha) raw.write(argb ushr 24 and 0xFF)
            }
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(raw.toByteArray()) }

        val header = ByteArrayOutputStream()
        DataOutputStream(header).apply {
            writeInt(pixels.width)
            writeInt(pixels.height)
            writeByte(BIT_DEPTH)
            writeByte(if (alpha) COLOR_RGBA else COLOR_RGB)
            writeByte(0) // compression
            writeByte(0) // filter method
            writeByte(0) // no interlace
        }
        val png = ByteArrayOutputStream()
        png.write(SIGNATURE)
        png.chunk("IHDR", header.toByteArray())
        png.chunk("IDAT", compressed.toByteArray())
        png.chunk("IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun ByteArrayOutputStream.chunk(
        type: String,
        data: ByteArray,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc =
            CRC32().apply {
                update(typeBytes)
                update(data)
            }
        DataOutputStream(this).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}

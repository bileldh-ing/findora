package com.findora.app.feature.locations.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap

/** Colours for the marker bitmaps, taken from the Findora theme so markers follow light and dark mode. */
data class MarkerPalette(
    @ColorInt val pill: Int,
    @ColorInt val pillText: Int,
    @ColorInt val outline: Int,
    @ColorInt val selected: Int,
    @ColorInt val selectedText: Int,
    @ColorInt val cluster: Int,
    @ColorInt val clusterText: Int,
)

/**
 * Draws the map's price pills, cluster bubbles and the "you are here" dot as bitmaps (osmdroid markers are
 * drawables). Results are cached per text, state and palette so panning never allocates new bitmaps.
 */
class MarkerBitmaps(
    private val density: Float,
    private val palette: MarkerPalette,
) {
    private val cache = HashMap<String, Bitmap>()

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_SP * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE_DP * density
        }

    /** A rounded price pill with a small pointer under it; the anchor is the pointer's tip (bottom centre). */
    fun pricePill(
        text: String,
        selected: Boolean,
    ): Bitmap =
        cache.getOrPut("pill|$selected|$text") {
            val padH = PAD_H_DP * density
            val height = PILL_HEIGHT_DP * density
            val pointer = POINTER_DP * density
            val stroke = STROKE_DP * density
            val width = textPaint.measureText(text) + padH * 2
            val bitmap = createBitmap((width + stroke * 2).toInt(), (height + pointer + stroke * 2).toInt())
            val canvas = Canvas(bitmap)
            val body = RectF(stroke, stroke, stroke + width, stroke + height)
            val tip =
                Path().apply {
                    moveTo(body.centerX() - pointer, body.bottom - stroke)
                    lineTo(body.centerX(), body.bottom + pointer)
                    lineTo(body.centerX() + pointer, body.bottom - stroke)
                    close()
                }
            fillPaint.color = if (selected) palette.selected else palette.pill
            strokePaint.color = if (selected) palette.selected else palette.outline
            canvas.drawRoundRect(body, height / 2, height / 2, fillPaint)
            canvas.drawRoundRect(body, height / 2, height / 2, strokePaint)
            canvas.drawPath(tip, fillPaint)
            textPaint.color = if (selected) palette.selectedText else palette.pillText
            canvas.drawText(text, body.centerX(), baseline(body), textPaint)
            bitmap
        }

    /** A round bubble with the number of homes it groups. */
    fun clusterBubble(count: Int): Bitmap =
        cache.getOrPut("cluster|$count") {
            val size = (CLUSTER_DP + (count.toString().length - 1) * CLUSTER_GROW_DP) * density
            val ring = RING_DP * density
            val bitmap = createBitmap(size.toInt(), size.toInt())
            val canvas = Canvas(bitmap)
            val radius = size / 2
            fillPaint.color = palette.outline
            canvas.drawCircle(radius, radius, radius, fillPaint)
            fillPaint.color = palette.cluster
            canvas.drawCircle(radius, radius, radius - ring, fillPaint)
            textPaint.color = palette.clusterText
            canvas.drawText(count.toString(), radius, baseline(RectF(0f, 0f, size, size)), textPaint)
            bitmap
        }

    /** The approximate-location dot: accent fill with a light halo. */
    fun locationDot(): Bitmap =
        cache.getOrPut("dot") {
            val size = DOT_DP * density
            val bitmap = createBitmap(size.toInt(), size.toInt())
            val canvas = Canvas(bitmap)
            val radius = size / 2
            fillPaint.color = palette.selected
            fillPaint.alpha = HALO_ALPHA
            canvas.drawCircle(radius, radius, radius, fillPaint)
            fillPaint.alpha = OPAQUE
            canvas.drawCircle(radius, radius, radius * DOT_CORE, fillPaint)
            strokePaint.color = palette.selectedText
            canvas.drawCircle(radius, radius, radius * DOT_CORE, strokePaint)
            bitmap
        }

    private fun baseline(box: RectF): Float = box.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

    private companion object {
        const val TEXT_SP = 13f
        const val PAD_H_DP = 10f
        const val PILL_HEIGHT_DP = 28f
        const val POINTER_DP = 6f
        const val STROKE_DP = 1f
        const val CLUSTER_DP = 40f
        const val CLUSTER_GROW_DP = 8f
        const val RING_DP = 3f
        const val DOT_DP = 28f
        const val DOT_CORE = 0.4f
        const val HALO_ALPHA = 64
        const val OPAQUE = 255
    }
}

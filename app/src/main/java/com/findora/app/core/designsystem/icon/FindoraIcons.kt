package com.findora.app.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin outline icon set (24dp grid, 1.5dp stroke) drawn in the style of Lucide (ISC licence).
 * Outline icons are tinted by `Icon`; [VerifiedBadge] is pre-coloured and drawn with `Image`.
 */
object FindoraIcons {
    val Home: ImageVector by lazy {
        outline(
            "Home",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5" +
                "a2 2 0 0 1-2-2z",
        )
    }

    val HomeFilled: ImageVector by lazy {
        filled(
            "HomeFilled",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2h-5" +
                "v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8H5a2 2 0 0 1-2-2z",
        )
    }

    /** Map pin with a small house: the location chip and the Locations tab. */
    val MapPinHouse: ImageVector by lazy {
        outline(
            "MapPinHouse",
            "M15 22a1 1 0 0 1-1-1v-4a1 1 0 0 1 .445-.832l3-2a1 1 0 0 1 1.11 0l3 2A1 1 0 0 1 22 17v4a1 1 0 0 1-1 1z",
            "M18 10a8 8 0 0 0-16 0c0 4.993 5.539 10.193 7.399 11.799a1 1 0 0 0 .601.2",
            "M18 22v-3",
            circle(10f, 10f, 3f),
        )
    }

    val MapPin: ImageVector by lazy {
        outline(
            "MapPin",
            "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
            circle(12f, 10f, 3f),
        )
    }

    val Chat: ImageVector by lazy {
        outline("Chat", "M7.9 20A9 9 0 1 0 4 16.1L2 22Z", "M8 12h.01", "M12 12h.01", "M16 12h.01")
    }

    val Settings: ImageVector by lazy {
        outline(
            "Settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73" +
                "l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73" +
                "l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44" +
                "a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
                "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73" +
                "l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }

    val Bell: ImageVector by lazy {
        outline("Bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0")
    }

    val Search: ImageVector by lazy { outline("Search", circle(11f, 11f, 8f), "m21 21-4.3-4.3") }

    /** Horizontal sliders: the filter button. */
    val Sliders: ImageVector by lazy {
        outline(
            "Sliders",
            "M21 4h-7",
            "M10 4H3",
            "M21 12h-9",
            "M8 12H3",
            "M21 20h-5",
            "M12 20H3",
            "M14 2v4",
            "M8 10v4",
            "M16 18v4",
        )
    }

    val Heart: ImageVector by lazy { outline("Heart", HEART) }

    val HeartFilled: ImageVector by lazy { filled("HeartFilled", HEART) }

    val Star: ImageVector by lazy {
        filled("Star", "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z")
    }

    val ArrowBack: ImageVector by lazy { outline("ArrowBack", "m12 19-7-7 7-7", "M19 12H5", autoMirror = true) }

    val Close: ImageVector by lazy { outline("Close", "M18 6 6 18", "m6 6 12 12") }

    val Eye: ImageVector by lazy {
        outline("Eye", "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z", circle(12f, 12f, 3f))
    }

    val EyeOff: ImageVector by lazy {
        outline(
            "EyeOff",
            "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
            "M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
            "M14.12 14.12a3 3 0 1 1-4.24-4.24",
            "m2 2 20 20",
        )
    }

    val Lock: ImageVector by lazy {
        outline(
            "Lock",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val Mail: ImageVector by lazy {
        outline(
            "Mail",
            "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7",
        )
    }

    val User: ImageVector by lazy { outline("User", circle(12f, 8f, 5f), "M20 21a8 8 0 0 0-16 0") }

    val LogOut: ImageVector by lazy {
        outline("LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9", autoMirror = true)
    }

    val Trash: ImageVector by lazy {
        outline(
            "Trash",
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        )
    }

    val History: ImageVector by lazy {
        outline("History", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")
    }

    val AlertCircle: ImageVector by lazy { outline("AlertCircle", circle(12f, 12f, 10f), "M12 8v4", "M12 16h.01") }

    val Check: ImageVector by lazy { outline("Check", "M20 6 9 17l-5-5") }

    val ChevronEnd: ImageVector by lazy { outline("ChevronEnd", "m9 18 6-6-6-6", autoMirror = true) }

    val Fingerprint: ImageVector by lazy {
        outline(
            "Fingerprint",
            "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
            "M14 13.12c0 2.38 0 6.38-1 8.88",
            "M17.29 21.02c.12-.6.43-2.3.5-3.02",
            "M2 12a10 10 0 0 1 18-6",
            "M2 16h.01",
            "M21.8 16c.2-2 .131-5.354 0-6",
            "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
            "M8.65 22c.21-.66.45-1.32.57-2",
            "M9 6.8a6 6 0 0 1 9 5.2v2",
        )
    }

    val Share: ImageVector by lazy {
        outline(
            "Share",
            circle(18f, 5f, 3f),
            circle(6f, 12f, 3f),
            circle(18f, 19f, 3f),
            "M8.59 13.51 15.42 17.49",
            "M15.41 6.51 8.59 10.49",
        )
    }

    val Phone: ImageVector by lazy {
        outline(
            "Phone",
            "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67" +
                "A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11" +
                "L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z",
        )
    }

    val Bed: ImageVector by lazy { outline("Bed", "M2 4v16", "M2 8h18a2 2 0 0 1 2 2v10", "M2 17h20", "M6 8v9") }

    val Bath: ImageVector by lazy {
        outline(
            "Bath",
            "M10 4 8 6",
            "M17 19v2",
            "M2 12h20",
            "M7 19v2",
            "M9 5 7.621 3.621A2.121 2.121 0 0 0 4 5v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5",
        )
    }

    /** Four corners: floor area. */
    val Area: ImageVector by lazy {
        outline(
            "Area",
            "M8 3H5a2 2 0 0 0-2 2v3",
            "M21 8V5a2 2 0 0 0-2-2h-3",
            "M3 16v3a2 2 0 0 0 2 2h3",
            "M16 21h3a2 2 0 0 0 2-2v-3",
        )
    }

    /** Stacked layers: the floor a home is on. */
    val Layers: ImageVector by lazy {
        outline(
            "Layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9" +
                "a1 1 0 0 0 0-1.83Z",
            "M22 17.65l-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
            "M22 12.65l-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
        )
    }

    val Calendar: ImageVector by lazy {
        outline(
            "Calendar",
            "M8 2v4",
            "M16 2v4",
            "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
            "M3 10h18",
        )
    }

    val Clock: ImageVector by lazy { outline("Clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }

    val FoldedMap: ImageVector by lazy {
        outline(
            "FoldedMap",
            "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277" +
                "a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618" +
                "a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
            "M15 5.764v15",
            "M9 3.236v15",
        )
    }

    /** Rising line; mirrors in right-to-left layouts, like the charts it labels. */
    val TrendUp: ImageVector by lazy {
        outline("TrendUp", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6", autoMirror = true)
    }

    val TrendDown: ImageVector by lazy {
        outline("TrendDown", "M22 17 13.5 8.5 8.5 13.5 2 7", "M16 17h6v-6", autoMirror = true)
    }

    val TrendFlat: ImageVector by lazy { outline("TrendFlat", "M5 12h14") }

    val Calculator: ImageVector by lazy {
        outline(
            "Calculator",
            "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z",
            "M8 6h8",
            "M16 14v4",
            "M16 10h.01",
            "M12 10h.01",
            "M8 10h.01",
            "M12 14h.01",
            "M8 14h.01",
            "M12 18h.01",
            "M8 18h.01",
        )
    }

    /** Curved arrow back: Undo in the Dream Match deck (mirrors in right-to-left layouts). */
    val Undo: ImageVector by lazy {
        outline("Undo", "M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11", autoMirror = true)
    }

    /** Three columns: Compare. */
    val Compare: ImageVector by lazy {
        outline(
            "Compare",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M9 3v18",
            "M15 3v18",
        )
    }

    /** Four-point sparkle: Dream Match and the super like. */
    val Sparkles: ImageVector by lazy {
        outline(
            "Sparkles",
            "M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z",
            "M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z",
        )
    }

    /** Blue shield with a white check: the verified badge on cards (not tinted). */
    val VerifiedBadge: ImageVector by lazy {
        ImageVector
            .Builder("VerifiedBadge", 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(SHIELD),
                fill = SolidColor(VERIFIED_BLUE),
                stroke = SolidColor(VERIFIED_BLUE),
                strokeLineWidth = STROKE,
                strokeLineJoin = StrokeJoin.Round,
            ).addPath(
                pathData = addPathNodes("m9 12 2 2 4-4"),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }

    private const val HEART =
        "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2" +
            "A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"
    private const val SHIELD =
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72" +
            "a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
    private val VERIFIED_BLUE = Color(0xFF1F6BFF)
    private const val STROKE = 1.5f

    private fun circle(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun outline(
        name: String,
        vararg paths: String,
        autoMirror: Boolean = false,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror)
            .apply {
                paths.forEach { path ->
                    addPath(
                        pathData = addPathNodes(path),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = STROKE,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    private fun filled(
        name: String,
        path: String,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}

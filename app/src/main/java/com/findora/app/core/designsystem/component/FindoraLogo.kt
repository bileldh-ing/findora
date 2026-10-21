package com.findora.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.designsystem.theme.SyneFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SPOKES = 8

/** The network-snowflake mark: a hub with eight spokes, each ending in a node. */
@Composable
fun FindoraLogoMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val stroke = radius * 0.09f
        val node = radius * 0.13f
        repeat(SPOKES) { i ->
            val angle = (2 * PI * i / SPOKES) - PI / 2
            val reach = if (i % 2 == 0) radius * 0.86f else radius * 0.62f
            val end = Offset(center.x + (cos(angle) * reach).toFloat(), center.y + (sin(angle) * reach).toFloat())
            drawLine(color, center, end, strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = node, center = end)
        }
        drawCircle(color, radius = radius * 0.2f, center = center)
    }
}

/** White circular badge with the mark and the tiny "FINDORA / DREAM HOUSE" lockup (about 112dp). */
@Composable
fun FindoraLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
) {
    val ink = Color(0xFF161616)
    Column(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .semantics { contentDescription = "Findora Dream House logo" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FindoraLogoMark(color = ink, modifier = Modifier.size(size * 0.36f))
        Text(
            text = "FINDORA",
            color = ink,
            fontFamily = SyneFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.085f).sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "DREAM HOUSE",
            color = ink,
            style = FindoraTheme.typography.label,
            fontSize = (size.value * 0.045f).sp,
            letterSpacing = 0.2.em,
            textAlign = TextAlign.Center,
        )
    }
}

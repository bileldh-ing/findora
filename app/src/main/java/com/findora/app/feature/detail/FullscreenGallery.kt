package com.findora.app.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.PropertyImage
import com.findora.app.core.image.offlineFallback
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DismissDistance = 160.dp
private val CloseButtonSize = 48.dp
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val DISMISS_VELOCITY = 2_000f
private const val CLOSE_SCRIM_ALPHA = 0.45f

/**
 * Full-screen photo viewer over the detail page: swipe between photos, pinch or double-tap to zoom (pan while
 * zoomed), and drag vertically to dismiss with the black background fading as the photo moves away.
 * Back and the close button also dismiss; [onDismiss] receives the photo the user ended on.
 */
@Composable
fun FullscreenGallery(
    images: List<PropertyImage>,
    initialPage: Int,
    onDismiss: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { images.size }
    var zoomed by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { DismissDistance.toPx() }
    val dismiss = { onDismiss(pagerState.currentPage) }
    BackHandler(onBack = dismiss)
    val fade = (1f - abs(drag.value) / (dismissPx * 2)).coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fade))
                .testTag("gallery"),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            key = { images[it].url },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = drag.value }
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !zoomed,
                        state = rememberDraggableState { delta -> scope.launch { drag.snapTo(drag.value + delta) } },
                        onDragStopped = { velocity ->
                            if (abs(drag.value) > dismissPx || abs(velocity) > DISMISS_VELOCITY) {
                                dismiss()
                            } else {
                                drag.animateTo(0f)
                            }
                        },
                    ),
        ) { page ->
            ZoomableImage(
                image = images[page],
                onZoomChange = { isZoomed -> if (page == pagerState.currentPage) zoomed = isZoomed },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = stringResource(R.string.detail_photo_position, pagerState.currentPage + 1, images.size),
            style = FindoraTheme.typography.label,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = FindoraTheme.spacing.lg)
                    .graphicsLayer { alpha = fade },
        )
        val closeLabel = stringResource(R.string.detail_gallery_close)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(FindoraTheme.spacing.md)
                    .size(CloseButtonSize)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = CLOSE_SCRIM_ALPHA))
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = dismiss)
                    .semantics { contentDescription = closeLabel }
                    .testTag("galleryClose"),
        ) {
            Icon(FindoraIcons.Close, contentDescription = null, tint = Color.White)
        }
    }
}

/** A photo that zooms with pinch (1x to 4x) or double-tap (toggles 2.5x around the tap) and pans while zoomed. */
@Composable
private fun ZoomableImage(
    image: PropertyImage,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset): Offset {
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun update(
        newScale: Float,
        newOffset: Offset,
    ) {
        scale = newScale.coerceIn(1f, MAX_ZOOM)
        offset = if (scale > 1f) clamp(newOffset) else Offset.Zero
        onZoomChange(scale > 1f)
    }
    val transformState =
        rememberTransformableState { zoomChange, panChange, _ -> update(scale * zoomChange, offset + panChange) }
    val context = LocalContext.current
    val request =
        remember(image.url) {
            ImageRequest
                .Builder(context)
                .data(image.url)
                .offlineFallback(image.fallbackAsset)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = image.altText,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                update(1f, Offset.Zero)
                            } else {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                update(DOUBLE_TAP_ZOOM, (center - tap) * (DOUBLE_TAP_ZOOM - 1f))
                            }
                        },
                    )
                }.transformable(state = transformState, canPan = { scale > 1f })
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
    )
}

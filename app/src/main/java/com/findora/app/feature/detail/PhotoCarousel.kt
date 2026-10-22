package com.findora.app.feature.detail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.findora.app.R
import com.findora.app.core.designsystem.component.rememberReducedMotion
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyImage
import com.findora.app.core.image.offlineFallback
import com.findora.app.core.ui.SharedPropertyPhoto

val PhotoHeight = 380.dp
private val DotSize = 6.dp
private val ActiveDotWidth = 18.dp
private const val HORIZONTAL_PARALLAX = 0.35f
private const val VERTICAL_PARALLAX = 0.5f
private const val TOP_SCRIM_END = 0.3f
private const val INACTIVE_DOT_ALPHA = 0.55f

/**
 * Swipeable listing photos. Each photo drifts slower than the page while swiping (horizontal parallax) and the
 * carousel scrolls away at half speed (vertical parallax); both are off with reduced motion. A dot indicator
 * shows the position; tapping a photo opens the full-screen gallery at that photo.
 */
@Composable
fun PhotoCarousel(
    property: Property,
    pagerState: PagerState,
    listState: LazyListState,
    onOpenGallery: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val openLabel = stringResource(R.string.detail_open_gallery)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PhotoHeight)
                .clipToBounds()
                .graphicsLayer {
                    if (!reduceMotion && listState.firstVisibleItemIndex == 0) {
                        translationY = listState.firstVisibleItemScrollOffset * VERTICAL_PARALLAX
                    }
                }.testTag("detailPhotos"),
    ) {
        SharedPropertyPhoto(propertyId = property.id) { sharedModifier ->
            HorizontalPager(
                state = pagerState,
                key = { property.images[it].url },
                modifier = sharedModifier.fillMaxSize(),
            ) { page ->
                val image = property.images[page]
                val position =
                    stringResource(R.string.detail_photo_position, page + 1, property.images.size)
                CarouselPhoto(
                    image = image,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(role = Role.Image, onClickLabel = openLabel) { onOpenGallery(page) }
                            .semantics { contentDescription = "$position. ${image.altText}" }
                            .graphicsLayer {
                                if (!reduceMotion) {
                                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    val direction = if (rtl) -1f else 1f
                                    translationX = offset * size.width * HORIZONTAL_PARALLAX * direction
                                }
                            },
                )
            }
        }
        // Keeps the white top buttons legible on bright photos.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = TOP_SCRIM_END), 1f to Color.Transparent)),
        )
        if (property.images.size > 1) {
            PageIndicator(
                count = property.images.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FindoraTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun CarouselPhoto(
    image: PropertyImage,
    modifier: Modifier = Modifier,
) {
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
        contentDescription = null,
        placeholder = ColorPainter(Color(image.dominantColorArgb)),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** Dots, the current one stretched into a pill. Read by TalkBack as "Photo 2 of 5". */
@Composable
private fun PageIndicator(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.detail_photo_position, current + 1, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(DotSize),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(FindoraTheme.shapes.chip)
                .background(Color.Black.copy(alpha = INDICATOR_SCRIM_ALPHA))
                .padding(horizontal = FindoraTheme.spacing.xs, vertical = FindoraTheme.spacing.xxs)
                .clearAndSetSemantics { contentDescription = description }
                .testTag("detailPageIndicator"),
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) ActiveDotWidth else DotSize, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) Color.White else Color.White.copy(alpha = INACTIVE_DOT_ALPHA),
                label = "dotColor",
            )
            Box(
                Modifier
                    .width(width)
                    .height(DotSize)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private const val INDICATOR_SCRIM_ALPHA = 0.3f

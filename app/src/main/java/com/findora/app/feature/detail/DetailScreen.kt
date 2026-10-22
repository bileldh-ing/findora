package com.findora.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.ShimmerBox
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.StateMessage
import kotlinx.coroutines.launch

private val TopButtonSize = 48.dp
private val TopIconSize = 22.dp
private const val TOP_BUTTON_SCRIM_ALPHA = 0.38f
private val SkeletonLine = 20.dp
private const val SKELETON_LINES = 4

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val noApp = stringResource(R.string.detail_no_app)
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            if (!context.launchDetailEffect(effect)) {
                launch { snackbarHostState.showSnackbar(noApp) }
            }
        }
    }
    DetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenProperty = onOpenProperty,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * A listing's detail page: photo carousel (tap for the full-screen gallery) under back, share and save
 * buttons, then price, rating, verification, description, specs, amenities, agent, location and similar
 * homes. Loading shows a skeleton; a missing or unreadable listing shows a message with a way out.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    onEvent: (DetailUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(FindoraTheme.colors.background)
                .testTag("detail"),
    ) {
        when (state) {
            DetailUiState.Loading -> DetailSkeleton()
            is DetailUiState.Error ->
                DetailErrorMessage(state.error, onRetry = { onEvent(DetailUiEvent.Retry) }, onBack = onBack)
            is DetailUiState.Content ->
                DetailContent(content = state, onEvent = onEvent, onBack = onBack, onOpenProperty = onOpenProperty)
        }
        if (state !is DetailUiState.Content) {
            TopIconButton(
                icon = FindoraIcons.ArrowBack,
                label = stringResource(R.string.back),
                onClick = onBack,
                tag = "detailBack",
                modifier = Modifier.statusBarsPadding().padding(FindoraTheme.spacing.md),
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun DetailContent(
    content: DetailUiState.Content,
    onEvent: (DetailUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProperty: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val property = content.property
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState { property.images.size }
    var galleryPage by rememberSaveable(property.id) { mutableStateOf<Int?>(null) }
    var showVerification by rememberSaveable(property.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomInset + FindoraTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.lg),
            modifier = Modifier.fillMaxSize().testTag("detailList"),
        ) {
            item(key = "photos") {
                PhotoCarousel(
                    property = property,
                    pagerState = pagerState,
                    listState = listState,
                    onOpenGallery = { galleryPage = it },
                )
            }
            item(key = "summary") {
                SummarySection(property = property, onShowVerification = { showVerification = true })
            }
            item(key = "specs") { SpecsRow(property) }
            item(key = "description") { DescriptionSection(property.description) }
            if (property.amenities.isNotEmpty()) item(key = "amenities") { AmenitiesSection(property.amenities) }
            item(key = "agent") {
                AgentCard(
                    agent = property.agent,
                    onCall = { onEvent(DetailUiEvent.CallAgent) },
                    onEmail = { onEvent(DetailUiEvent.EmailAgent) },
                )
            }
            item(key = "location") {
                LocationSection(property = property, onOpenMap = { onEvent(DetailUiEvent.OpenInMaps) })
            }
            if (content.similar.isNotEmpty()) {
                item(key = "similar") {
                    SimilarSection(
                        homes = content.similar,
                        favoriteIds = content.similarFavoriteIds,
                        onOpen = onOpenProperty,
                        onToggleFavorite = { onEvent(DetailUiEvent.ToggleSimilarFavorite(it)) },
                    )
                }
            }
        }
        DetailTopBar(
            isFavorite = content.isFavorite,
            onBack = onBack,
            onShare = { onEvent(DetailUiEvent.Share) },
            onToggleFavorite = { onEvent(DetailUiEvent.ToggleFavorite) },
        )
        galleryPage?.let { start ->
            FullscreenGallery(
                images = property.images,
                initialPage = start,
                onDismiss = { page ->
                    galleryPage = null
                    scope.launch { pagerState.scrollToPage(page) }
                },
            )
        }
        if (showVerification) VerificationSheet(onDismiss = { showVerification = false })
    }
}

@Composable
private fun DetailTopBar(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = FindoraTheme.spacing.md, vertical = FindoraTheme.spacing.xs),
    ) {
        TopIconButton(FindoraIcons.ArrowBack, stringResource(R.string.back), onBack, tag = "detailBack")
        Spacer(Modifier.weight(1f))
        TopIconButton(FindoraIcons.Share, stringResource(R.string.action_share), onShare, tag = "detailShare")
        TopIconButton(
            icon = if (isFavorite) FindoraIcons.HeartFilled else FindoraIcons.Heart,
            label = stringResource(if (isFavorite) R.string.action_unsave else R.string.action_save),
            onClick = onToggleFavorite,
            tag = "detailFavorite",
            tint = if (isFavorite) FindoraTheme.colors.error else Color.White,
        )
    }
}

/** 48dp round button with a dark scrim so it reads on any photo and on the page background alike. */
@Composable
private fun TopIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(TopButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = TOP_BUTTON_SCRIM_ALPHA))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(TopIconSize))
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag("detailLoading")) {
        ShimmerBox(Modifier.fillMaxWidth().height(PhotoHeight))
        Column(
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm),
            modifier = Modifier.padding(FindoraTheme.spacing.screenHorizontal),
        ) {
            repeat(SKELETON_LINES) { index ->
                ShimmerBox(
                    Modifier
                        .fillMaxWidth(if (index % 2 == 0) 1f else SKELETON_SHORT_FRACTION)
                        .height(SkeletonLine)
                        .clip(FindoraTheme.shapes.chip),
                )
            }
        }
    }
}

private const val SKELETON_SHORT_FRACTION = 0.6f

@Composable
private fun DetailErrorMessage(
    error: DetailError,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (error) {
            DetailError.NotFound ->
                StateMessage(
                    icon = FindoraIcons.Search,
                    title = stringResource(R.string.detail_not_found_title),
                    body = stringResource(R.string.detail_not_found_body),
                    actionLabel = stringResource(R.string.back),
                    onAction = onBack,
                    modifier = Modifier.testTag("detailNotFound"),
                )
            DetailError.Unavailable ->
                StateMessage(
                    icon = FindoraIcons.AlertCircle,
                    title = stringResource(R.string.detail_error_title),
                    body = stringResource(R.string.error_body),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                    modifier = Modifier.testTag("detailError"),
                )
        }
    }
}

package com.findora.app.feature.about

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.R
import com.findora.app.core.designsystem.component.FindoraLogoBadge
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.ui.currentLocale
import com.findora.app.feature.settings.PRIVACY_POLICY_URL
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val BackButtonSize = 48.dp
private val LinkRowMinHeight = 48.dp
private val LogoSize = 72.dp

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AboutScreen(state = state, onBack = onBack, modifier = modifier)
}

/** Version, privacy policy, exchange-rate source, map attribution, photo credits and open-source licences. */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(FindoraTheme.colors.background).testTag("about")) {
        AboutTopBar(onBack)
        when (state) {
            AboutUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FindoraTheme.colors.onBackground)
                }
            is AboutUiState.Content -> AboutContent(state)
        }
    }
}

@Composable
private fun AboutContent(
    state: AboutUiState.Content,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val credits = state.credits
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("aboutList"),
        contentPadding =
            PaddingValues(
                start = FindoraTheme.spacing.screenHorizontal,
                end = FindoraTheme.spacing.screenHorizontal,
                bottom = FindoraTheme.spacing.lg + bottomInset,
            ),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
                FindoraLogoBadge(size = LogoSize)
                Text(
                    text = stringResource(R.string.about_version, state.versionName),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onSurfaceMuted,
                    modifier = Modifier.testTag("aboutVersion"),
                )
                Text(
                    text = stringResource(R.string.about_body),
                    style = FindoraTheme.typography.body,
                    color = FindoraTheme.colors.onBackground,
                )
                LinkRow(stringResource(R.string.privacy_policy), onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) })
            }
        }
        state.rates?.let { rates ->
            item(key = "rates") {
                Section(stringResource(R.string.about_rates_title)) {
                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())
                    Body(stringResource(R.string.about_rates_body, date.format(rates.asOf), rates.source))
                }
            }
        }
        item(key = "map") {
            Section(stringResource(R.string.about_map_title)) {
                Body(stringResource(R.string.about_map_body, credits.mapAttribution, credits.mapLicense))
                LinkRow(credits.mapAttribution, onClick = { uriHandler.openUri(credits.mapUrl) })
            }
        }
        item(key = "photos") {
            Section(stringResource(R.string.about_photos_title)) {
                Body(stringResource(R.string.about_photos_body, credits.photoSource, credits.photoLicense))
                LinkRow(credits.photoLicense, onClick = { uriHandler.openUri(credits.photoLicenseUrl) })
            }
        }
        items(credits.photographers, key = { "photo-${it.url}" }) { credit ->
            LinkRow(
                title = credit.name,
                summary = pluralStringResource(R.plurals.about_photo_count, credit.photos, credit.photos),
                onClick = { uriHandler.openUri(credit.url) },
            )
        }
        item(key = "libraries") {
            Section(stringResource(R.string.about_libraries_title), Modifier.testTag("aboutLibraries")) {}
        }
        items(credits.libraries, key = { "lib-${it.name}" }) { library ->
            LinkRow(
                title = library.name,
                summary = stringResource(R.string.about_library_summary, library.author, library.license),
                onClick = { uriHandler.openUri(library.url) },
            )
        }
    }
}

@Composable
private fun AboutTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backLabel = stringResource(R.string.back)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(FindoraTheme.spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = backLabel, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .testTag("aboutBack"),
        ) {
            Icon(FindoraIcons.ArrowBack, contentDescription = null, tint = FindoraTheme.colors.onBackground)
        }
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(
            text = stringResource(R.string.about_title),
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(top = FindoraTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        Text(
            text = title,
            style = FindoraTheme.typography.section,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Body(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onBackground, modifier = modifier)
}

/** An outbound link: opens in the browser. */
@Composable
private fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = LinkRowMinHeight)
                .clip(FindoraTheme.shapes.chip)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FindoraTheme.spacing.xs, vertical = FindoraTheme.spacing.xxs),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = FindoraTheme.typography.bodyMedium, color = FindoraTheme.colors.accent)
            if (summary != null) {
                Text(text = summary, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
            }
        }
        Icon(FindoraIcons.ExternalLink, contentDescription = null, tint = FindoraTheme.colors.onSurfaceMuted)
    }
}

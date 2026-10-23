package com.findora.app.feature.auth.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.Dot
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.EmailError
import com.findora.app.core.domain.model.NameError
import com.findora.app.core.domain.model.PasswordRule
import java.time.Duration
import java.time.Instant

private val AuthFormMaxWidth = 560.dp

/** Scrollable, keyboard-aware column shared by the auth forms, with a back button and a heading. */
@Composable
fun AuthFormScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Centred and capped at a readable width on tablets, foldables and in landscape.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = AuthFormMaxWidth)
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal, vertical = FindoraTheme.spacing.xs),
    ) {
        IconButton(onClick = onBack) {
            Icon(FindoraIcons.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
        Text(
            text = title,
            style = FindoraTheme.typography.display,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(FindoraTheme.spacing.xs))
        Text(text = subtitle, style = FindoraTheme.typography.body, color = FindoraTheme.colors.onSurfaceMuted)
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
        content()
        Spacer(Modifier.height(FindoraTheme.spacing.lg))
    }
}

/** Live checklist of password rules: green dot when met, grey when not; each row reads "rule, met". */
@Composable
fun PasswordRulesChecklist(
    unmet: Set<PasswordRule>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xxs)) {
        PasswordRule.entries.forEach { rule ->
            val met = rule !in unmet
            val label = stringResource(rule.labelRes())
            val status = stringResource(if (met) R.string.rule_met else R.string.rule_not_met)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$label, $status" },
            ) {
                Dot(color = if (met) FindoraTheme.colors.success else FindoraTheme.colors.outline)
                Spacer(Modifier.width(FindoraTheme.spacing.xs))
                Text(
                    text = label,
                    style = FindoraTheme.typography.caption,
                    color = if (met) FindoraTheme.colors.onBackground else FindoraTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

/** A form-level error banner (wrong credentials, lockout countdown). */
@Composable
fun FormErrorText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = FindoraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FindoraIcons.AlertCircle, contentDescription = null, tint = FindoraTheme.colors.error)
        Spacer(Modifier.width(FindoraTheme.spacing.xs))
        Text(text = text, style = FindoraTheme.typography.body, color = FindoraTheme.colors.error)
    }
}

/** Spacing between form fields. */
val FieldGap = 16.dp

fun PasswordRule.labelRes(): Int =
    when (this) {
        PasswordRule.MinLength -> R.string.rule_min_length
        PasswordRule.Letter -> R.string.rule_letter
        PasswordRule.Digit -> R.string.rule_digit
    }

fun EmailError.messageRes(): Int =
    when (this) {
        EmailError.Blank -> R.string.error_email_blank
        EmailError.Malformed -> R.string.error_email_malformed
    }

fun NameError.messageRes(): Int =
    when (this) {
        NameError.Blank -> R.string.error_name_blank
        NameError.TooLong -> R.string.error_name_too_long
    }

/** Whole seconds left until [until], rounded up so a countdown never shows 0 while still locked. */
fun secondsUntil(
    until: Instant,
    now: Instant,
): Int {
    val millis = Duration.between(now, until).toMillis()
    return if (millis <= 0) 0 else ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

private const val MILLIS_PER_SECOND = 1000L

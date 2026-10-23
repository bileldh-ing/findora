package com.findora.app.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme

/**
 * Filled, borderless text field on the surface grey with the chip radius. The label sits above the field and
 * an [error] message (when present) below it, announced to TalkBack through the error semantics.
 */
@Composable
fun FindoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    fieldTag: String? = null,
    minLines: Int = 1,
    supportingText: String? = null,
) {
    val colors = FindoraTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = FindoraTheme.typography.bodyMedium,
            color = colors.onBackground,
            modifier = Modifier.padding(bottom = FindoraTheme.spacing.xs),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = minLines == 1,
            minLines = minLines,
            isError = error != null,
            textStyle = FindoraTheme.typography.body,
            leadingIcon = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            shape = FindoraTheme.shapes.chip,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    disabledContainerColor = colors.surface,
                    errorContainerColor = colors.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.onBackground,
                    unfocusedTextColor = colors.onBackground,
                    cursorColor = colors.accent,
                    focusedLeadingIconColor = colors.onBackground,
                    unfocusedLeadingIconColor = colors.onSurfaceMuted,
                    errorLeadingIconColor = colors.error,
                    focusedTrailingIconColor = colors.onSurfaceMuted,
                    unfocusedTrailingIconColor = colors.onSurfaceMuted,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .then(
                        if (error != null) {
                            Modifier.border(1.dp, colors.error, FindoraTheme.shapes.chip)
                        } else {
                            Modifier
                        },
                    ).semantics { if (error != null) error(error) }
                    .then(if (fieldTag != null) Modifier.testTag(fieldTag) else Modifier),
        )
        if (error != null) {
            Text(
                text = error,
                style = FindoraTheme.typography.caption,
                color = colors.error,
                modifier = Modifier.padding(top = FindoraTheme.spacing.xxs, start = FindoraTheme.spacing.xs),
            )
        } else if (supportingText != null) {
            Text(
                text = supportingText,
                style = FindoraTheme.typography.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = FindoraTheme.spacing.xxs, start = FindoraTheme.spacing.xs),
            )
        }
    }
}

/** Password field with a show/hide toggle; the state survives configuration changes. */
@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
    fieldTag: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FindoraTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        leadingIcon = FindoraIcons.Lock,
        error = error,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
        enabled = enabled,
        fieldTag = fieldTag,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) FindoraIcons.EyeOff else FindoraIcons.Eye,
                    contentDescription =
                        stringResource(
                            if (visible) R.string.hide_password else R.string.show_password,
                        ),
                )
            }
        },
    )
}

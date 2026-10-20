package com.shilapi.xcertplay.newui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun NewUiFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = NewUiTypography.SettingSubtitle,
            color = NewUiColors.TextMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        // Wi‑Fi names/passwords are always typed LTR; @ _ etc. must not flip bidi or the cursor jumps.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                    autoCorrect = false,
                ),
                textStyle = NewUiTypography.ActionSubtitle.copy(
                    color = NewUiColors.TextPrimary,
                    textDirection = TextDirection.Ltr,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NewUiColors.Accent,
                    unfocusedBorderColor = NewUiColors.CardBorder,
                    cursorColor = NewUiColors.Accent,
                    focusedContainerColor = NewUiColors.DropdownBackground,
                    unfocusedContainerColor = NewUiColors.DropdownBackground,
                ),
                shape = shape,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingLabel != null && onTrailingClick != null) {
            Text(
                trailingLabel,
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.Accent,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onTrailingClick),
            )
        }
    }
}

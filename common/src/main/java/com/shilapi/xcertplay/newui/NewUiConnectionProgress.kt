package com.shilapi.xcertplay.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.CarPlayConnectionProgress
import com.shilapi.xcertplay.host.R

enum class NewUiProgressVisual {
    IDLE,
    ACTIVE,
    COMPLETE,
    FAILED,
}

@Composable
fun NewUiConnectionProgressBlock(
    litCount: Int,
    stepTitle: String,
    stepSubtitle: String?,
    visual: NewUiProgressVisual,
    modifier: Modifier = Modifier,
    stepCount: Int = CarPlayConnectionProgress.STEP_COUNT,
    pairingCode: String? = null,
) {
    val shape = RoundedCornerShape(NewUiLayout.ProgressBlockRadius)
    val borderColor = when (visual) {
        NewUiProgressVisual.COMPLETE -> NewUiColors.StatusReady.copy(alpha = 0.35f)
        NewUiProgressVisual.FAILED -> NewUiColors.HongqiRed.copy(alpha = 0.35f)
        else -> NewUiColors.Divider.copy(alpha = 0.5f)
    }
    val backgroundBrush = when (visual) {
        NewUiProgressVisual.COMPLETE -> Brush.verticalGradient(
            0f to NewUiColors.StatusReady.copy(alpha = 0.08f),
            1f to Color.Transparent,
        )
        NewUiProgressVisual.FAILED -> Brush.verticalGradient(
            0f to NewUiColors.HongqiRed.copy(alpha = 0.10f),
            1f to Color.Transparent,
        )
        else -> Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.06f),
            1f to Color.Transparent,
        )
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val availableWidth = maxWidth
        val progressContent: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(contentModifier) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (stepCount == CarPlayConnectionProgress.STEP_COUNT)
                        Arrangement.SpaceBetween else Arrangement.spacedBy(NewUiLayout.NavTabGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.new_ui_connection_progress_heading),
                        style = NewUiTypography.ProgressLabel,
                        color = NewUiColors.TextSecondary,
                    )
                    if (visual != NewUiProgressVisual.IDLE && litCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.new_ui_connection_progress_counter,
                                litCount,
                                stepCount,
                            ),
                            style = NewUiTypography.ProgressCounter,
                            color = when (visual) {
                                NewUiProgressVisual.COMPLETE -> NewUiColors.StatusReady
                                NewUiProgressVisual.FAILED -> NewUiColors.HongqiRed.copy(alpha = 0.9f)
                                else -> NewUiColors.Accent.copy(alpha = 0.85f)
                            },
                        )
                    }
                }
                NewUiConnectionProgressDots(
                    litCount = litCount,
                    visual = visual,
                    stepCount = stepCount,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
                )
                Text(
                    text = stepTitle,
                    style = NewUiTypography.ProgressStepTitle,
                    color = when (visual) {
                        NewUiProgressVisual.COMPLETE -> NewUiColors.StatusReady.copy(alpha = 0.95f)
                        NewUiProgressVisual.FAILED -> NewUiColors.HongqiRed.copy(alpha = 0.85f)
                        NewUiProgressVisual.ACTIVE -> NewUiColors.TextPrimary
                        NewUiProgressVisual.IDLE -> NewUiColors.TextSecondary
                    },
                )
                stepSubtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        val codeContent: @Composable (Modifier) -> Unit = { codeModifier ->
            pairingCode?.let { code ->
                Column(codeModifier
                    .testTag("carlink-pairing-code"),
                    verticalArrangement = Arrangement.spacedBy(NewUiLayout.NavTabGap)) {
                    Text(stringResource(R.string.new_ui_carlink_pairing_code),
                        style = NewUiTypography.SettingSubtitle, color = NewUiColors.TextSecondary)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(NewUiLayout.PairingCodeCellGap)) {
                            code.forEach { digit ->
                                Box(Modifier.weight(1f).height(NewUiLayout.PairingCodeCellHeight)
                                    .background(NewUiColors.CardSecondary, RoundedCornerShape(NewUiLayout.NavTabRadius / 2))
                                    .border(1.dp, NewUiColors.CardBorderTop, RoundedCornerShape(NewUiLayout.NavTabRadius / 2)),
                                    contentAlignment = Alignment.Center) {
                                    Text(digit.toString(), style = NewUiTypography.ProgressStepTitle.copy(fontFamily = FontFamily.Monospace),
                                        color = NewUiColors.TextPrimary, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pairingCode != null && availableWidth < NewUiLayout.PairingCodeSideBySideMinWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(NewUiLayout.MainGap), horizontalAlignment = Alignment.End) {
                progressContent(Modifier.fillMaxWidth())
                codeContent(Modifier.width(availableWidth.coerceAtMost(NewUiLayout.PairingCodeMaxWidth)))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NewUiLayout.MainGap)) {
                progressContent(Modifier.weight(1f))
                codeContent(Modifier.width((availableWidth * .46f).coerceAtMost(NewUiLayout.PairingCodeMaxWidth)))
            }
        }
    }
}

@Composable
fun NewUiConnectionProgressDots(
    litCount: Int,
    visual: NewUiProgressVisual,
    modifier: Modifier = Modifier,
    stepCount: Int = CarPlayConnectionProgress.STEP_COUNT,
) {
    Row(
        modifier = modifier.widthIn(max = 640.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(stepCount.coerceAtLeast(1)) { index ->
            NewUiConnectionProgressDot(
                index = index,
                litCount = litCount,
                visual = visual,
                stepCount = stepCount,
            )
        }
    }
}

@Composable
private fun NewUiConnectionProgressDot(
    index: Int,
    litCount: Int,
    visual: NewUiProgressVisual,
    stepCount: Int,
) {
    val reduceMotion = rememberNewUiReduceMotion()
    val duration = if (reduceMotion) 0 else NewUiMotion.ProgressDotDurationMs
    val lit = visual != NewUiProgressVisual.FAILED && index < litCount
    val failedDot = visual == NewUiProgressVisual.FAILED &&
        litCount > 0 &&
        index == litCount.coerceAtMost(stepCount - 1)
    val active = lit && index == litCount - 1 && visual == NewUiProgressVisual.ACTIVE
    val targetSize = if (active || failedDot) 7.dp else 6.dp
    val targetColor = when {
        failedDot -> NewUiColors.HongqiRed
        visual == NewUiProgressVisual.FAILED && index < litCount -> NewUiColors.Accent
        visual == NewUiProgressVisual.COMPLETE && lit -> NewUiColors.StatusReady
        lit && active -> NewUiColors.ProgressDotActive
        lit -> NewUiColors.Accent
        else -> NewUiColors.ProgressDotIdle
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "progressDotColor",
    )
    val size by animateDpAsState(
        targetValue = targetSize,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "progressDotSize",
    )
    Box(
        Modifier
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = NewUiColors.ProgressDotActive.copy(alpha = 0.85f),
                        spotColor = NewUiColors.ProgressDotActive.copy(alpha = 0.85f),
                    )
                } else {
                    Modifier
                },
            )
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

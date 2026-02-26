package com.motebaya.vaulten.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motebaya.vaulten.presentation.theme.VaultThemeExtras

/**
 * Skeuomorphic Card with soft shadows and depth.
 * Creates an elevated, tactile appearance with light and dark shadows.
 */
@Composable
fun SkeuoCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = skeuoColors.shadowDark.copy(alpha = 0.3f),
                spotColor = skeuoColors.shadowDark.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(skeuoColors.surfaceCard)
            .drawBehind {
                // Inner highlight (top-left)
                drawInnerHighlight(
                    highlightColor = skeuoColors.innerHighlight,
                    shadowColor = skeuoColors.innerShadow
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Skeuomorphic Surface for containing content with depth.
 */
@Composable
fun SkeuoSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = VaultThemeExtras.skeuomorphicColors.surfaceElevated,
    content: @Composable BoxScope.() -> Unit
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Box(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = skeuoColors.shadowDark.copy(alpha = 0.2f),
                spotColor = skeuoColors.shadowDark.copy(alpha = 0.2f)
            )
            .clip(shape)
            .background(backgroundColor),
        content = content
    )
}

/**
 * Skeuomorphic Button with press animation and tactile feel.
 */
@Composable
fun SkeuoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "buttonScale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else 6.dp,
        animationSpec = tween(durationMillis = 100),
        label = "buttonElevation"
    )
    
    val backgroundColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = skeuoColors.shadowDark.copy(alpha = 0.3f),
                spotColor = skeuoColors.shadowDark.copy(alpha = 0.3f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.85f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Skeuomorphic TextField Container with inset appearance.
 */
@Composable
fun SkeuoTextFieldContainer(
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isError: Boolean = false,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 150),
        label = "borderWidth"
    )
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(skeuoColors.surfaceBase)
            .border(borderWidth, borderColor, shape)
            .drawBehind {
                // Inset shadow effect
                drawInsetShadow(skeuoColors.innerShadow)
            },
        content = content
    )
}

/**
 * Skeuomorphic Warning Banner with prominent styling.
 */
@Composable
fun SkeuoWarningBanner(
    message: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(skeuoColors.warningContainer)
            .border(
                width = 1.dp,
                color = com.motebaya.vaulten.presentation.theme.Warning.copy(alpha = 0.5f),
                shape = shape
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = skeuoColors.onWarningContainer
            )
        }
    }
}

/**
 * Skeuomorphic divider with depth.
 */
@Composable
fun SkeuoDivider(
    modifier: Modifier = Modifier
) {
    val skeuoColors = VaultThemeExtras.skeuomorphicColors
    
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(skeuoColors.innerShadow)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(skeuoColors.innerHighlight)
        )
    }
}

// ============================================================================
// PRIVATE HELPER FUNCTIONS
// ============================================================================

private fun DrawScope.drawInnerHighlight(
    highlightColor: Color,
    shadowColor: Color
) {
    // Top-left highlight
    drawLine(
        color = highlightColor.copy(alpha = 0.5f),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = highlightColor.copy(alpha = 0.5f),
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx()
    )
    
    // Bottom-right shadow
    drawLine(
        color = shadowColor.copy(alpha = 0.3f),
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = shadowColor.copy(alpha = 0.3f),
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx()
    )
}

private fun DrawScope.drawInsetShadow(shadowColor: Color) {
    // Top inset shadow
    drawLine(
        color = shadowColor.copy(alpha = 0.4f),
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 2.dp.toPx()
    )
    // Left inset shadow
    drawLine(
        color = shadowColor.copy(alpha = 0.3f),
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 2.dp.toPx()
    )
}

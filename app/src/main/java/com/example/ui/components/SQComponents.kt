package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TokenStatus
import com.example.ui.theme.*

@Composable
fun SQButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: SQButtonVariant = SQButtonVariant.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String = "sq_button"
) {
    val containerColor = when (variant) {
        SQButtonVariant.PRIMARY -> MaterialTheme.colorScheme.primary
        SQButtonVariant.SECONDARY -> MaterialTheme.colorScheme.secondary
        SQButtonVariant.DANGER -> MaterialTheme.colorScheme.error
        SQButtonVariant.OUTLINE -> Color.Transparent
        SQButtonVariant.GHOST -> Color.Transparent
        SQButtonVariant.DARK -> Color(0xFF1B1B1F)
    }

    val contentColor = when (variant) {
        SQButtonVariant.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        SQButtonVariant.SECONDARY -> MaterialTheme.colorScheme.onSecondary
        SQButtonVariant.DANGER -> MaterialTheme.colorScheme.onError
        SQButtonVariant.OUTLINE -> MaterialTheme.colorScheme.primary
        SQButtonVariant.GHOST -> MaterialTheme.colorScheme.onSurface
        SQButtonVariant.DARK -> Color.White
    }

    val borderStroke = when (variant) {
        SQButtonVariant.OUTLINE -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        SQButtonVariant.DARK -> null
        else -> null
    }

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        border = borderStroke,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

enum class SQButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER,
    OUTLINE,
    GHOST,
    DARK
}

@Composable
fun SQCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    shapeRadius: Dp = 24.dp,
    elevation: Dp = 0.dp,
    testTag: String = "sq_card",
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick
        )
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .then(clickableModifier),
        shape = RoundedCornerShape(shapeRadius),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Bento Grid Standard Container Card
 */
@Composable
fun SQBentoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .then(clickableModifier)
            .padding(16.dp),
        content = content
    )
}

/**
 * Bento Grid Hero Card for Live Digital Tokens
 */
@Composable
fun SQBentoHeroCard(
    tokenNumber: String,
    status: TokenStatus,
    businessName: String,
    branchInfo: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(BentoPrimaryContainer)
            .border(1.dp, BentoSkyBorder, RoundedCornerShape(28.dp))
            .then(clickableModifier)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "YOUR CURRENT TOKEN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = BentoOnPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tokenNumber,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    fontFamily = FontFamily.SansSerif
                ),
                color = BentoOnPrimaryContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            
            // Status Pill
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = BentoPrimary,
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status == TokenStatus.CALLED || status == TokenStatus.SERVING) {
                        SQLiveDot(color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Status: ${status.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$businessName • $branchInfo",
                style = MaterialTheme.typography.bodySmall,
                color = BentoOnPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Bento Grid Equalizer Wave Indicator for Queue Health / Estimated Wait
 */
@Composable
fun SQBentoEqualizerBars(
    color: Color = BentoPrimary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(18.dp)
                .background(color, shape = RoundedCornerShape(100.dp))
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(30.dp)
                .background(color, shape = RoundedCornerShape(100.dp))
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(24.dp)
                .background(color.copy(alpha = 0.45f), shape = RoundedCornerShape(100.dp))
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(14.dp)
                .background(color.copy(alpha = 0.25f), shape = RoundedCornerShape(100.dp))
        )
    }
}

@Composable
fun SQStatusBadge(
    status: TokenStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        TokenStatus.WAITING -> Triple(BentoPrimaryContainer, BentoOnPrimaryContainer, "WAITING")
        TokenStatus.CALLED -> Triple(BentoLavenderContainer, BentoOnLavenderContainer, "CALLED")
        TokenStatus.SERVING -> Triple(BentoMintContainer, BentoOnMintContainer, "SERVING NOW")
        TokenStatus.SERVED -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), "SERVED")
        TokenStatus.SKIPPED -> Triple(BentoLavenderContainer, BentoOnLavenderContainer, "SKIPPED")
        TokenStatus.NO_SHOW -> Triple(BentoRoseContainer, BentoOnRoseContainer, "NO SHOW")
        TokenStatus.CANCELLED -> Triple(BentoRoseContainer, BentoOnRoseContainer, "CANCELLED")
        TokenStatus.EXPIRED -> Triple(Color(0xFFE2E8F0), Color(0xFF64748B), "EXPIRED")
        TokenStatus.BLOCKED -> Triple(BentoRoseContainer, BentoOnRoseContainer, "BLOCKED")
        TokenStatus.CREATED -> Triple(BentoSkyContainer, BentoOnSkyContainer, "CREATED")
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(100.dp)),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status == TokenStatus.SERVING || status == TokenStatus.CALLED) {
                SQLiveDot(color = textColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
fun SQLiveDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color, shape = CircleShape)
    )
}

@Composable
fun SQStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SQEmptyState(
    title: String,
    description: String,
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(1.dp, BentoSkyBorder.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(22.dp))
            SQButton(
                text = actionLabel,
                onClick = onAction,
                variant = SQButtonVariant.PRIMARY
            )
        }
    }
}


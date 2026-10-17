package com.soltini.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.ui.theme.*

enum class BadgeStyle {
    ACTIVE,
    INACTIVE,
    ACCENT,
    PURPLE,
    WARNING,
    ERROR
}

/**
 * Floating white glassmorphic card (~92% white opacity, soft drop shadow, 24dp radius, no harsh border)
 * with subtle spring scale micro-animation and haptic feedback when clickable.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalMyraTokens.current.cardCornerRadius,
    elevation: Dp = 6.dp,
    containerColor: Color = LocalMyraTokens.current.cardColor,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val tokens = LocalMyraTokens.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "glassCardScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = tokens.shadowColor.copy(alpha = 0.14f),
                spotColor = tokens.shadowColor.copy(alpha = 0.18f)
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    )
                } else Modifier
            ),
        shape = shape,
        color = containerColor,
        border = tokens.cardBorder,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Overload accepting Dp for contentPadding.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalMyraTokens.current.cardCornerRadius,
    elevation: Dp = 6.dp,
    containerColor: Color = LocalMyraTokens.current.cardColor,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        elevation = elevation,
        containerColor = containerColor,
        onClick = onClick,
        contentPadding = PaddingValues(contentPadding),
        content = content
    )
}

/**
 * Circular pastel icon badge used on list items, cards, and headers.
 */
@Composable
fun PastelIconBadge(
    icon: ImageVector,
    backgroundColor: Color = BadgePurpleBg,
    badgeColor: Color = backgroundColor,
    iconTint: Color = BadgePurpleTint,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Pill-shaped button with purple-to-pink gradient fill, spring scale press animation, and haptic feedback.
 */
@Composable
fun GradientPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    brush: Brush = LocalMyraTokens.current.accentHorizontalGradient,
    contentColor: Color = Color.White,
    height: Dp = 48.dp
) {
    val shape = CircleShape
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillBtnScale"
    )

    val effectiveBrush = if (enabled) {
        brush
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFD5D2E0), Color(0xFFE2DFE9)))
    }
    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = shape,
                ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.25f),
                spotColor = LocalMyraTokens.current.accentSecondary.copy(alpha = 0.30f)
            )
            .clip(shape)
            .background(effectiveBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else TextMidGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) contentColor else TextMidGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GlassGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    brush: Brush = LocalMyraTokens.current.accentHorizontalGradient,
    contentColor: Color = Color.White,
    height: Dp = 48.dp
) {
    GradientPillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        brush = brush,
        contentColor = contentColor,
        height = height
    )
}

/**
 * Soft secondary pill button (white or pastel fill with soft shadow, spring scale, and haptic feedback).
 */
@Composable
fun GlassSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = GlassCardSolid,
    contentColor: Color = TextCharcoal,
    height: Dp = 44.dp
) {
    val shape = CircleShape
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = shape,
        color = if (enabled) containerColor else GlassSurfaceSubtle,
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (enabled) 4.dp else 0.dp,
                shape = shape,
                ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.12f),
                spotColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.16f)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else TextMidGray,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = if (enabled) contentColor else TextMidGray,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Circular floating button with purple-to-pink gradient fill (for mic buttons & FABs)
 * with spring bounce and haptic feedback.
 */
@Composable
fun GradientCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    iconSize: Dp = 26.dp,
    brush: Brush = LocalMyraTokens.current.accentGradient,
    iconTint: Color = Color.White,
    elevation: Dp = 8.dp
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "circleBtnScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(size)
            .shadow(
                elevation = elevation,
                shape = CircleShape,
                ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.30f),
                spotColor = LocalMyraTokens.current.accentSecondary.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(brush)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GlassCircularActionButton(
    icon: ImageVector,
    contentDescription: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp
) {
    GradientCircleButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize
    )
}

/**
 * Status Pill Badge (e.g., "Live", "Active", "Connected", "Offline").
 * Green fill when active/live, soft gray when inactive.
 */
@Composable
fun GlassStatusBadge(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeBg: Color = BadgeMintBg,
    activeContent: Color = SecondaryMintDark,
    inactiveBg: Color = GlassSurfaceSubtle,
    inactiveContent: Color = TextMidGray,
    showDot: Boolean = true
) {
    val bg by animateColorAsState(if (isActive) activeBg else inactiveBg, tween(250), label = "badgeBg")
    val fg by animateColorAsState(if (isActive) activeContent else inactiveContent, tween(250), label = "badgeFg")

    Surface(
        color = bg,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(fg)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Overload supporting BadgeStyle enum.
 */
@Composable
fun GlassStatusBadge(
    text: String,
    style: BadgeStyle,
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    val (bg, fg) = when (style) {
        BadgeStyle.ACTIVE -> BadgeMintBg to SecondaryMintDark
        BadgeStyle.INACTIVE -> GlassSurfaceSubtle to TextMidGray
        BadgeStyle.ACCENT, BadgeStyle.PURPLE -> BadgePurpleBg to BadgePurpleTint
        BadgeStyle.WARNING -> BadgeYellowBg to AmberConnecting
        BadgeStyle.ERROR -> Color(0xFFFDE8E8) to RedError
    }
    Surface(
        color = bg,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(fg)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Quick Action / Capability Shortcut Pill Chip with a pastel icon badge + label,
 * spring scale feedback, and haptic tick.
 */
@Composable
fun GlassQuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeBg: Color = BadgePurpleBg,
    badgeTint: Color = BadgePurpleTint,
    isSelected: Boolean = false
) {
    val shape = CircleShape
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = shape,
        color = if (isSelected) BadgePurpleBg else GlassCardWhite,
        modifier = modifier.shadow(
            elevation = 4.dp,
            shape = shape,
            ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.12f),
            spotColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.16f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) LocalMyraTokens.current.accent else badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else badgeTint,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = TextCharcoal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

/**
 * White pill-shaped search / text input bar with soft shadow and leading icon.
 */
@Composable
fun GlassPillSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true
) {
    val shape = CircleShape
    Surface(
        shape = shape,
        color = GlassCardSolid,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.12f),
                spotColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.16f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = BadgePurpleTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextMidGray,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        color = TextCharcoal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(PrimaryDeepPurple),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

/**
 * Flexible white pill-shaped input field with optional leadingIcon, trailingIcon / trailingContent, and visualTransformation.
 */
@Composable
fun GlassPillInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val shape = if (singleLine) CircleShape else RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = GlassCardSolid,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = shape,
                ambientColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.12f),
                spotColor = LocalMyraTokens.current.shadowColor.copy(alpha = 0.16f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon ?: Icons.Default.Edit,
                contentDescription = null,
                tint = BadgePurpleTint,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextMidGray,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        color = TextCharcoal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(PrimaryDeepPurple),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val trailing = trailingContent ?: trailingIcon
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/**
 * Rounded Switch colors matching the design system:
 * Mint green (#A9E8C9) when ON, light gray when OFF.
 */
@Composable
fun glassSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = SecondaryMint,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = Color(0xFFDCD9E6),
    uncheckedBorderColor = Color.Transparent
)

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = glassSwitchColors()
    )
}

/**
 * OutlinedTextField colors styled for the light glassmorphic theme.
 */
@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextCharcoal,
    unfocusedTextColor = TextCharcoal,
    focusedContainerColor = GlassCardSolid,
    unfocusedContainerColor = GlassSurfaceSubtle,
    focusedBorderColor = LocalMyraTokens.current.accent,
    unfocusedBorderColor = SoftDivider,
    cursorColor = PrimaryDeepPurple,
    focusedLabelColor = BadgePurpleTint,
    unfocusedLabelColor = TextMidGray,
    focusedPlaceholderColor = TextMidGray,
    unfocusedPlaceholderColor = TextMidGray
)

/**
 * Section header for settings/lists.
 */
@Composable
fun GlassSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            text = title,
            color = TextCharcoal,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMidGray,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Standard glass list item card: white rounded card, colored circular icon badge on the left,
 * title + small gray subtitle, action control (toggle/chevron/icon) on the right.
 */
@Composable
fun GlassListItemCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badgeColor: Color = BadgePurpleBg,
    iconTint: Color = BadgePurpleTint,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        elevation = 5.dp,
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelIconBadge(
                icon = icon,
                badgeColor = badgeColor,
                iconTint = iconTint,
                size = 44.dp,
                iconSize = 21.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextCharcoal,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextMidGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null && showChevron) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMidGray,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Code / Log dark monospace block wrapped inside a white rounded outer container with soft shadow.
 */
@Composable
fun GlassCodeContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        elevation = 6.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        if (title != null || headerTrailing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = TextCharcoal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                if (headerTrailing != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, content = headerTrailing)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Surface(
            color = CodeBlockDarkBg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                content = content
            )
        }
    }
}

@Composable
fun GlassCodeBlockContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    headerTrailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCodeContainer(
        modifier = modifier,
        title = title,
        headerTrailing = headerTrailing,
        content = content
    )
}

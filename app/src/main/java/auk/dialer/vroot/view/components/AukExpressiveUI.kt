package auk.dialer.vroot.view.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import auk.dialer.vroot.R
import auk.dialer.vroot.controller.util.PreferenceManager
import auk.dialer.vroot.view.theme.LocalAccentBarColor
import auk.dialer.vroot.view.theme.LocalOnAccentBarColor
import auk.dialer.vroot.view.theme.LocalCardRoundness
import auk.dialer.vroot.view.theme.AukMaterialShapes
import auk.dialer.vroot.view.theme.AukMotion
import auk.dialer.vroot.view.theme.AukShapeDefaults
import auk.dialer.vroot.view.theme.rememberAukMorphShape
import auk.dialer.vroot.view.theme.aukCornerDp
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Opacity of an unselected filter chip sitting on the accent band. */
private const val AccentChipContainerAlpha = 0.16f

object AukElevation {
    val Flat: Dp = 0.dp
    val Raised: Dp = 3.dp
    val Floating: Dp = 6.dp
}

@Immutable
data class AukSurfaceStyle(
    val showCards: Boolean,
    val showDividers: Boolean
)

val LocalAukSurfaceStyle: ProvidableCompositionLocal<AukSurfaceStyle?> =
    staticCompositionLocalOf { null }

@Composable
fun rememberAukSurfaceStyle(prefs: PreferenceManager = koinInject()): AukSurfaceStyle {
    val settingsVersion by prefs.settingsChanged.collectAsState()
    val showCards = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_CARDS, true)
    }
    val showDividers = remember(settingsVersion) {
        prefs.getBoolean(PreferenceManager.KEY_SHOW_DIVIDERS, true)
    }
    return remember(showCards, showDividers) { AukSurfaceStyle(showCards, showDividers) }
}

@Composable
fun aukSurfaceStyle(): AukSurfaceStyle {
    val provided = LocalAukSurfaceStyle.current
    if (provided != null) return provided
    return rememberAukSurfaceStyle()
}

object AukListItemDefaults {
    val MinHeight: Dp = 48.dp
    val AvatarSize: Dp = 40.dp
    val CompactAvatarSize: Dp = 42.dp
    val HorizontalPadding: Dp = 6.dp
    val CompactHorizontalPadding: Dp = 10.dp
    val VerticalPadding: Dp = 6.dp
    val CompactVerticalPadding: Dp = 6.dp
    val Spacing: Dp = 12.dp
    val CompactSpacing: Dp = 14.dp
    val TrailingSpacing: Dp = 8.dp
    val TrailingIconSize: Dp = 20.dp

    @Composable
    fun headlineStyle(): TextStyle = MaterialTheme.typography.titleMedium

    @Composable
    fun supportingStyle(): TextStyle = MaterialTheme.typography.bodyMedium

    @Composable
    fun metaStyle(): TextStyle = MaterialTheme.typography.labelMedium

    @Composable
    fun shape(): Shape = MaterialTheme.shapes.extraLarge
}

enum class AukIconTileSize { Medium, Large }

@Composable
fun AukLeadingIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: AukIconTileSize = AukIconTileSize.Medium,
    selected: Boolean = false,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    val tileSize = if (size == AukIconTileSize.Large) 64.dp else 44.dp
    val iconSize = if (size == AukIconTileSize.Large) 32.dp else 20.dp
    val shape = if (size == AukIconTileSize.Large) {
        MaterialTheme.shapes.largeIncreased
    } else {
        MaterialTheme.shapes.medium
    }
    val resolvedContainer = when {
        containerColor.isSpecified -> containerColor
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val resolvedContent = when {
        contentColor.isSpecified -> contentColor
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier.size(tileSize),
        shape = shape,
        color = resolvedContainer,
        contentColor = resolvedContent,
        shadowElevation = AukElevation.Flat
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * The thin gradient edge every card carries: a sweep between the chosen accent and a hue shifted
 * sibling of it.
 *
 * The card itself stays plain and legible, and its edge is what ties it to the accent, rather than
 * tinting the card or dropping a shadow under it. The hue swing is wide, 70 degrees, or the two ends
 * read as one flat tone; the sibling loses a little saturation and value too so it does not just
 * look like the same colour turned up.
 */
@Composable
fun aukAccentCardBorder(width: Dp = 1.dp): BorderStroke {
    val accent = MaterialTheme.colorScheme.primary
    val brush = remember(accent) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        val sibling = Color(
            android.graphics.Color.HSVToColor(
                floatArrayOf(
                    (hsv[0] + AccentBorderHueShift).mod(360f),
                    (hsv[1] * 0.85f).coerceIn(0f, 1f),
                    (hsv[2] * 0.9f).coerceIn(0f, 1f)
                )
            )
        )
        Brush.linearGradient(listOf(accent, sibling, accent))
    }
    return BorderStroke(width, brush)
}

private const val AccentBorderHueShift = 70f

/** How much of the card's own colour sits over the wash behind it. */
private const val AukCardVeilAlpha = 0.55f

@Composable
fun AukExpressiveCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    shape: Shape? = null,
    containerColor: Color? = null,
    isCompact: Boolean = false,
    showCards: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardsEnabled = showCards ?: aukSurfaceStyle().showCards
    val resolvedShape = shape ?: MaterialTheme.shapes.extraLarge

    val padding = if (isCompact) 12.dp else 16.dp
    val spacing = if (isCompact) 8.dp else 12.dp
    // Sides are much tighter than top and bottom on purpose. The screen already
    // insets the card and the rows inset their own content, so a symmetric card
    // padding was a third margin stacked on the other two, and rows were losing
    // a fifth of the screen width to whitespace.
    val horizontalPadding = if (isCompact) 4.dp else 8.dp

    if (cardsEnabled) {
        // No colour of its own: a thin neutral veil over whatever is already drawn behind it, the
        // one continuous wash the theme paints once behind every screen. That is what keeps a card
        // reading as the same background as the page around it rather than as a second, separately
        // computed patch of colour that stops dead at the card's own edge.
        val resolvedContainerColor = containerColor
            ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = AukCardVeilAlpha)
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = resolvedShape,
            colors = CardDefaults.cardColors(
                containerColor = resolvedContainerColor,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = AukElevation.Flat),
            border = aukAccentCardBorder()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = padding),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                if (title != null || icon != null) {
                    AukSectionHeader(
                        title = title.orEmpty(),
                        icon = icon,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                content()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (title != null || icon != null) {
                AukSectionHeader(title = title.orEmpty(), icon = icon)
            }
            content()
        }
    }
}

@Composable
fun AukDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    visible: Boolean? = null
) {
}

@Composable
fun AukSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun AukExpressiveButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 64.dp,
    iconSize: Dp = 24.dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val roundness = LocalCardRoundness.current

    val restCorner = aukCornerDp((size.value * 0.45f).roundToInt(), roundness)
    val pressedCorner = aukCornerDp((size.value * 0.26f).roundToInt(), roundness)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) pressedCorner else restCorner,
        animationSpec = AukMotion.pressFeedback(),
        label = "AukExpressiveButtonCorner"
    )

    val description = contentDescription
    val hasVisibleLabel = label != null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            if (!hasVisibleLabel && description != null) {
                this.contentDescription = description
            }
        }
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .height(size)
                .widthIn(max = size * 1.3f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            contentColor = contentColor,
            interactionSource = interactionSource,
            shadowElevation = AukElevation.Flat
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize)
                    )
                } else if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = Color.Unspecified
                    )
                }
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AukListItem(
    headline: String,
    supporting: String? = null,
    supporting2: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    avatarName: String? = null,
    photoUri: String? = null,
    avatarShape: Shape? = null,
    badgeIcon: ImageVector? = null,
    badgeColor: Color? = null,
    headlineColor: Color = Color.Unspecified,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectable: Boolean = false,
    toggled: Boolean? = null,
    role: Role? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    containerColor: Color = Color.Unspecified,
    headlineStyle: TextStyle = AukListItemDefaults.headlineStyle(),
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val verticalPadding = if (isCompact) {
        AukListItemDefaults.CompactVerticalPadding
    } else {
        AukListItemDefaults.VerticalPadding
    }
    val horizontalPadding = if (isCompact) {
        AukListItemDefaults.CompactHorizontalPadding
    } else {
        AukListItemDefaults.HorizontalPadding
    }
    val avatarSize = if (isCompact) {
        AukListItemDefaults.CompactAvatarSize
    } else {
        AukListItemDefaults.AvatarSize
    }
    val spacing = if (isCompact) {
        AukListItemDefaults.CompactSpacing
    } else {
        AukListItemDefaults.Spacing
    }

    val targetContainer = when {
        containerColor.isSpecified -> containerColor
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val animatedContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = AukMotion.colorChange(),
        label = "AukListItemContainer"
    )

    val isSelected = selected
    val toggleState = toggled
    val resolvedRole = role ?: if (selectable) Role.Checkbox else Role.Button
    val selectedDescription = stringResource(R.string.content_desc_selected_item)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val itemScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = AukMotion.pressFeedback(),
        label = "AukListItemScale"
    )

    val roundness = LocalCardRoundness.current
    val restCorner = aukCornerDp(24, roundness)
    val pressedCorner = aukCornerDp(12, roundness)
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isPressed) pressedCorner else restCorner,
        animationSpec = AukMotion.shapeMorph(),
        label = "AukListItemCorner"
    )

    Surface(
        color = animatedContainer,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            LocalContentColor.current
        },
        shape = RoundedCornerShape(animatedCornerRadius),
        shadowElevation = AukElevation.Flat,
        modifier = modifier
            .fillMaxWidth()
            .scale(itemScale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = resolvedRole,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                this.selected = isSelected
                this.role = resolvedRole
                if (toggleState != null) {
                    this.toggleableState = ToggleableState(toggleState)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AukListItemDefaults.MinHeight)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(spacing))
            } else if (selected) {
                val checkShape = avatarShape ?: aukAvatarStyle().shape
                Surface(
                    modifier = Modifier.size(avatarSize),
                    shape = checkShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = AukElevation.Flat
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = selectedDescription,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(spacing))
            } else if (avatarName != null || photoUri != null) {
                AukAvatar(
                    name = avatarName ?: "",
                    photoUri = photoUri,
                    badgeIcon = badgeIcon,
                    badgeColor = badgeColor,
                    shape = avatarShape,
                    modifier = Modifier.size(avatarSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            } else if (leadingIcon != null) {
                AukLeadingIconTile(icon = leadingIcon)
                Spacer(modifier = Modifier.width(spacing))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = headlineStyle,
                    color = headlineColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = AukListItemDefaults.supportingStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (supporting2 != null) {
                    Text(
                        text = supporting2,
                        style = AukListItemDefaults.metaStyle(),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                supportingContent?.invoke(this)
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AukListItemDefaults.TrailingIconSize)
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
                trailingContent()
            }
        }
    }
}

@Composable
fun AukSwitchListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AukListItem(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        enabled = enabled,
        toggled = checked,
        role = Role.Switch,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

@Composable
fun AukSelectListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    preview: (@Composable (Int) -> Unit)? = null
) {
    var showSelectionScreen by remember { mutableStateOf(false) }

    AukListItem(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        onClick = { showSelectionScreen = true },
        modifier = modifier,
        enabled = enabled,
        trailingContent = {
            if (preview != null) {
                preview(selectedValue)
                Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.content_desc_select_option),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AukListItemDefaults.TrailingIconSize)
            )
        }
    )

    if (showSelectionScreen) {
        AukSelectionDialog(
            onDismissRequest = { showSelectionScreen = false },
            title = headline,
            icon = leadingIcon,
            items = options,
            itemLabel = { it.first },
            onItemSelected = { onValueChange(it.second) },
            itemPreview = preview?.let { p -> { option -> p(option.second) } },
            isSelected = { it.second == selectedValue }
        )
    }
}

@Composable
fun AukFilterChip(
    label: String,
    selected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAllFilter: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onAccentBar: Boolean = false
) {
    val roundness = LocalCardRoundness.current
    val accent = LocalAccentBarColor.current
    val onAccent = LocalOnAccentBarColor.current
    // On the accent band the usual colours vanish: the selected chip is filled with primary, which
    // is the band itself. Swapped for the band's own pair instead.
    val chipColors = if (onAccentBar) {
        FilterChipDefaults.filterChipColors(
            containerColor = onAccent.copy(alpha = AccentChipContainerAlpha),
            labelColor = onAccent,
            iconColor = onAccent,
            selectedContainerColor = onAccent,
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    } else {
        FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    }
    FilterChip(
        selected = selected,
        onClick = { onClick(label) },
        label = {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                }
            )
        },
        shapes = FilterChipDefaults.shapes(
            shape = RoundedCornerShape(aukCornerDp(AukShapeDefaults.BaseLargeIncreased, roundness)),
            selectedShape = RoundedCornerShape(aukCornerDp(AukShapeDefaults.BaseSmall, roundness)),
            pressedShape = RoundedCornerShape(aukCornerDp(AukShapeDefaults.BaseExtraSmall, roundness))
        ),
        modifier = modifier,
        enabled = enabled,
        colors = chipColors,
        leadingIcon = leadingIcon ?: if (isAllFilter) {
            {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        border = null,
        elevation = null
    )
}

@Composable
fun AukToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        shapes = ToggleButtonDefaults.shapes(
            shape = MaterialTheme.shapes.largeIncreased,
            pressedShape = MaterialTheme.shapes.small,
            checkedShape = MaterialTheme.shapes.extraLarge
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A single colour chip. Circle when idle, cookie shape with a check when it is
 * the current choice.
 */
@Composable
fun AukColorSwatch(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = AukColorSwatchDefaults.Size
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AukMotion.shapeMorph(),
        label = "AukColorSwatchMorph"
    )
    val shape = rememberAukMorphShape(
        AukMaterialShapes.Circle,
        AukMaterialShapes.Cookie9Sided
    ) { progress }
    val onColor = if (color.luminance() > 0.5f) {
        MaterialTheme.colorScheme.scrim
    } else {
        MaterialTheme.colorScheme.surface
    }
    val selectedDescription = stringResource(R.string.content_desc_selected_item)

    val check: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            if (progress > 0f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = selectedDescription,
                    modifier = Modifier.size(size * 0.45f * progress)
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.size(size),
            shape = shape,
            color = color,
            contentColor = onColor,
            shadowElevation = AukElevation.Flat,
            content = check
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = shape,
            color = color,
            contentColor = onColor,
            shadowElevation = AukElevation.Flat,
            content = check
        )
    }
}

object AukColorSwatchDefaults {
    val Size: Dp = 44.dp
    val TrailingSize: Dp = 28.dp
}

/**
 * Settings row for a colour choice: the current colour sits at the end of the
 * row, and the palette itself opens in a dialog rather than taking up the
 * screen.
 */
@Composable
fun AukColorSelectListItem(
    headline: String,
    colors: List<Color>,
    selectedColor: Color?,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    AukListItem(
        headline = headline,
        supporting = supporting,
        leadingIcon = leadingIcon,
        onClick = { showPicker = true },
        modifier = modifier,
        enabled = enabled,
        trailingContent = {
            AukColorSwatch(
                color = selectedColor ?: MaterialTheme.colorScheme.primary,
                selected = false,
                size = AukColorSwatchDefaults.TrailingSize
            )
            Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.content_desc_select_option),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AukListItemDefaults.TrailingIconSize)
            )
        }
    )

    if (showPicker) {
        AukColorPickerDialog(
            onDismissRequest = { showPicker = false },
            title = headline,
            icon = leadingIcon,
            colors = colors,
            selectedColor = selectedColor,
            onColorSelected = {
                showPicker = false
                onColorSelected(it)
            }
        )
    }
}

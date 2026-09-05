package auk.dialer.vroot.view.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import auk.dialer.vroot.view.theme.LocalCardRoundness
import auk.dialer.vroot.view.theme.AukMaterialShapes
import auk.dialer.vroot.view.theme.AukMotion
import auk.dialer.vroot.view.theme.AukShapeDefaults
import auk.dialer.vroot.view.theme.rememberAukMorphShape
import auk.dialer.vroot.view.theme.aukCornerDp
import auk.dialer.vroot.view.theme.aukAvatarShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import org.koin.compose.koinInject
import java.util.Locale
import kotlin.math.roundToInt

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
    val HorizontalPadding: Dp = 8.dp
    val CompactHorizontalPadding: Dp = 10.dp
    val VerticalPadding: Dp = 6.dp
    val CompactVerticalPadding: Dp = 6.dp
    val Spacing: Dp = 14.dp
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

@Composable
fun AukExpressiveCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    shape: Shape? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
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
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = resolvedShape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = AukElevation.Flat)
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
                    color = MaterialTheme.colorScheme.primary
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
    enabled: Boolean = true
) {
    val roundness = LocalCardRoundness.current
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
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
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

@Composable
fun AukSegmentedOptionRow(
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionIcon: ((Int) -> ImageVector?)? = null,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val optionValue = option.second
            val isSelected = optionValue == selectedValue
            val glyph = optionIcon?.invoke(optionValue)
            SegmentedButton(
                selected = isSelected,
                onClick = { onValueChange(optionValue) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {
                    if (glyph != null) {
                        Icon(
                            imageVector = glyph,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = isSelected)
                    }
                },
                label = {
                    Text(
                        text = option.first,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun AukOptionRow(
    headline: String,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    optionIcon: ((Int) -> ImageVector?)? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AukListItemDefaults.HorizontalPadding,
                vertical = AukListItemDefaults.VerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                AukLeadingIconTile(icon = leadingIcon)
                Spacer(modifier = Modifier.width(AukListItemDefaults.Spacing))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = AukListItemDefaults.headlineStyle(),
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
            }
        }
        AukSegmentedOptionRow(
            options = options,
            selectedValue = selectedValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            optionIcon = optionIcon,
            enabled = enabled
        )
    }
}

object AukPreviewTileDefaults {
    val Width: Dp = 108.dp
    val PreviewHeight: Dp = 84.dp
    val BadgeSize: Dp = 22.dp
    val BadgeIconSize: Dp = 14.dp
    val Spacing: Dp = 12.dp
}

@Composable
fun AukPreviewTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    width: Dp = AukPreviewTileDefaults.Width,
    previewHeight: Dp = AukPreviewTileDefaults.PreviewHeight,
    enabled: Boolean = true,
    previewContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable BoxScope.() -> Unit
) {
    val roundness = LocalCardRoundness.current
    val cornerTarget = if (selected) {
        aukCornerDp(AukShapeDefaults.BaseExtraLargeIncreased, roundness)
    } else {
        aukCornerDp(AukShapeDefaults.BaseLarge, roundness)
    }
    val corner by animateDpAsState(
        targetValue = cornerTarget,
        animationSpec = AukMotion.shapeMorph(),
        label = "AukPreviewTileCorner"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = AukMotion.pressFeedback(),
        label = "AukPreviewTileBorder"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = AukMotion.colorChange(),
        label = "AukPreviewTileBorderColor"
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = AukMotion.spatialFast(),
        label = "AukPreviewTileBadge"
    )
    val shape = RoundedCornerShape(corner)
    val selectedDescription = stringResource(R.string.content_desc_selected_item)

    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Surface(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight),
                shape = shape,
                color = previewContainerColor,
                border = BorderStroke(borderWidth, borderColor),
                shadowElevation = AukElevation.Flat
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    content = content
                )
            }
            if (badgeScale > 0f) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(AukPreviewTileDefaults.BadgeSize * badgeScale),
                    shape = AukShapeDefaults.Full,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = AukElevation.Flat
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = selectedDescription,
                            modifier = Modifier.size(AukPreviewTileDefaults.BadgeIconSize * badgeScale)
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            style = if (selected) {
                MaterialTheme.typography.labelMediumEmphasized
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AukAvatarShapeSelectorRow(
    headline: String,
    supporting: String?,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AukListItemDefaults.VerticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AukListItemDefaults.HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AukLeadingIconTile(icon = Icons.Outlined.AccountBox)
            Spacer(modifier = Modifier.width(AukListItemDefaults.Spacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = AukListItemDefaults.headlineStyle(),
                    maxLines = 1,
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
            }
            Spacer(modifier = Modifier.width(12.dp))
            val currentShape = aukAvatarShape(selectedValue)
            Surface(
                modifier = Modifier.size(44.dp),
                shape = currentShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "R",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = AukListItemDefaults.HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(options) { (label, shapeIndex) ->
                val selected = shapeIndex == selectedValue
                val shape = aukAvatarShape(shapeIndex)

                AukPreviewTile(
                    label = label,
                    selected = selected,
                    onClick = { onValueChange(shapeIndex) },
                    width = 88.dp,
                    previewHeight = 72.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                shape = shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "R",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AukVisualOptionSelectorRow(
    headline: String,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    tileWidth: Dp = 100.dp,
    tileHeight: Dp = 72.dp,
    optionContent: @Composable BoxScope.(Int, Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AukListItemDefaults.VerticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AukListItemDefaults.HorizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                AukLeadingIconTile(icon = leadingIcon)
                Spacer(modifier = Modifier.width(AukListItemDefaults.Spacing))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = AukListItemDefaults.headlineStyle(),
                    maxLines = 1,
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
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = AukListItemDefaults.HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(options) { (label, value) ->
                val selected = value == selectedValue

                AukPreviewTile(
                    label = label,
                    selected = selected,
                    onClick = { onValueChange(value) },
                    width = tileWidth,
                    previewHeight = tileHeight
                ) {
                    optionContent(value, selected)
                }
            }
        }
    }
}

@Composable
fun AukInteractiveRoundnessSlider(
    headline: String,
    supporting: String?,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AukListItemDefaults.HorizontalPadding,
                vertical = AukListItemDefaults.VerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AukLeadingIconTile(icon = Icons.Outlined.RoundedCorner)
            Spacer(modifier = Modifier.width(AukListItemDefaults.Spacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = AukListItemDefaults.headlineStyle()
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = AukListItemDefaults.supportingStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(value.coerceAtLeast(1f).dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Live Corner Roundness: ${value.roundToInt()}dp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AukPreviewTileRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AukPreviewTileDefaults.Spacing),
        verticalAlignment = Alignment.Top,
        content = content
    )
}

@Composable
fun AukSliderListItem(
    headline: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val readout = valueLabel ?: String.format(Locale.getDefault(), "%d", value.roundToInt())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AukListItemDefaults.HorizontalPadding,
                vertical = AukListItemDefaults.VerticalPadding
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                AukLeadingIconTile(icon = leadingIcon)
                Spacer(modifier = Modifier.width(AukListItemDefaults.Spacing))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = AukListItemDefaults.headlineStyle(),
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
            }
            Spacer(modifier = Modifier.width(AukListItemDefaults.TrailingSpacing))
            Text(
                text = readout,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
fun aukSwatchPalette(count: Int = 12): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(count, dark) {
        List(count) { index ->
            val hue = index * (360f / count)
            Color(
                androidx.core.graphics.ColorUtils.HSLToColor(
                    floatArrayOf(
                        hue,
                        if (dark) 0.55f else 0.68f,
                        if (dark) 0.62f else 0.46f
                    )
                )
            )
        }
    }
}

@Composable
fun AukColorSwatchRow(
    colors: List<Color>,
    selectedColor: Color?,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 44.dp,
    enabled: Boolean = true,
    swatchContentDescription: ((Color) -> String)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val selectedDescription = stringResource(R.string.content_desc_selected_item)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        colors.forEach { swatch ->
            key(swatch.value) {
                val isSelected = selectedColor != null && selectedColor == swatch
                val progress by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = AukMotion.shapeMorph(),
                    label = "AukSwatchMorph"
                )
                val swatchShape = rememberAukMorphShape(
                    AukMaterialShapes.Circle,
                    AukMaterialShapes.Cookie9Sided
                ) { progress }
                val onSwatch = if (swatch.luminance() > 0.5f) {
                    MaterialTheme.colorScheme.scrim
                } else {
                    MaterialTheme.colorScheme.surface
                }
                Surface(
                    selected = isSelected,
                    onClick = { onColorSelected(swatch) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(swatchSize)
                        .semantics {
                            val description = swatchContentDescription?.invoke(swatch)
                            if (description != null) {
                                this.contentDescription = description
                            }
                        },
                    shape = swatchShape,
                    color = swatch,
                    contentColor = onSwatch,
                    shadowElevation = AukElevation.Flat
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (progress > 0f) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = selectedDescription,
                                modifier = Modifier.size(20.dp * progress)
                            )
                        }
                    }
                }
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

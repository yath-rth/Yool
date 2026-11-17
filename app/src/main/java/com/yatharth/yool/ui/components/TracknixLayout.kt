package com.yatharth.yool.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg

/*
 * Tracknix layout + structure pieces: top bar, icon buttons, grouped list rows, dialogs, menus,
 * collapsible sections, metric tiles and the bottom bar. Screens should be composed from these
 * (plus Tracknix.kt) — never raw Material AlertDialog / TextButton / NavigationBar.
 *
 * UX rules these pieces encode:
 *  - One primary action per screen (coral filled TxButton); everything else is secondary.
 *  - Summaries first, details on demand (TxCollapsible, tap-to-expand tiles, dialogs).
 *  - Related settings/links live in one TxListGroup instead of a wall of cards.
 */

private val Shape4 = RoundedCornerShape(4.dp)

// ── Icon button + top bar ────────────────────────────────────────────────────

/** Square bordered icon button with a small hard shadow that sinks on press. */
@Composable
fun TxIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Ink,
    bg: Color = ItemsBg,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed) 2.dp else 0.dp, tween(50), label = "press")
    Box(modifier.padding(end = 2.dp, bottom = 2.dp)) {
        Box(Modifier.matchParentSize().offset(2.dp, 2.dp).background(Ink, Shape4))
        Box(
            Modifier
                .size(size)
                .offset(press, press)
                .clip(Shape4)
                .background(if (enabled) bg else SecondaryBg, Shape4)
                .border(1.5.dp, Ink, Shape4)
                .clickable(interaction, null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, Modifier.size(size * 0.5f), tint = if (enabled) tint else tint.copy(alpha = 0.3f))
        }
    }
}

/**
 * Screen header. With [onBack] it is a sub-screen header (bordered back button + title);
 * without it, a top-level tab title. [actions] sit on the right (TxIconButtons, a small TxButton).
 */
@Composable
fun TxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) TxIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(
                title.uppercase(),
                style = if (onBack != null) MaterialTheme.typography.titleSmall.copy(letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing)
                else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ── Grouped list ─────────────────────────────────────────────────────────────

/** Hairline separator used inside cards. */
@Composable
fun TxDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.15f)))
}

/** One ShadowCard holding a stack of [TxListRow]s separated by hairlines. */
@Composable
fun TxListGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) SectionLabel(title)
        ShadowCard(contentPadding = 0.dp) {
            Column(content = content)
        }
    }
}

/**
 * A tappable row: optional leading icon, title + one-line subtitle, optional trailing value, chevron.
 * Use [divider] = false on the last row of a group.
 */
@Composable
fun TxListRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Ink,
    icon: ImageVector? = null,
    iconTint: Color = Ink,
    chevron: Boolean = onClick != null,
    divider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = valueColor)
            trailing?.invoke()
            if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = Ink.copy(alpha = 0.6f))
        }
        if (divider) TxDivider(Modifier.padding(horizontal = 16.dp))
    }
}

// ── Progressive disclosure ───────────────────────────────────────────────────

/**
 * Card whose body is hidden until the header is tapped. The header shows [title] and a one-line
 * [summary] so the collapsed state still answers "what's in here?".
 */
@Composable
fun TxCollapsible(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    key: String = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(key) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(120), label = "chevron")
    ShadowCard(modifier, contentPadding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f))
            }
            Icon(Icons.Outlined.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.rotate(rotation))
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(120)), exit = shrinkVertically(tween(120))) {
            Column {
                TxDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

// ── Metric tile ──────────────────────────────────────────────────────────────

/**
 * Compact stat tile for 2-column grids: caps label, big value + unit, optional thin progress,
 * optional one-line [footnote]. A coloured top strip keys it to its metric.
 */
@Composable
fun TxMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = Ink,
    progress: Float? = null,
    progressColor: Color = color,
    footnote: String? = null,
    footnoteColor: Color = Ink.copy(alpha = 0.7f),
    onClick: (() -> Unit)? = null,
) {
    ShadowCard(modifier, onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)).border(1.dp, Ink, RoundedCornerShape(2.dp)))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.8f), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = if (value == "—") Ink.copy(alpha = 0.35f) else Ink, maxLines = 1)
            if (unit != null && value != "—") Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (progress != null) TxProgress(progress, color = progressColor, height = 8.dp)
        if (footnote != null) Text(footnote, style = MaterialTheme.typography.labelSmall, color = footnoteColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Small filled tag (e.g. PR, TODAY, IMPORTED). */
@Composable
fun TxBadge(text: String, modifier: Modifier = Modifier, color: Color = Ink, textColor: Color = ItemsBg) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = textColor,
        modifier = modifier.background(color, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

// ── Dialogs + menus ──────────────────────────────────────────────────────────

/** The one Tracknix modal: a ShadowCard in a dialog window, scrollable for the keyboard. */
@Composable
fun TxDialog(
    title: String?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 20.dp)) {
            ShadowCard {
                Column(
                    Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (title != null) Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
                    content()
                }
            }
        }
    }
}

/** Two buttons at the bottom of a dialog: secondary on the left, primary (filled) on the right. */
@Composable
fun TxDialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TxButton(dismissLabel, onDismiss, Modifier.weight(1f))
        TxButton(confirmLabel, onConfirm, Modifier.weight(1f), bg = if (destructive) Coral else Ink, textColor = ItemsBg, enabled = confirmEnabled)
    }
}

@Composable
fun TxConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = "Cancel",
) {
    TxDialog(title, onDismiss) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TxDialogButtons(confirmLabel, onConfirm, onDismiss, dismissLabel, destructive)
    }
}

@Composable
fun TxPromptDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    numeric: Boolean = false,
    maxLength: Int? = 60,
) {
    var text by remember { mutableStateOf(initial) }
    TxDialog(title, onDismiss) {
        TxTextField(text, { text = it }, label, Modifier.fillMaxWidth(), numeric = numeric, maxLength = maxLength)
        TxDialogButtons(confirmLabel, { onConfirm(text.trim()) }, onDismiss, confirmEnabled = text.isNotBlank())
    }
}

/** A stacked list of full-width actions inside a dialog (replaces overflow menus with many items). */
@Composable
fun TxActionSheet(title: String?, onDismiss: () -> Unit, actions: List<Pair<String, () -> Unit>>, destructive: Set<String> = emptySet()) {
    TxDialog(title, onDismiss) {
        Column {
            actions.forEachIndexed { i, (label, action) ->
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (label in destructive) Coral else Ink,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 14.dp),
                )
                if (i < actions.lastIndex) TxDivider()
            }
        }
    }
}

/** Tracknix-styled dropdown: itemsBg, ink border, 4dp corners. */
@Composable
fun TxMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = Shape4,
        containerColor = ItemsBg,
        border = BorderStroke(1.5.dp, Ink),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun TxMenuItem(text: String, onClick: () -> Unit, color: Color = Ink) {
    DropdownMenuItem(
        text = { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = color) },
        onClick = onClick,
    )
}

// ── Bottom navigation ────────────────────────────────────────────────────────

data class TxNavItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Tracknix bottom bar: secondaryBg strip with a 2dp ink top rule. The selected tab becomes a
 * bordered itemsBg box with a hard shadow; the rest are flat ink icons with ExtraLight labels.
 */
@Composable
fun TxBottomBar(items: List<TxNavItem>, selectedKey: String?, onSelect: (TxNavItem) -> Unit) {
    Column(Modifier.fillMaxWidth().background(SecondaryBg)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val sel = item.key == selectedKey
                Box(Modifier.weight(1f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp)) {
                        if (sel) Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(Ink, Shape4))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(Shape4)
                                .background(if (sel) ItemsBg else Color.Transparent, Shape4)
                                .then(if (sel) Modifier.border(1.5.dp, Ink, Shape4) else Modifier)
                                .clickable { onSelect(item) }
                                .padding(horizontal = 2.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(item.icon, item.label, Modifier.size(22.dp), tint = if (sel) Coral else Ink)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.ExtraLight,
                                color = if (sel) Coral else Ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.yatharth.yool.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg

/*
 * Tracknix building blocks. Design DNA: hard ink shadow (offset box, not elevation),
 * 1.5–2dp ink borders, 4–5dp corners, ExtraLight labels / ExtraBold values.
 * Always build UI from these instead of raw Material components.
 */

private val CardShape = RoundedCornerShape(4.dp)
private val FieldShape = RoundedCornerShape(5.dp)

/** Card with hard 4dp offset shadow. Use for every content block. Sinks 2dp on press if clickable. */
@Composable
fun ShadowCard(
    modifier: Modifier = Modifier,
    bg: Color = ItemsBg,
    shadow: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed && onClick != null) 2.dp else 0.dp, tween(50), label = "press")
    // propagateMinConstraints: when the caller fixes the height (e.g. fillMaxHeight in an
    // IntrinsicSize.Min row) the content stretches with it instead of leaving shadow showing.
    Box(modifier.padding(end = shadow, bottom = shadow), propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .offset(shadow, shadow)
                .background(Ink, CardShape)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .offset(press, press)
                .clip(CardShape)
                .background(bg, CardShape)
                .border(1.5.dp, Ink, CardShape)
                .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Pressable button with hard shadow that sinks on press. */
@Composable
fun TxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = ItemsBg,
    textColor: Color = Ink,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    /** false = size to the label. Needed inside a Row next to weighted siblings, which a full-width button would starve. */
    fill: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(50), label = "press")
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(Ink, CardShape))
        Row(
            (if (fill) Modifier.fillMaxWidth() else Modifier)
                .offset(press, press)
                .clip(CardShape)
                .background(if (enabled) bg else SecondaryBg, CardShape)
                .border(1.5.dp, Ink, CardShape)
                .clickable(interaction, null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp))
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) textColor else Ink.copy(alpha = 0.4f),
            )
        }
    }
}

/** Square Tracknix switch. */
@Composable
fun TxSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val thumb by animateDpAsState(if (checked) 20.dp else 0.dp, tween(120), label = "thumb")
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(Ink, CardShape))
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CardShape)
                .background(if (checked) Green.copy(alpha = 0.15f).compositeOver(ItemsBg) else ItemsBg)
                .border(2.dp, Ink, CardShape)
                .clickable { onCheckedChange(!checked) }
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumb)
                    .size(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (checked) Green else Ink.copy(alpha = 0.2f).compositeOver(ItemsBg))
                    .border(1.5.dp, Ink, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Segmented control (range selectors, tabs). */
@Composable
fun <T> TxSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
    selectedColor: Color = Coral,
) {
    Box(modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(Modifier.matchParentSize().offset(4.dp, 4.dp).background(Ink, CardShape))
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(CardShape)
                .background(ItemsBg, CardShape)
                .border(2.dp, Ink, CardShape)
        ) {
            options.forEachIndexed { i, opt ->
                val isSel = opt == selected
                if (i > 0) Box(Modifier.width(2.dp).fillMaxHeight().background(Ink))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSel) selectedColor.copy(alpha = 0.12f).compositeOver(ItemsBg) else ItemsBg)
                        .clickable { onSelect(opt) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(opt).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.ExtraLight,
                        color = if (isSel) selectedColor else Ink.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** Bordered text input with a small caps label and optional unit suffix. */
@Composable
fun TxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    placeholder: String = "",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Box(Modifier.padding(end = 3.dp, bottom = 3.dp)) {
            Box(Modifier.matchParentSize().offset(3.dp, 3.dp).background(Ink, FieldShape))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ItemsBg, FieldShape)
                    .border(1.5.dp, Ink, FieldShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.35f))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { v -> if (maxLength == null || v.length <= maxLength) onValueChange(v) },
                        singleLine = singleLine,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold, color = Ink),
                        cursorBrush = SolidColor(Ink),
                        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (unit != null) Text(unit, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (maxLength != null) {
            Text("${value.length}/$maxLength", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Small selectable tag chip. */
@Composable
fun TxChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Coral) {
    Box(
        modifier
            .clip(CardShape)
            .background(if (selected) color.copy(alpha = 0.15f).compositeOver(ItemsBg) else ItemsBg, CardShape)
            .border(1.5.dp, if (selected) color else Ink, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.ExtraLight,
            color = if (selected) color else Ink,
        )
    }
}

/** Screen heading: wide-tracked ExtraBold caps with optional subtitle and trailing slot. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
        }
        trailing?.invoke()
    }
}

/** Section label — small ExtraLight caps. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, modifier = modifier.padding(top = 8.dp))
}

/** Label above a big value: the core Tracknix stat pattern. */
@Composable
fun StatValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    color: Color = Ink,
    big: Boolean = false,
) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, color = color)
            if (unit != null) Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/** Pill progress bar toward a target. */
@Composable
fun TxProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Green, height: Dp = 12.dp) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height).padding(end = 2.dp, bottom = 2.dp)) {
        Box(Modifier.matchParentSize().offset(2.dp, 2.dp).background(Ink, RoundedCornerShape(50)))
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(SecondaryBg)
                .border(1.5.dp, Ink, RoundedCornerShape(50))
        ) {
            if (p > 0f) Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(color))
        }
    }
}

/** Empty / placeholder state. */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Ink.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
    }
}

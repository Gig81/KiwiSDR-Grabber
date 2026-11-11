package com.grabber.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pannello di una scheda: superficie con bordo, testata opzionale (icona, titolo, azioni a destra)
 * e contenuto in colonna. `overlay` viene disegnato sopra l'intero pannello, ad esempio
 * [FeatureLockedOverlay] per le funzioni che richiedono la licenza.
 */
@Composable
fun AppPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = GrabberColors.Primary,
    titleAccessory: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    size: ControlSize = ControlSize.Regular,
    contentSpacing: Dp = GrabberSpacing.sm,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isRegular = size == ControlSize.Regular
    Surface(
        modifier = modifier,
        color = GrabberColors.Surface,
        shape = RoundedCornerShape(GrabberShapes.panel),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(if (isRegular) GrabberSpacing.lg else 10.dp),
                verticalArrangement = Arrangement.spacedBy(contentSpacing)
            ) {
                if (title != null || icon != null || actions != null) {
                    AppPanelTitleRow(title, icon, iconTint, titleAccessory, actions, isRegular)
                }
                content()
            }
            overlay?.invoke(this)
        }
    }
}

@Composable
private fun AppPanelTitleRow(
    title: String?,
    icon: ImageVector?,
    iconTint: Color,
    titleAccessory: (@Composable () -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    isRegular: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            if (title != null) {
                if (isRegular) {
                    Text(text = title, style = GrabberTypography.panelTitle)
                } else {
                    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
                }
            }
            titleAccessory?.invoke()
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions
            )
        }
    }
}

@Composable
fun AppPanelHeader(
    title: String? = null,
    icon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = GrabberSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(GrabberSpacing.sm))
            }
            if (title != null) {
                Text(
                    text = title,
                    style = GrabberTypography.panelTitle
                )
            }
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}

@Composable
fun AppSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Column(modifier = Modifier.padding(GrabberSpacing.md)) {
            if (title != null) {
                Text(
                    text = title,
                    style = GrabberTypography.label,
                    modifier = Modifier.padding(bottom = GrabberSpacing.sm)
                )
            }
            content()
        }
    }
}

@Composable
fun AppDivider(modifier: Modifier = Modifier, color: Color = GrabberColors.Border, thickness: Dp = 1.dp) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/** Separatore leggero tra controlli dello stesso pannello. */
@Composable
fun AppSubtleDivider(modifier: Modifier = Modifier) {
    AppDivider(modifier = modifier, color = GrabberColors.Border.copy(alpha = 0.4f), thickness = 0.5.dp)
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GrabberShapes.panel),
        color = GrabberColors.Surface,
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Column(modifier = Modifier.padding(GrabberSpacing.lg)) {
            if (title != null) {
                Text(
                    text = title,
                    style = GrabberTypography.panelTitle,
                    modifier = Modifier.padding(bottom = GrabberSpacing.sm)
                )
            }
            content()
        }
    }
}

/**
 * Dimensioni dei controlli: `Regular` per i dialoghi, `Compact` e `Dense` per le schede,
 * `Mini` per le barre strumenti ad alta densità (spettro e console).
 */
enum class ControlSize(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    val fontSize: TextUnit,
    val cornerRadius: Dp
) {
    Regular(38.dp, 16.dp, 18.dp, 12.sp, GrabberShapes.control),
    Compact(32.dp, 10.dp, 13.dp, 10.sp, 4.dp),
    Dense(26.dp, 7.dp, 13.dp, 10.sp, GrabberShapes.control),
    Mini(24.dp, 6.dp, 12.dp, 9.sp, 3.dp)
}

/** Pulsante pieno nel colore d'accento: azione principale (salva, attiva, invia). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Regular,
    containerColor: Color = GrabberColors.Primary,
    contentColor: Color = Color.White,
    iconTint: Color? = null
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = GrabberColors.Border,
            disabledContentColor = GrabberColors.TextDisabled
        ),
        border = null,
        fontWeight = FontWeight.Bold,
        iconTint = iconTint
    )
}

/** Pulsante tonale con bordo: azioni secondarie e comandi di testata. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Regular,
    contentColor: Color = GrabberColors.TextPrimary,
    iconTint: Color? = null
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        colors = ButtonDefaults.buttonColors(
            containerColor = GrabberColors.SurfaceElevated,
            contentColor = contentColor,
            disabledContainerColor = GrabberColors.SurfaceVariant.copy(alpha = 0.5f),
            disabledContentColor = GrabberColors.TextDisabled.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, if (enabled) GrabberColors.Border else GrabberColors.Border.copy(alpha = 0.3f)),
        fontWeight = FontWeight.SemiBold,
        iconTint = iconTint
    )
}

/** Pulsante trasparente con bordo: alternative e azioni a basso impatto. */
@Composable
fun OutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Regular,
    contentColor: Color = GrabberColors.TextPrimary,
    iconTint: Color? = null
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = GrabberColors.TextDisabled
        ),
        border = BorderStroke(1.dp, GrabberColors.Border),
        fontWeight = FontWeight.Normal,
        iconTint = iconTint
    )
}

@Composable
private fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    size: ControlSize,
    colors: ButtonColors,
    border: BorderStroke?,
    fontWeight: FontWeight,
    iconTint: Color? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(size.height),
        enabled = enabled,
        shape = RoundedCornerShape(size.cornerRadius),
        colors = colors,
        border = border,
        contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(size.iconSize),
                tint = if (enabled && iconTint != null) iconTint else LocalContentColor.current
            )
            Spacer(modifier = Modifier.width(GrabberSpacing.xs))
        }
        Text(text = text, fontSize = size.fontSize, fontWeight = fontWeight)
    }
}

/**
 * Pulsante a due stati: spento è trasparente con bordo neutro, acceso ha fondo, bordo e testo
 * nel colore d'accento. L'icona può cambiare con lo stato.
 */
@Composable
fun AppToggleButton(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Mini,
    accentColor: Color = GrabberColors.Success
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = true,
        icon = icon,
        size = size,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (checked) accentColor.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (checked) accentColor else GrabberColors.TextSecondary
        ),
        border = BorderStroke(1.dp, if (checked) accentColor else GrabberColors.Border),
        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
    )
}

/** Pulsante con menu a tendina per scegliere un valore tra `options`. */
@Composable
fun <T> AppMenuButton(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Mini,
    accentColor: Color = GrabberColors.Primary
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.height(size.height),
            shape = RoundedCornerShape(size.cornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = GrabberColors.Surface, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(size.iconSize), tint = accentColor)
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(label(selected), fontSize = size.fontSize)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(size.iconSize), tint = Color.Gray)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(GrabberColors.SurfaceVariant)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) accentColor else Color.White
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
    content: @Composable () -> Unit
) {
    if (tooltip != null) {
        // Tooltip support is kept intentionally lightweight and does not change component behavior.
    }
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

/** Pulsante pieno rosso per azioni distruttive. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    size: ControlSize = ControlSize.Regular
) {
    PrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        containerColor = GrabberColors.Error,
        contentColor = Color.White
    )
}

@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val container = if (selected) GrabberColors.Primary else GrabberColors.SurfaceVariant
    val contentColor = if (selected) Color.White else GrabberColors.TextPrimary
    Button(
        onClick = onClick,
        modifier = modifier.height(30.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = GrabberColors.Border,
            disabledContentColor = GrabberColors.TextDisabled
        ),
        shape = RoundedCornerShape(GrabberShapes.control)
    ) {
        Text(text = text, style = GrabberTypography.label)
    }
}

@Composable
fun StatusIndicator(label: String, status: String, modifier: Modifier = Modifier) {
    val color = when (status.lowercase()) {
        "connected", "active", "online", "ok" -> GrabberColors.Success
        "warning", "waiting", "paused", "attention" -> GrabberColors.Warning
        "error", "offline", "disabled", "failed" -> GrabberColors.Error
        else -> GrabberColors.TextDisabled
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = GrabberTypography.label, color = GrabberColors.TextSecondary)
    }
}

@Composable
fun StatusBadge(
    text: String,
    color: Color = GrabberColors.Success,
    modifier: Modifier = Modifier,
    size: ControlSize = ControlSize.Regular,
    monospace: Boolean = false,
    icon: ImageVector? = null
) {
    val isRegular = size == ControlSize.Regular
    Surface(
        modifier = modifier,
        color = color.copy(alpha = if (isRegular) 0.12f else 0.18f),
        shape = RoundedCornerShape(if (isRegular) GrabberShapes.chip else 3.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (isRegular) 0.35f else 0.5f))
    ) {
        Row(
            modifier = if (isRegular) {
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (isRegular) 11.dp else 10.dp))
            }
            Text(
                text = text,
                style = GrabberTypography.status,
                fontSize = if (isRegular) GrabberTypography.status.fontSize else 8.sp,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LedIndicator(isOn: Boolean, label: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOn) GrabberColors.Success else GrabberColors.TextDisabled)
        )
        if (label != null) {
            Text(text = label, style = GrabberTypography.label)
        }
    }
}

@Composable
fun ConnectionStatus(status: String, modifier: Modifier = Modifier) {
    StatusBadge(
        text = status,
        color = when (status.lowercase()) {
            "connected", "online", "active" -> GrabberColors.Success
            "connecting", "waiting" -> GrabberColors.Warning
            "disconnected", "offline" -> GrabberColors.TextDisabled
            "error", "failed" -> GrabberColors.Error
            else -> GrabberColors.Primary
        },
        modifier = modifier
    )
}

@Composable
fun RecordingStatus(status: String, modifier: Modifier = Modifier) {
    StatusBadge(
        text = status,
        color = when (status.lowercase()) {
            "recording", "active" -> GrabberColors.Error
            "paused", "waiting" -> GrabberColors.Warning
            "stopped", "idle" -> GrabberColors.TextDisabled
            else -> GrabberColors.Success
        },
        modifier = modifier
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = GrabberTypography.label,
                modifier = Modifier.padding(bottom = GrabberSpacing.xs)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, style = GrabberTypography.label) },
            isError = isError,
            textStyle = GrabberTypography.body,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GrabberColors.Primary,
                unfocusedBorderColor = GrabberColors.Border,
                cursorColor = GrabberColors.Primary,
                focusedTextColor = GrabberColors.TextPrimary,
                unfocusedTextColor = GrabberColors.TextPrimary,
                disabledTextColor = GrabberColors.TextDisabled,
                errorBorderColor = GrabberColors.Error,
                errorCursorColor = GrabberColors.Error
            )
        )
    }
}

@Composable
fun AppNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = true
    )
}

@Composable
fun AppDropdown(
    selectedValue: String,
    options: List<String>,
    onSelectionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    var currentValue by remember(selectedValue) { mutableStateOf(selectedValue) }
    val items = options.ifEmpty { listOf(selectedValue) }
    AppTextField(
        value = currentValue,
        onValueChange = { currentValue = it; onSelectionChange(it) },
        modifier = modifier,
        label = label,
        placeholder = "Select",
        enabled = enabled
    )
    if (items.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)) {
            items.forEach { option ->
                CompactButton(
                    text = option,
                    onClick = { currentValue = option; onSelectionChange(option) },
                    selected = option == currentValue,
                    modifier = Modifier
                )
            }
        }
    }
}

/**
 * Casella con etichetta. Nelle taglie compatte l'intera riga è cliccabile; con `emphasized`
 * l'etichetta è in grassetto e, quando la casella è attiva, assume il colore d'accento.
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ControlSize = ControlSize.Regular,
    accentColor: Color = GrabberColors.Primary,
    labelColor: Color = GrabberColors.TextSecondary,
    emphasized: Boolean = false
) {
    if (size == ControlSize.Regular) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = accentColor,
                    uncheckedColor = GrabberColors.Border,
                    checkmarkColor = Color.White
                )
            )
            Text(text = text, style = GrabberTypography.body)
        }
        return
    }
    Row(
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.xs)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = accentColor)
        )
        Text(
            text = text,
            fontSize = if (size == ControlSize.Dense || size == ControlSize.Mini) 9.sp else 10.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasized && checked) accentColor else labelColor
        )
    }
}

@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = GrabberColors.Primary,
                unselectedColor = GrabberColors.Border
            )
        )
        Text(text = text, style = GrabberTypography.body)
    }
}

/**
 * Interruttore con etichetta. In `Regular` l'etichetta segue l'interruttore; nelle taglie compatte
 * lo precede e assume il colore d'accento quando l'interruttore è attivo.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    size: ControlSize = ControlSize.Regular,
    accentColor: Color = GrabberColors.Primary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (size == ControlSize.Regular) GrabberSpacing.sm else 6.dp)
    ) {
        if (size == ControlSize.Regular) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            if (label != null) Text(text = label, style = GrabberTypography.body)
            return@Row
        }
        if (label != null) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (checked) accentColor else GrabberColors.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.height(20.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = GrabberColors.TextDisabled,
                uncheckedTrackColor = GrabberColors.SurfaceElevated,
                uncheckedBorderColor = GrabberColors.Border
            )
        )
    }
}

/**
 * Slider con etichetta. In `Compact` occupa due righe: titolo, descrizione in linea e valore
 * evidenziato; poi slider sottile tra le etichette di minimo e massimo.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    label: String? = null,
    size: ControlSize = ControlSize.Regular,
    valueText: String? = null,
    description: String? = null,
    accentColor: Color = GrabberColors.Primary,
    minLabel: String? = null,
    maxLabel: String? = null
) {
    if (size == ControlSize.Regular) {
        Column(modifier = modifier) {
            if (label != null) {
                Text(
                    text = label,
                    style = GrabberTypography.label,
                    modifier = Modifier.padding(bottom = GrabberSpacing.xs)
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        CompactSliderHeader(label, description, valueText, accentColor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (minLabel != null) SliderBoundLabel(minLabel)
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f).height(18.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = GrabberColors.SurfaceElevated
                )
            )
            if (maxLabel != null) SliderBoundLabel(maxLabel)
        }
    }
}

@Composable
private fun CompactSliderHeader(label: String?, description: String?, valueText: String?, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (label != null) {
                Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GrabberColors.TextPrimary)
            }
            if (description != null) {
                Text(
                    text = "•  $description",
                    fontSize = 10.sp,
                    color = GrabberColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (valueText != null) {
            Text(
                text = valueText,
                style = GrabberTypography.numeric,
                fontSize = 10.sp,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun SliderBoundLabel(text: String) {
    Text(text = text, fontSize = 8.sp, color = GrabberColors.TextDisabled)
}

/** Gruppo di chip a scelta singola; un indice fuori intervallo non seleziona nessuna opzione. */
@Composable
fun AppSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: ControlSize = ControlSize.Regular,
    accentColor: Color = GrabberColors.Secondary,
    selectedContentColor: Color = Color.Black,
    fillWidth: Boolean = false
) {
    val isDense = size == ControlSize.Dense || size == ControlSize.Mini
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (isDense) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = if (selected) accentColor else GrabberColors.SurfaceElevated,
                border = BorderStroke(1.dp, if (selected) accentColor else GrabberColors.Border),
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier).clickable { onSelectionChange(index) }
            ) {
                Text(
                    text = option,
                    fontSize = when (size) {
                        ControlSize.Dense, ControlSize.Mini -> 9.sp
                        ControlSize.Compact -> 10.sp
                        ControlSize.Regular -> 12.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (selected) selectedContentColor else GrabberColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = when {
                        isDense -> Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        fillWidth -> Modifier.padding(vertical = 6.dp)
                        else -> Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    }
                )
            }
        }
    }
}

@Composable
fun RadioValueDisplay(value: String, modifier: Modifier = Modifier) {
    Text(
        text = value,
        style = GrabberTypography.numeric,
        modifier = modifier
    )
}

@Composable
fun FrequencyDisplay(value: String, modifier: Modifier = Modifier) {
    Text(
        text = value,
        style = GrabberTypography.frequency,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
fun SignalMeter(level: Float, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(GrabberShapes.control))
            .background(GrabberColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(GrabberShapes.control))
                .background(
                    when {
                        clamped < 0.35f -> GrabberColors.Success
                        clamped < 0.7f -> GrabberColors.Warning
                        else -> GrabberColors.Error
                    }
                )
                .padding(end = 0.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(12.dp)
                .clip(RoundedCornerShape(GrabberShapes.control))
                .background(
                    when {
                        clamped < 0.35f -> GrabberColors.Success
                        clamped < 0.7f -> GrabberColors.Warning
                        else -> GrabberColors.Error
                    }
                )
        )
    }
}

@Composable
fun AudioLevelMeter(level: Float, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(GrabberShapes.control))
            .background(GrabberColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(12.dp)
                .clip(RoundedCornerShape(GrabberShapes.control))
                .background(GrabberColors.Primary)
        )
    }
}

@Composable
fun MiniStatusLabel(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GrabberSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GrabberSpacing.sm)
        ) {
            Text(text = label, style = GrabberTypography.label, color = GrabberColors.TextSecondary)
            Text(text = value, style = GrabberTypography.numeric, color = GrabberColors.TextPrimary)
        }
    }
}

@Composable
fun ListSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = GrabberTypography.label,
        color = GrabberColors.TextSecondary,
        modifier = modifier.padding(vertical = GrabberSpacing.sm)
    )
}

/**
 * Voce selezionabile di un elenco. In `Compact` la voce selezionata è piena nel colore primario
 * e il sottotitolo compare solo sulle voci non selezionate.
 */
@Composable
fun SelectableListItem(
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: ControlSize = ControlSize.Regular,
    trailing: (@Composable () -> Unit)? = null
) {
    val isRegular = size == ControlSize.Regular
    val shape = RoundedCornerShape(GrabberShapes.control)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = when {
            isRegular -> if (selected) GrabberColors.SurfaceVariant else GrabberColors.Surface
            else -> if (selected) GrabberColors.Primary else GrabberColors.SurfaceVariant
        },
        shape = shape,
        border = BorderStroke(width = 1.dp, color = if (selected) GrabberColors.Primary else GrabberColors.Border)
    ) {
        Row(
            modifier = if (isRegular) Modifier.padding(GrabberSpacing.md) else Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isRegular) {
                    Text(text = title, style = GrabberTypography.body)
                    if (subtitle != null) Text(text = subtitle, style = GrabberTypography.label)
                } else {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else GrabberColors.TextPrimary
                    )
                    if (subtitle != null && !selected) {
                        Text(text = subtitle, fontSize = 9.sp, color = GrabberColors.TextDisabled)
                    }
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
fun EmptyListMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = GrabberTypography.label,
        color = GrabberColors.TextSecondary,
        modifier = modifier.padding(vertical = GrabberSpacing.lg)
    )
}

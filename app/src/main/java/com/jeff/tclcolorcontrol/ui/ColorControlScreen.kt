package com.jeff.tclcolorcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.color.percentLabel
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.TclModeSnapshot
import com.jeff.tclcolorcontrol.state.ColorControlUiState
import com.jeff.tclcolorcontrol.state.ControlMode

@Composable
fun ColorControlScreen(
    state: ColorControlUiState,
    onSelectProfile: (ColorProfile) -> Unit,
    onRedChange: (Float) -> Unit,
    onGreenChange: (Float) -> Unit,
    onBlueChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onInversionChange: (Boolean) -> Unit,
    onAutoBrightnessChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onGrantSystemSettings: () -> Unit,
    onEnableCustom: () -> Unit,
    onSwitchClassic: () -> Unit,
    onRestore: () -> Unit,
    isCollapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    onMovePanel: (Float, Float) -> Unit,
    onMovePanelFinished: () -> Unit,
    onPanelSizeChanged: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailsExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(min = if (isCollapsed) 232.dp else 320.dp, max = 520.dp)
            .padding(10.dp)
            .onSizeChanged { onPanelSizeChanged(it.width, it.height) },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (isCollapsed) {
            MinimizedPanel(
                inversionEnabled = state.inversionEnabled,
                inversionControlEnabled = state.inversionControlEnabled,
                onInversionChange = onInversionChange,
                onMovePanel = onMovePanel,
                onMovePanelFinished = onMovePanelFinished,
                onExpand = { onCollapsedChange(false) },
                onDismiss = onDismiss,
            )
            return@Surface
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(
                selectedProfileLabel = state.selected.label,
                onMovePanel = onMovePanel,
                onMovePanelFinished = onMovePanelFinished,
                onMinimize = { onCollapsedChange(true) },
                onDismiss = onDismiss,
            )
            ModeControls(
                mode = state.controlMode,
                onEnableCustom = onEnableCustom,
                onSwitchClassic = onSwitchClassic,
            )
            Presets(
                presets = state.presets,
                selected = state.selected,
                enabled = state.controlsEnabled,
                onSelectProfile = onSelectProfile,
            )
            ChannelSlider("Red", state.selected.red, ChannelRed, state.controlsEnabled, onRedChange, onSliderFinished)
            ChannelSlider("Green", state.selected.green, ChannelGreen, state.controlsEnabled, onGreenChange, onSliderFinished)
            ChannelSlider("Blue", state.selected.blue, ChannelBlue, state.controlsEnabled, onBlueChange, onSliderFinished)
            DisplayControls(
                inversionEnabled = state.inversionEnabled,
                inversionControlEnabled = state.inversionControlEnabled,
                autoBrightness = state.autoBrightness,
                brightness = state.brightness,
                canWriteSystemSettings = state.capabilities.canWriteSystemSettings,
                brightnessControlsEnabled = state.brightnessControlsEnabled,
                onInversionChange = onInversionChange,
                onAutoBrightnessChange = onAutoBrightnessChange,
                onBrightnessChange = onBrightnessChange,
                onBrightnessFinished = onBrightnessFinished,
                onGrantSystemSettings = onGrantSystemSettings,
            )
            Actions(
                onRestore = onRestore,
            )
            Details(
                expanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded },
                state = state,
            )
        }
    }
}

@Composable
private fun Header(
    selectedProfileLabel: String,
    onMovePanel: (Float, Float) -> Unit,
    onMovePanelFinished: () -> Unit,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TCL Color",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = headerSubtitle(selectedProfileLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(
                onMovePanel = onMovePanel,
                onMovePanelFinished = onMovePanelFinished,
            )
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Filled.Minimize,
                    contentDescription = "Minimize",
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                )
            }
        }
    }
}

@Composable
private fun MinimizedPanel(
    inversionEnabled: Boolean,
    inversionControlEnabled: Boolean,
    onInversionChange: (Boolean) -> Unit,
    onMovePanel: (Float, Float) -> Unit,
    onMovePanelFinished: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(
            onMovePanel = onMovePanel,
            onMovePanelFinished = onMovePanelFinished,
        )
        IconToggleButton(
            checked = inversionEnabled,
            enabled = inversionControlEnabled,
            onCheckedChange = onInversionChange,
        ) {
            Icon(
                imageVector = Icons.Filled.InvertColors,
                contentDescription = if (inversionEnabled) "Turn inversion off" else "Turn inversion on",
                tint = if (inversionEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onExpand) {
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = "Expand",
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
            )
        }
    }
}

@Composable
private fun DragHandle(
    onMovePanel: (Float, Float) -> Unit,
    onMovePanelFinished: () -> Unit,
) {
    val accessibilityStepPx = with(LocalDensity.current) { 64.dp.toPx() }
    fun movePanelForAccessibility(deltaX: Float, deltaY: Float): Boolean {
        onMovePanel(deltaX, deltaY)
        onMovePanelFinished()
        return true
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Move panel"
                role = Role.Button
                onClick(label = "Move panel right") {
                    movePanelForAccessibility(accessibilityStepPx, 0f)
                }
                customActions = listOf(
                    CustomAccessibilityAction("Move panel left") {
                        movePanelForAccessibility(-accessibilityStepPx, 0f)
                    },
                    CustomAccessibilityAction("Move panel right") {
                        movePanelForAccessibility(accessibilityStepPx, 0f)
                    },
                    CustomAccessibilityAction("Move panel up") {
                        movePanelForAccessibility(0f, -accessibilityStepPx)
                    },
                    CustomAccessibilityAction("Move panel down") {
                        movePanelForAccessibility(0f, accessibilityStepPx)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onMovePanelFinished,
                    onDragCancel = onMovePanelFinished,
                ) { _, dragAmount ->
                    onMovePanel(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun headerSubtitle(selectedProfileLabel: String): String =
    "Live compositor profile / $selectedProfileLabel"

@Composable
private fun ModeControls(
    mode: ControlMode,
    onEnableCustom: () -> Unit,
    onSwitchClassic: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (mode == ControlMode.CustomMatrix) {
                Button(
                    onClick = onEnableCustom,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Custom")
                }
            } else {
                OutlinedButton(
                    onClick = onEnableCustom,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Custom")
                }
            }
            if (mode == ControlMode.ClassicSafe) {
                Button(
                    onClick = onSwitchClassic,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Classic safe")
                }
            } else {
                OutlinedButton(
                    onClick = onSwitchClassic,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Classic safe")
                }
            }
        }
    }
}

@Composable
private fun Presets(
    presets: List<ColorProfile>,
    selected: ColorProfile,
    enabled: Boolean,
    onSelectProfile: (ColorProfile) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { profile ->
            val isSelected = selected.id == profile.id
            if (isSelected) {
                Button(
                    onClick = { onSelectProfile(profile) },
                    enabled = enabled,
                ) {
                    Text(profile.label)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectProfile(profile) },
                    enabled = enabled,
                ) {
                    Text(profile.label)
                }
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    color: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(value.percentLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun DisplayControls(
    inversionEnabled: Boolean,
    inversionControlEnabled: Boolean,
    autoBrightness: Boolean,
    brightness: Float,
    canWriteSystemSettings: Boolean,
    brightnessControlsEnabled: Boolean,
    onInversionChange: (Boolean) -> Unit,
    onAutoBrightnessChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessFinished: () -> Unit,
    onGrantSystemSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Display", fontWeight = FontWeight.Medium)
        ToggleRow(
            label = "Invert",
            checked = inversionEnabled,
            enabled = inversionControlEnabled,
            onCheckedChange = onInversionChange,
        )
        ToggleRow(
            label = "Auto brightness",
            checked = autoBrightness,
            enabled = canWriteSystemSettings,
            onCheckedChange = onAutoBrightnessChange,
        )
        if (canWriteSystemSettings) {
            ChannelSlider(
                label = "Brightness",
                value = brightness,
                color = ChannelBrightness,
                enabled = brightnessControlsEnabled,
                onValueChange = onBrightnessChange,
                onValueChangeFinished = onBrightnessFinished,
            )
        } else {
            OutlinedButton(
                onClick = onGrantSystemSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant brightness access")
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun Actions(
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore baseline")
        }
    }
}

@Composable
private fun Details(
    expanded: Boolean,
    onToggle: () -> Unit,
    state: ColorControlUiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (expanded) "Hide details" else "Details")
        }
        if (expanded) {
            StatusBlock(state)
        }
    }
}

@Composable
private fun StatusBlock(state: ColorControlUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(state.status, fontWeight = FontWeight.Medium)
        Text("Mode: ${state.controlMode.label} / ${state.capabilities.modeSnapshot.modeLabel}")
        Text("Binder: ${if (state.capabilities.binderAvailable) "available" else "missing"}")
        Text("Secure settings: ${if (state.capabilities.canWriteSecureSettings) "granted" else "missing"}")
        Text("System settings: ${if (state.capabilities.canWriteSystemSettings) "granted" else "missing"}")
        Text("Brightness: ${if (state.autoBrightness) "auto" else state.brightness.percentLabel()}")
        Text("Activation: ${state.capabilities.activationState.label}")
        Text("Eye comfort: ${state.capabilities.modeSnapshot.eyeComfortLabel}")
        Text("Color mode: ${state.capabilities.modeSnapshot.colorModeLabel}")
    }
}

private val ActivationState.label: String
    get() = when (this) {
        ActivationState.Active -> "active"
        ActivationState.Inactive -> "inactive"
        ActivationState.Unknown -> "unknown"
    }

private val ControlMode.label: String
    get() = when (this) {
        ControlMode.CustomMatrix -> "custom"
        ControlMode.ClassicSafe -> "classic safe"
        ControlMode.External -> "external"
    }

private val TclModeSnapshot.eyeComfortLabel: String
    get() {
        val status = eyeProtectStatus?.toString() ?: "?"
        val kind = eyeProtectKind?.toString() ?: "?"
        val classic = eyeProtectClassicMode?.toString() ?: "?"
        return "status $status, kind $kind, classic $classic"
    }

private val TclModeSnapshot.colorModeLabel: String
    get() {
        val color = colorModeValue?.toString() ?: "?"
        val advanced = advancedColorModeValue?.toString() ?: "?"
        return "color $color, advanced $advanced"
    }

private val ChannelRed = Color(0xFFB42318)
private val ChannelGreen = Color(0xFF16803C)
private val ChannelBlue = Color(0xFF175CD3)
private val ChannelBrightness = Color(0xFF8B6F1D)

@Preview(widthDp = 420)
@Composable
private fun ColorControlScreenPreview() {
    TclColorControlTheme {
        ColorControlScreen(
            state = ColorControlUiState(selected = ColorProfiles.Red),
            onSelectProfile = {},
            onRedChange = {},
            onGreenChange = {},
            onBlueChange = {},
            onSliderFinished = {},
            onInversionChange = {},
            onAutoBrightnessChange = {},
            onBrightnessChange = {},
            onBrightnessFinished = {},
            onGrantSystemSettings = {},
            onEnableCustom = {},
            onSwitchClassic = {},
            onRestore = {},
            isCollapsed = false,
            onCollapsedChange = {},
            onMovePanel = { _, _ -> },
            onMovePanelFinished = {},
            onPanelSizeChanged = { _, _ -> },
            onDismiss = {},
        )
    }
}

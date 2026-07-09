package com.jeff.tclcolorcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onEnableCustom: () -> Unit,
    onSwitchClassic: () -> Unit,
    onRestore: () -> Unit,
    panelPositionLabel: String,
    onCyclePanelPosition: () -> Unit,
    showAddTile: Boolean,
    onAddTile: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCollapsed by rememberSaveable { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(min = 320.dp, max = 520.dp)
            .padding(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(
                isCollapsed = isCollapsed,
                panelPositionLabel = panelPositionLabel,
                selectedProfileLabel = state.selected.label,
                onCyclePanelPosition = onCyclePanelPosition,
                onToggleCollapsed = { isCollapsed = !isCollapsed },
                onDismiss = onDismiss,
            )
            if (!isCollapsed) {
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
                Actions(
                    showAddTile = showAddTile,
                    onAddTile = onAddTile,
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
}

@Composable
private fun Header(
    isCollapsed: Boolean,
    panelPositionLabel: String,
    selectedProfileLabel: String,
    onCyclePanelPosition: () -> Unit,
    onToggleCollapsed: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TCL Color",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = headerSubtitle(
                    isCollapsed = isCollapsed,
                    selectedProfileLabel = selectedProfileLabel,
                    panelPositionLabel = panelPositionLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.width(176.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCyclePanelPosition) {
                Text("Move")
            }
            TextButton(onClick = onToggleCollapsed) {
                Text(if (isCollapsed) "Show" else "Hide")
            }
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

private fun headerSubtitle(
    isCollapsed: Boolean,
    selectedProfileLabel: String,
    panelPositionLabel: String,
): String {
    val modeLabel = if (isCollapsed) selectedProfileLabel else "Live compositor profile"
    return "$modeLabel / $panelPositionLabel"
}

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
private fun Actions(
    showAddTile: Boolean,
    onAddTile: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showAddTile) {
            Button(
                onClick = onAddTile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add QS tile")
            }
        }
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
            onEnableCustom = {},
            onSwitchClassic = {},
            onRestore = {},
            panelPositionLabel = "Top",
            onCyclePanelPosition = {},
            showAddTile = true,
            onAddTile = {},
            onDismiss = {},
        )
    }
}

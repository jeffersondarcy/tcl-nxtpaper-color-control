package com.jeff.tclcolorcontrol.device

import com.jeff.tclcolorcontrol.color.ColorProfile

interface ColorBackend {
    fun getCapabilities(): BackendCapabilities
    fun readModeSnapshot(): TclModeSnapshot
    fun readDisplaySnapshot(): DisplaySnapshot
    fun apply(profile: ColorProfile, inverted: Boolean = false): BackendResult
    fun restoreBaseline(): BackendResult
    fun setBrightness(value: Float): BackendResult
    fun setAutoBrightness(enabled: Boolean): BackendResult
    fun setExtraDimEnabled(enabled: Boolean): BackendResult
    fun setExtraDimStrength(value: Float): BackendResult
}

data class BackendCapabilities(
    val binderAvailable: Boolean,
    val canWriteSecureSettings: Boolean,
    val canWriteSystemSettings: Boolean,
    val activationState: ActivationState,
    val modeSnapshot: TclModeSnapshot = TclModeSnapshot(),
    val displaySnapshot: DisplaySnapshot = DisplaySnapshot(),
)

enum class ActivationState {
    Active,
    Inactive,
    Unknown,
}

data class TclModeSnapshot(
    val eyeProtectStatus: Int? = null,
    val eyeProtectKind: Int? = null,
    val eyeProtectClassicMode: Int? = null,
    val eyeProtectPersonalizedSet: Int? = null,
    val colorModeValue: Int? = null,
    val advancedColorModeValue: Int? = null,
    val matrixActive: Int? = null,
    val matrix: String? = null,
) {
    val isEyeComfortOn: Boolean
        get() = eyeProtectStatus == 1

    val isClassicFlagOn: Boolean
        get() = eyeProtectClassicMode == 1

    val isAdvancedColorMode: Boolean
        get() = colorModeValue in ADVANCED_COLOR_MODES ||
            advancedColorModeValue in ADVANCED_COLOR_MODES

    val modeLabel: String
        get() = when {
            matrixActive == 1 -> "Custom matrix"
            isEyeComfortOn && isClassicFlagOn -> "Classic/iComfort"
            isEyeComfortOn -> "Eye comfort"
            isAdvancedColorMode -> "TCL advanced"
            colorModeValue != null -> "TCL standard"
            else -> "Unknown"
        }

    private companion object {
        val ADVANCED_COLOR_MODES = setOf(10, 11, 12)
    }
}

data class DisplaySnapshot(
    val brightness: Float? = null,
    val rawBrightness: Int? = null,
    val autoBrightness: Boolean? = null,
    val colorInversionEnabled: Boolean? = null,
    val extraDimEnabled: Boolean? = null,
    val extraDimStrength: Float? = null,
    val rawExtraDimLevel: Int? = null,
)

sealed interface BackendResult {
    data object Success : BackendResult
    data object BinderUnavailable : BackendResult
    data object PermissionMissing : BackendResult
    data object SecureSettingsPermissionMissing : BackendResult
    data object SystemSettingsPermissionMissing : BackendResult
    data class Failed(val message: String) : BackendResult
}

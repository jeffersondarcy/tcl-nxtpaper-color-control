package com.jeff.tclcolorcontrol.device

import com.jeff.tclcolorcontrol.color.ColorProfile

interface ColorBackend {
    fun getCapabilities(): BackendCapabilities
    fun readModeSnapshot(): TclModeSnapshot
    fun apply(profile: ColorProfile): BackendResult
    fun restoreBaseline(): BackendResult
}

data class BackendCapabilities(
    val binderAvailable: Boolean,
    val canWriteSecureSettings: Boolean,
    val activationState: ActivationState,
    val modeSnapshot: TclModeSnapshot = TclModeSnapshot(),
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

sealed interface BackendResult {
    data object Success : BackendResult
    data object BinderUnavailable : BackendResult
    data object PermissionMissing : BackendResult
    data class Failed(val message: String) : BackendResult
}

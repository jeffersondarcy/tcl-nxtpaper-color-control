package com.jeff.tclcolorcontrol.device

import com.jeff.tclcolorcontrol.color.ColorProfile

interface ColorBackend {
    fun getCapabilities(): BackendCapabilities
    fun readModeSnapshot(): TclModeSnapshot
    fun readDisplaySnapshot(): DisplaySnapshot
    fun readExperimentalSnapshot(): ExperimentalDisplaySnapshot
    fun apply(profile: ColorProfile, inverted: Boolean = false, saturation: Float = 1f): BackendResult
    fun restoreBaseline(): BackendResult
    fun setBrightness(value: Float): BackendResult
    fun setAutoBrightness(enabled: Boolean): BackendResult
    fun setExtraDimEnabled(enabled: Boolean): BackendResult
    fun setExtraDimStrength(value: Float): BackendResult
    fun setScreenColorMode(mode: ScreenColorMode): BackendResult
    fun setImageEnhancement(enabled: Boolean): BackendResult
    fun setVideoEnhancement(enabled: Boolean): BackendResult
    fun setBoldText(enabled: Boolean): BackendResult
    fun setHighContrastText(enabled: Boolean): BackendResult
}

data class BackendCapabilities(
    val binderAvailable: Boolean,
    val canWriteSecureSettings: Boolean,
    val canWriteSystemSettings: Boolean,
    val extraDimAvailable: Boolean = false,
    val activationState: ActivationState,
    val modeSnapshot: TclModeSnapshot = TclModeSnapshot(),
    val displaySnapshot: DisplaySnapshot = DisplaySnapshot(),
    val experimentalSnapshot: ExperimentalDisplaySnapshot = ExperimentalDisplaySnapshot(),
)

data class ExperimentalDisplaySnapshot(
    val screenColorMode: ScreenColorMode? = null,
    val rawScreenColorMode: Int? = null,
    val rawAdvancedColorMode: Int? = null,
    val imageEnhancementEnabled: Boolean? = null,
    val videoEnhancementEnabled: Boolean? = null,
    val boldTextEnabled: Boolean? = null,
    val highContrastTextEnabled: Boolean? = null,
)

enum class ScreenColorMode(val rawValue: Int, val label: String) {
    P3(0, "P3 / Natural"),
    Vivid(1, "Vivid"),
    Srgb(2, "sRGB / Gentle"),
    AdvancedVivid(10, "Advanced Vivid"),
    AdvancedP3(11, "Advanced P3"),
    AdvancedSrgb(12, "Advanced sRGB"),
    ;

    val isAdvanced: Boolean
        get() = rawValue >= 10

    companion object {
        fun fromRaw(value: Int?): ScreenColorMode? = entries.firstOrNull { it.rawValue == value }
    }
}

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

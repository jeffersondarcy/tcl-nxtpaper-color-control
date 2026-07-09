package com.jeff.tclcolorcontrol.device

import com.jeff.tclcolorcontrol.color.ColorProfile

interface ColorBackend {
    fun getCapabilities(): BackendCapabilities
    fun apply(profile: ColorProfile): BackendResult
    fun restoreBaseline(): BackendResult
}

data class BackendCapabilities(
    val binderAvailable: Boolean,
    val canWriteSecureSettings: Boolean,
    val activationState: ActivationState,
)

enum class ActivationState {
    Active,
    Inactive,
    Unknown,
}

sealed interface BackendResult {
    data object Success : BackendResult
    data object BinderUnavailable : BackendResult
    data object PermissionMissing : BackendResult
    data class Failed(val message: String) : BackendResult
}

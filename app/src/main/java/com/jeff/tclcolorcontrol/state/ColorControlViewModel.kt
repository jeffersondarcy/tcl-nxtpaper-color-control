package com.jeff.tclcolorcontrol.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.color.clampChannel
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.BackendCapabilities
import com.jeff.tclcolorcontrol.device.BackendResult
import com.jeff.tclcolorcontrol.device.ColorBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ColorControlUiState(
    val selected: ColorProfile = ColorProfiles.Red,
    val presets: List<ColorProfile> = ColorProfiles.presets,
    val capabilities: BackendCapabilities = BackendCapabilities(
        binderAvailable = false,
        canWriteSecureSettings = false,
        activationState = ActivationState.Unknown,
    ),
    val status: String = "Ready",
)

class ColorControlViewModel(
    private val backend: ColorBackend,
    private val profileStore: ProfileStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ColorControlUiState(
            selected = profileStore.load() ?: ColorProfiles.Red,
            capabilities = backend.getCapabilities(),
        ),
    )
    val uiState: StateFlow<ColorControlUiState> = _uiState.asStateFlow()

    fun selectProfile(profile: ColorProfile) {
        profileStore.save(profile)
        _uiState.update { it.copy(selected = profile, status = "${profile.label} selected") }
    }

    fun selectProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let(::selectProfile)
    }

    fun applyProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let { profile ->
            selectProfile(profile)
            applyCurrent()
        }
    }

    fun setRed(value: Float) = updateCustom(red = value.clampChannel())

    fun setGreen(value: Float) = updateCustom(green = value.clampChannel())

    fun setBlue(value: Float) = updateCustom(blue = value.clampChannel())

    fun applyCurrent() {
        val result = backend.apply(_uiState.value.selected)
        profileStore.save(_uiState.value.selected)
        refreshAfter(result, successMessage = "Applied ${_uiState.value.selected.label}")
    }

    fun restoreBaseline() {
        val result = backend.restoreBaseline()
        profileStore.save(ColorProfiles.Baseline)
        _uiState.update { it.copy(selected = ColorProfiles.Baseline) }
        refreshAfter(result, successMessage = "Baseline restored")
    }

    private fun updateCustom(
        red: Float = _uiState.value.selected.red,
        green: Float = _uiState.value.selected.green,
        blue: Float = _uiState.value.selected.blue,
    ) {
        _uiState.update {
            val profile = ColorProfile.custom(red, green, blue)
            profileStore.save(profile)
            it.copy(
                selected = profile,
                status = "Custom profile",
            )
        }
    }

    private fun refreshAfter(result: BackendResult, successMessage: String) {
        _uiState.update {
            it.copy(
                capabilities = backend.getCapabilities(),
                status = result.toStatusMessage(successMessage),
            )
        }
    }
}

class ColorControlViewModelFactory(
    private val backend: ColorBackend,
    private val profileStore: ProfileStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ColorControlViewModel::class.java))
        return ColorControlViewModel(backend, profileStore) as T
    }
}

private fun BackendResult.toStatusMessage(successMessage: String): String =
    when (this) {
        BackendResult.Success -> successMessage
        BackendResult.BinderUnavailable -> "TCL service unavailable"
        BackendResult.PermissionMissing -> "Matrix sent; grant WRITE_SECURE_SETTINGS for activation"
        is BackendResult.Failed -> "Failed: $message"
    }

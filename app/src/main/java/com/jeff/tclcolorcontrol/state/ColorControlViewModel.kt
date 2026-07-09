package com.jeff.tclcolorcontrol.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.color.clampChannel
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.BackendCapabilities
import com.jeff.tclcolorcontrol.device.BackendResult
import com.jeff.tclcolorcontrol.device.ColorBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ColorControlUiState(
    val selected: ColorProfile = ColorProfiles.Red,
    val presets: List<ColorProfile> = ColorProfiles.presets,
    val capabilities: BackendCapabilities = BackendCapabilities(
        binderAvailable = false,
        canWriteSecureSettings = false,
        activationState = ActivationState.Unknown,
    ),
    val controlMode: ControlMode = ControlMode.External,
    val status: String = "Ready",
) {
    val controlsEnabled: Boolean
        get() = controlMode == ControlMode.CustomMatrix && capabilities.binderAvailable
}

enum class ControlMode {
    CustomMatrix,
    ClassicSafe,
    External,
}

class ColorControlViewModel(
    private val backend: ColorBackend,
    private val profileStore: ProfileStore,
    liveApplyScope: CoroutineScope? = null,
    private val applyDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val liveApplyDelayMillis: Long = LIVE_APPLY_DELAY_MILLIS,
) : ViewModel() {
    private val liveRequests = Channel<LiveApplyRequest>(Channel.CONFLATED)
    private val scope = liveApplyScope ?: viewModelScope

    private val _uiState = MutableStateFlow(
        initialState(
            selected = profileStore.load() ?: ColorProfiles.Red,
            capabilities = backend.getCapabilities(),
        )
    )
    val uiState: StateFlow<ColorControlUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            processLiveRequests()
        }
    }

    fun selectProfile(profile: ColorProfile) {
        profileStore.save(profile)
        val shouldApply = _uiState.value.controlsEnabled
        _uiState.update {
            it.copy(
                selected = profile,
                status = if (shouldApply) "Applying ${profile.label}" else "${profile.label} selected",
            )
        }
        if (shouldApply) {
            queueLiveApply(profile, immediate = true)
        }
    }

    fun selectProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let(::selectProfile)
    }

    fun applyProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let { profile ->
            selectProfile(profile)
            enableCustomMode()
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

    fun finishSliderChange() {
        if (_uiState.value.controlsEnabled) {
            queueLiveApply(_uiState.value.selected, immediate = true)
        }
    }

    fun enableCustomMode() {
        _uiState.update {
            it.copy(
                controlMode = ControlMode.CustomMatrix,
                status = "Enabling custom matrix",
            )
        }
        queueLiveApply(_uiState.value.selected, immediate = true)
    }

    fun switchToClassicSafeMode() {
        scope.launch {
            _uiState.update { it.copy(status = "Turning custom matrix off") }
            val result = withContext(applyDispatcher) {
                backend.restoreBaseline()
            }
            refreshAfter(
                result = result,
                successMessage = "Custom matrix off; TCL Classic keys unchanged",
                forceMode = ControlMode.ClassicSafe,
            )
        }
    }

    fun restoreBaseline() {
        val result = backend.restoreBaseline()
        profileStore.save(ColorProfiles.Baseline)
        _uiState.update { it.copy(selected = ColorProfiles.Baseline, controlMode = ControlMode.ClassicSafe) }
        refreshAfter(result, successMessage = "Baseline restored", forceMode = ControlMode.ClassicSafe)
    }

    private fun updateCustom(
        red: Float = _uiState.value.selected.red,
        green: Float = _uiState.value.selected.green,
        blue: Float = _uiState.value.selected.blue,
    ) {
        val profile = ColorProfile.custom(red, green, blue)
        val shouldApply = _uiState.value.controlsEnabled
        profileStore.save(profile)
        _uiState.update {
            it.copy(
                selected = profile,
                status = if (shouldApply) "Live editing" else "Custom saved; choose Custom to apply",
            )
        }
        if (shouldApply) {
            queueLiveApply(profile, immediate = false)
        }
    }

    private suspend fun processLiveRequests() {
        for (request in liveRequests) {
            var latest = request
            if (!latest.immediate) {
                delay(liveApplyDelayMillis)
            }
            while (true) {
                latest = liveRequests.tryReceive().getOrNull() ?: break
            }
            applyLiveProfile(latest.profile)
        }
    }

    private suspend fun applyLiveProfile(profile: ColorProfile) {
        val result = withContext(applyDispatcher) {
            backend.apply(profile)
        }
        profileStore.save(profile)
        refreshAfter(result, successMessage = "Live ${profile.label}", forceMode = ControlMode.CustomMatrix)
    }

    private fun queueLiveApply(profile: ColorProfile, immediate: Boolean) {
        liveRequests.trySend(LiveApplyRequest(profile, immediate))
    }

    private fun refreshAfter(
        result: BackendResult,
        successMessage: String,
        forceMode: ControlMode? = null,
    ) {
        val capabilities = backend.getCapabilities()
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                controlMode = forceMode ?: capabilities.toControlMode(),
                status = result.toStatusMessage(successMessage),
            )
        }
    }

    private companion object {
        const val LIVE_APPLY_DELAY_MILLIS = 40L
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

private data class LiveApplyRequest(
    val profile: ColorProfile,
    val immediate: Boolean,
)

private fun initialState(
    selected: ColorProfile,
    capabilities: BackendCapabilities,
): ColorControlUiState =
    ColorControlUiState(
        selected = selected,
        capabilities = capabilities,
        controlMode = capabilities.toControlMode(),
    )

private fun BackendCapabilities.toControlMode(): ControlMode =
    when (activationState) {
        ActivationState.Active -> ControlMode.CustomMatrix
        ActivationState.Inactive -> ControlMode.ClassicSafe
        ActivationState.Unknown -> ControlMode.External
    }

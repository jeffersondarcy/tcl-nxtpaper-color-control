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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class ColorControlUiState(
    val selected: ColorProfile = ColorProfiles.Red,
    val presets: List<ColorProfile> = ColorProfiles.presets,
    val capabilities: BackendCapabilities = BackendCapabilities(
        binderAvailable = false,
        canWriteSecureSettings = false,
        canWriteSystemSettings = false,
        activationState = ActivationState.Unknown,
    ),
    val inversionEnabled: Boolean = false,
    val brightness: Float = 1f,
    val autoBrightness: Boolean = false,
    val controlMode: ControlMode = ControlMode.External,
    val status: String = "Ready",
) {
    val controlsEnabled: Boolean
        get() = controlMode == ControlMode.CustomMatrix && capabilities.binderAvailable

    val inversionControlEnabled: Boolean
        get() = capabilities.binderAvailable && capabilities.canWriteSecureSettings

    val brightnessControlsEnabled: Boolean
        get() = capabilities.canWriteSystemSettings && !autoBrightness
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
    private val postInversionReapplyDelayMillis: Long = POST_INVERSION_REAPPLY_DELAY_MILLIS,
) : ViewModel() {
    private val liveRequests = Channel<LiveApplyRequest>(Channel.CONFLATED)
    private val scope = liveApplyScope ?: viewModelScope
    private val colorOperationMutex = Mutex()
    private val colorOperationVersion = AtomicLong()

    private val _uiState = MutableStateFlow(
        initialState(
            selected = profileStore.load() ?: ColorProfiles.Red,
            inversionEnabled = profileStore.loadInversionEnabled(),
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
        scope.launch {
            val profile = _uiState.value.selected
            val operationVersion = nextColorOperationVersion()
            val result = applyProfileOperation(profile, _uiState.value.inversionEnabled)
            if (!isCurrentColorOperation(operationVersion)) return@launch
            profileStore.save(profile)
            refreshAfter(result, successMessage = "Applied ${profile.label}")
        }
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

    fun setInversionEnabled(enabled: Boolean) {
        scope.launch {
            val profile = _uiState.value.selected
            val operationVersion = nextColorOperationVersion()
            _uiState.update {
                it.copy(
                    status = if (enabled) "Applying inverted profile" else "Applying normal profile",
                )
            }
            val result = applyProfileOperation(profile, enabled)
            if (!isCurrentColorOperation(operationVersion)) return@launch
            if (result == BackendResult.Success) {
                profileStore.saveInversionEnabled(enabled)
                profileStore.save(profile)
                _uiState.update {
                    it.copy(
                        inversionEnabled = enabled,
                        controlMode = ControlMode.CustomMatrix,
                    )
                }
            }
            refreshAfter(
                result = result,
                successMessage = if (enabled) "Inverted ${profile.label}" else "Normal ${profile.label}",
                forceMode = if (result == BackendResult.Success) ControlMode.CustomMatrix else null,
            )
            if (result == BackendResult.Success) {
                schedulePostInversionReapply(
                    expectedInversion = enabled,
                    operationVersion = operationVersion,
                )
            }
        }
    }

    fun setBrightness(value: Float) {
        val brightness = value.coerceIn(BRIGHTNESS_RANGE)
        _uiState.update {
            it.copy(
                brightness = brightness,
                status = "Setting brightness ${(brightness * 100f).toInt()}%",
            )
        }
    }

    fun finishBrightnessChange() {
        val brightness = _uiState.value.brightness
        val result = backend.setBrightness(brightness)
        refreshAfter(result, successMessage = "Brightness updated", keepMode = true)
    }

    fun setAutoBrightness(enabled: Boolean) {
        _uiState.update {
            it.copy(
                autoBrightness = enabled,
                status = if (enabled) "Turning auto brightness on" else "Turning auto brightness off",
            )
        }
        val result = backend.setAutoBrightness(enabled)
        refreshAfter(
            result = result,
            successMessage = if (enabled) "Auto brightness on" else "Manual brightness on",
            keepMode = true,
        )
    }

    fun refreshSystemState() {
        val capabilities = backend.getCapabilities()
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: it.inversionEnabled,
                brightness = capabilities.displaySnapshot.brightness ?: it.brightness,
                autoBrightness = capabilities.displaySnapshot.autoBrightness ?: it.autoBrightness,
            )
        }
    }

    fun switchToClassicSafeMode() {
        scope.launch {
            val operationVersion = nextColorOperationVersion()
            _uiState.update { it.copy(status = "Turning custom matrix off") }
            val result = restoreBaselineOperation()
            if (!isCurrentColorOperation(operationVersion)) return@launch
            refreshAfter(
                result = result,
                successMessage = "Custom matrix off; TCL Classic keys unchanged",
                forceMode = if (result == BackendResult.Success) ControlMode.ClassicSafe else null,
            )
        }
    }

    fun restoreBaseline() {
        scope.launch {
            val operationVersion = nextColorOperationVersion()
            val result = restoreBaselineOperation()
            if (!isCurrentColorOperation(operationVersion)) return@launch
            if (result == BackendResult.Success) {
                profileStore.save(ColorProfiles.Baseline)
                profileStore.saveInversionEnabled(false)
                _uiState.update {
                    it.copy(
                        selected = ColorProfiles.Baseline,
                        inversionEnabled = false,
                        controlMode = ControlMode.ClassicSafe,
                    )
                }
            }
            refreshAfter(
                result = result,
                successMessage = "Baseline restored",
                forceMode = if (result == BackendResult.Success) ControlMode.ClassicSafe else null,
            )
        }
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
            applyLiveProfile(latest.profile, latest.operationVersion)
        }
    }

    private suspend fun applyLiveProfile(profile: ColorProfile, operationVersion: Long) {
        if (!isCurrentColorOperation(operationVersion)) return
        val result = applyLiveProfileOperation(profile) ?: return
        if (!isCurrentColorOperation(operationVersion)) return
        profileStore.save(profile)
        refreshAfter(
            result = result,
            successMessage = if (_uiState.value.inversionEnabled) {
                "Live inverted ${profile.label}"
            } else {
                "Live ${profile.label}"
            },
            forceMode = ControlMode.CustomMatrix,
        )
    }

    private fun queueLiveApply(profile: ColorProfile, immediate: Boolean) {
        liveRequests.trySend(
            LiveApplyRequest(
                profile = profile,
                immediate = immediate,
                operationVersion = nextColorOperationVersion(),
            )
        )
    }

    private fun schedulePostInversionReapply(
        expectedInversion: Boolean,
        operationVersion: Long,
    ) {
        scope.launch {
            delay(postInversionReapplyDelayMillis)
            if (!isCurrentColorOperation(operationVersion)) return@launch
            val state = _uiState.value
            if (state.inversionEnabled != expectedInversion || state.controlMode != ControlMode.CustomMatrix) {
                return@launch
            }
            val result = applyProfileOperationIfCurrent(expectedInversion, operationVersion)
            if (result != null && result !is BackendResult.Success) {
                refreshFailureIfCurrent(result, operationVersion)
            }
        }
    }

    private suspend fun applyProfileOperation(profile: ColorProfile, inverted: Boolean): BackendResult =
        withContext(applyDispatcher) {
            colorOperationMutex.withLock {
                backend.apply(profile, inverted)
            }
        }

    private suspend fun applyLiveProfileOperation(profile: ColorProfile): BackendResult? =
        withContext(applyDispatcher) {
            colorOperationMutex.withLock {
                val state = _uiState.value
                if (state.controlMode != ControlMode.CustomMatrix || state.selected != profile) {
                    null
                } else {
                    backend.apply(profile, state.inversionEnabled)
                }
            }
        }

    private suspend fun applyProfileOperationIfCurrent(
        expectedInversion: Boolean,
        operationVersion: Long,
    ): BackendResult? =
        withContext(applyDispatcher) {
            colorOperationMutex.withLock {
                val state = _uiState.value
                if (
                    !isCurrentColorOperation(operationVersion) ||
                    state.inversionEnabled != expectedInversion ||
                    state.controlMode != ControlMode.CustomMatrix
                ) {
                    null
                } else {
                    backend.apply(state.selected, state.inversionEnabled)
                }
            }
        }

    private suspend fun restoreBaselineOperation(): BackendResult =
        withContext(applyDispatcher) {
            colorOperationMutex.withLock {
                backend.restoreBaseline()
            }
        }

    private fun nextColorOperationVersion(): Long =
        colorOperationVersion.incrementAndGet()

    private fun isCurrentColorOperation(operationVersion: Long): Boolean =
        colorOperationVersion.get() == operationVersion

    private fun refreshFailureIfCurrent(result: BackendResult, operationVersion: Long) {
        if (!isCurrentColorOperation(operationVersion)) return
        val capabilities = backend.getCapabilities()
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: it.inversionEnabled,
                status = result.toStatusMessage("Custom profile reapplied"),
            )
        }
    }

    private fun refreshAfter(
        result: BackendResult,
        successMessage: String,
        forceMode: ControlMode? = null,
        keepMode: Boolean = false,
    ) {
        val capabilities = backend.getCapabilities()
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: it.inversionEnabled,
                brightness = capabilities.displaySnapshot.brightness ?: it.brightness,
                autoBrightness = capabilities.displaySnapshot.autoBrightness ?: it.autoBrightness,
                controlMode = when {
                    forceMode != null -> forceMode
                    keepMode -> it.controlMode
                    else -> capabilities.toControlMode()
                },
                status = result.toStatusMessage(successMessage),
            )
        }
    }

    private companion object {
        const val LIVE_APPLY_DELAY_MILLIS = 40L
        const val POST_INVERSION_REAPPLY_DELAY_MILLIS = 1_000L
        val BRIGHTNESS_RANGE = 0f..1f
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
        BackendResult.SystemSettingsPermissionMissing -> "Grant Modify system settings for brightness"
        is BackendResult.Failed -> "Failed: $message"
    }

private data class LiveApplyRequest(
    val profile: ColorProfile,
    val immediate: Boolean,
    val operationVersion: Long,
)

private fun initialState(
    selected: ColorProfile,
    inversionEnabled: Boolean,
    capabilities: BackendCapabilities,
): ColorControlUiState =
    ColorControlUiState(
        selected = selected,
        inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: inversionEnabled,
        capabilities = capabilities,
        brightness = capabilities.displaySnapshot.brightness ?: DEFAULT_BRIGHTNESS,
        autoBrightness = capabilities.displaySnapshot.autoBrightness ?: false,
        controlMode = capabilities.toControlMode(),
    )

private const val DEFAULT_BRIGHTNESS = 1f

private fun BackendCapabilities.toControlMode(): ControlMode =
    when (activationState) {
        ActivationState.Active -> ControlMode.CustomMatrix
        ActivationState.Inactive -> ControlMode.ClassicSafe
        ActivationState.Unknown -> ControlMode.External
    }

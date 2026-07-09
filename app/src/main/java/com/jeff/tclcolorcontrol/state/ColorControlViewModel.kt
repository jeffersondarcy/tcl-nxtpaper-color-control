package com.jeff.tclcolorcontrol.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jeff.tclcolorcontrol.color.ColorChannel
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
    val extraDimEnabled: Boolean = false,
    val extraDimStrength: Float = DEFAULT_EXTRA_DIM_STRENGTH,
    val controlMode: ControlMode = ControlMode.External,
    val status: String = "Ready",
) {
    val controlsEnabled: Boolean
        get() = controlMode == ControlMode.CustomMatrix && capabilities.binderAvailable

    val inversionControlEnabled: Boolean
        get() = capabilities.binderAvailable && capabilities.canWriteSecureSettings

    val brightnessControlsEnabled: Boolean
        get() = capabilities.canWriteSystemSettings && !autoBrightness

    val extraDimControlsEnabled: Boolean
        get() = capabilities.canWriteSecureSettings && capabilities.extraDimAvailable

    val extraDimStrengthControlsEnabled: Boolean
        get() = extraDimControlsEnabled && extraDimEnabled
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
    private val initialProfile = (profileStore.load() ?: ColorProfiles.Red).safeForUse()

    private val _uiState = MutableStateFlow(
        initialState(
            selected = initialProfile,
            inversionEnabled = profileStore.loadInversionEnabled(),
            capabilities = backend.getCapabilities(),
        )
    )
    val uiState: StateFlow<ColorControlUiState> = _uiState.asStateFlow()
    private var lastKnownExtraDimStrength = _uiState.value.extraDimStrength

    init {
        migrateInitialCustomProfile()
        scope.launch {
            processLiveRequests()
        }
    }

    fun selectProfile(profile: ColorProfile) {
        val safeProfile = profile.safeForUse()
        saveActiveProfile(safeProfile)
        val shouldApply = _uiState.value.controlsEnabled
        _uiState.update {
            it.copy(
                selected = safeProfile,
                status = if (shouldApply) "Applying ${safeProfile.label}" else "${safeProfile.label} selected",
            )
        }
        if (shouldApply) {
            queueLiveApply(safeProfile, immediate = true)
        }
    }

    fun selectProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let(::selectProfile)
    }

    fun applyProfileId(profileId: String) {
        ColorProfiles.byId(profileId)?.let { profile ->
            selectProfile(profile)
            enableCustomMatrix(profile, status = "Enabling custom matrix")
        }
    }

    fun setRed(value: Float) = updateCustom(red = value.clampChannel(), editedChannel = ColorChannel.Red)

    fun setGreen(value: Float) = updateCustom(green = value.clampChannel(), editedChannel = ColorChannel.Green)

    fun setBlue(value: Float) = updateCustom(blue = value.clampChannel(), editedChannel = ColorChannel.Blue)

    fun applyCurrent() {
        scope.launch {
            val profile = _uiState.value.selected.safeForUse()
            val operationVersion = nextColorOperationVersion()
            val result = applyProfileOperation(profile, _uiState.value.inversionEnabled)
            if (!isCurrentColorOperation(operationVersion)) return@launch
            saveActiveProfile(profile)
            refreshAfter(result, successMessage = "Applied ${profile.label}")
        }
    }

    fun finishSliderChange() {
        if (_uiState.value.controlsEnabled) {
            queueLiveApply(_uiState.value.selected, immediate = true)
        }
    }

    fun enableCustomMode() {
        val customProfile = profileStore.loadCustom()
        val profile = (customProfile ?: _uiState.value.selected).safeForUse()
        saveActiveProfile(profile)
        enableCustomMatrix(
            profile = profile,
            status = if (customProfile == null) "Enabling custom matrix" else "Restoring Custom",
        )
    }

    private fun enableCustomMatrix(profile: ColorProfile, status: String) {
        _uiState.update {
            it.copy(
                selected = profile,
                controlMode = ControlMode.CustomMatrix,
                status = status,
            )
        }
        queueLiveApply(profile, immediate = true)
    }

    fun setInversionEnabled(enabled: Boolean) {
        scope.launch {
            val profile = _uiState.value.selected.safeForUse()
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
                saveActiveProfile(profile)
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

    fun setExtraDimEnabled(enabled: Boolean) {
        val previousEnabled = _uiState.value.capabilities.displaySnapshot.extraDimEnabled ?: _uiState.value.extraDimEnabled
        _uiState.update {
            it.copy(
                status = if (enabled) "Turning Extra dim on" else "Turning Extra dim off",
            )
        }
        val result = backend.setExtraDimEnabled(enabled)
        refreshAfter(
            result = result,
            successMessage = if (enabled) "Extra dim on" else "Extra dim off",
            keepMode = true,
        )
        if (_uiState.value.capabilities.displaySnapshot.extraDimEnabled == null) {
            _uiState.update {
                it.copy(
                    extraDimEnabled = if (result is BackendResult.Success) enabled else previousEnabled,
                )
            }
        }
    }

    fun setExtraDimStrength(value: Float) {
        val strength = value.coerceIn(EXTRA_DIM_STRENGTH_RANGE)
        _uiState.update {
            it.copy(
                extraDimStrength = strength,
                status = "Setting Extra dim intensity ${(strength * 100f).toInt()}%",
            )
        }
    }

    fun finishExtraDimStrengthChange() {
        val previousStrength = _uiState.value.capabilities.displaySnapshot.extraDimStrength
            ?: lastKnownExtraDimStrength
        val strength = _uiState.value.extraDimStrength
        val result = backend.setExtraDimStrength(strength)
        refreshAfter(result, successMessage = "Extra dim intensity updated", keepMode = true)
        if (result is BackendResult.Success) {
            lastKnownExtraDimStrength = _uiState.value.capabilities.displaySnapshot.extraDimStrength
                ?: strength
        }
        if (result !is BackendResult.Success && _uiState.value.capabilities.displaySnapshot.extraDimStrength == null) {
            _uiState.update { it.copy(extraDimStrength = previousStrength) }
        }
    }

    fun refreshSystemState() {
        val capabilities = backend.getCapabilities()
        capabilities.displaySnapshot.extraDimStrength?.let { lastKnownExtraDimStrength = it }
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: it.inversionEnabled,
                brightness = capabilities.displaySnapshot.brightness ?: it.brightness,
                autoBrightness = capabilities.displaySnapshot.autoBrightness ?: it.autoBrightness,
                extraDimEnabled = capabilities.displaySnapshot.extraDimEnabled ?: it.extraDimEnabled,
                extraDimStrength = capabilities.displaySnapshot.extraDimStrength ?: it.extraDimStrength,
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
        editedChannel: ColorChannel,
    ) {
        val profile = ColorProfile.customAfterChannelEdit(red, green, blue, editedChannel)
        val shouldApply = _uiState.value.controlsEnabled
        saveActiveProfile(profile)
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

    private fun saveActiveProfile(profile: ColorProfile) {
        val safeProfile = profile.safeForUse()
        profileStore.save(safeProfile)
        if (safeProfile.id == CUSTOM_PROFILE_ID) {
            profileStore.saveCustom(safeProfile)
        }
    }

    private fun migrateInitialCustomProfile() {
        if (initialProfile.id == CUSTOM_PROFILE_ID) {
            saveActiveProfile(initialProfile)
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
        saveActiveProfile(profile)
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
                backend.apply(profile.safeForUse(), inverted)
            }
        }

    private suspend fun applyLiveProfileOperation(profile: ColorProfile): BackendResult? =
        withContext(applyDispatcher) {
            colorOperationMutex.withLock {
                val state = _uiState.value
                val safeProfile = profile.safeForUse()
                if (state.controlMode != ControlMode.CustomMatrix || state.selected.safeForUse() != safeProfile) {
                    null
                } else {
                    backend.apply(safeProfile, state.inversionEnabled)
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
                    backend.apply(state.selected.safeForUse(), state.inversionEnabled)
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
        capabilities.displaySnapshot.extraDimStrength?.let { lastKnownExtraDimStrength = it }
        _uiState.update {
            it.copy(
                capabilities = capabilities,
                inversionEnabled = capabilities.displaySnapshot.colorInversionEnabled ?: it.inversionEnabled,
                brightness = capabilities.displaySnapshot.brightness ?: it.brightness,
                autoBrightness = capabilities.displaySnapshot.autoBrightness ?: it.autoBrightness,
                extraDimEnabled = capabilities.displaySnapshot.extraDimEnabled ?: it.extraDimEnabled,
                extraDimStrength = capabilities.displaySnapshot.extraDimStrength ?: it.extraDimStrength,
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
        BackendResult.SecureSettingsPermissionMissing -> "Grant WRITE_SECURE_SETTINGS for secure display controls"
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
        extraDimEnabled = capabilities.displaySnapshot.extraDimEnabled ?: false,
        extraDimStrength = capabilities.displaySnapshot.extraDimStrength ?: DEFAULT_EXTRA_DIM_STRENGTH,
        controlMode = capabilities.toControlMode(),
    )

private const val DEFAULT_BRIGHTNESS = 1f
private const val DEFAULT_EXTRA_DIM_STRENGTH = 0.5f
private const val CUSTOM_PROFILE_ID = "custom"
private val EXTRA_DIM_STRENGTH_RANGE = 0f..1f

private fun ColorProfile.safeForUse(): ColorProfile =
    if (id == CUSTOM_PROFILE_ID) {
        ColorProfile.custom(red, green, blue)
    } else {
        this
    }

private fun BackendCapabilities.toControlMode(): ControlMode =
    when (activationState) {
        ActivationState.Active -> ControlMode.CustomMatrix
        ActivationState.Inactive -> ControlMode.ClassicSafe
        ActivationState.Unknown -> ControlMode.External
    }

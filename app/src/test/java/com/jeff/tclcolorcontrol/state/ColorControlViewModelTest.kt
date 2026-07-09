package com.jeff.tclcolorcontrol.state

import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.BackendCapabilities
import com.jeff.tclcolorcontrol.device.BackendResult
import com.jeff.tclcolorcontrol.device.ColorBackend
import com.jeff.tclcolorcontrol.device.DisplaySnapshot
import com.jeff.tclcolorcontrol.device.TclModeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorControlViewModelTest {
    @Test
    fun permissionMissingResultIsVisibleInStatus() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.PermissionMissing),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.applyCurrent()

        assertEquals(
            "Matrix sent; grant WRITE_SECURE_SETTINGS for activation",
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun applyProfileIdSelectsAndAppliesPreset() {
        val store = InMemoryProfileStore()
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.applyProfileId("deep")

        assertEquals(ColorProfiles.Deep, viewModel.uiState.value.selected)
        assertEquals(ColorProfiles.Deep, backend.appliedProfile)
        assertEquals(ColorProfiles.Deep, store.savedProfile)
    }

    @Test
    fun inactiveMatrixStartsInClassicSafeModeWithControlsDisabled() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = false,
                    activationState = ActivationState.Inactive,
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(ControlMode.ClassicSafe, viewModel.uiState.value.controlMode)
        assertFalse(viewModel.uiState.value.controlsEnabled)
    }

    @Test
    fun inversionControlRequiresBinderAndSecureSettingsPermission() {
        val missingPermissionViewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = false,
                    canWriteSystemSettings = false,
                    activationState = ActivationState.Active,
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )
        val grantedViewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = false,
                    activationState = ActivationState.Active,
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertFalse(missingPermissionViewModel.uiState.value.inversionControlEnabled)
        assertTrue(grantedViewModel.uiState.value.inversionControlEnabled)
    }

    @Test
    fun togglingInversionSavesAndAppliesCurrentProfileAsInverted() {
        val store = InMemoryProfileStore()
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setInversionEnabled(true)

        assertTrue(viewModel.uiState.value.inversionEnabled)
        assertTrue(store.savedInversionEnabled)
        assertEquals(ColorProfiles.Red, backend.appliedProfile)
        assertTrue(backend.appliedInverted)
    }

    @Test
    fun changingProfileWhileInvertedAppliesInvertedProfile() {
        val store = InMemoryProfileStore(savedInversionEnabled = true)
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.applyProfileId("deep")

        assertEquals(ColorProfiles.Deep, backend.appliedProfile)
        assertTrue(backend.appliedInverted)
    }

    @Test
    fun failedInversionApplyDoesNotPersistInversion() {
        val store = InMemoryProfileStore()
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Failed("rejected")),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setInversionEnabled(true)

        assertFalse(viewModel.uiState.value.inversionEnabled)
        assertFalse(store.savedInversionEnabled)
    }

    @Test
    fun permissionMissingInversionApplyDoesNotPersistInversion() {
        val store = InMemoryProfileStore()
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.PermissionMissing),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setInversionEnabled(true)

        assertFalse(viewModel.uiState.value.inversionEnabled)
        assertFalse(store.savedInversionEnabled)
    }

    @Test
    fun brightnessControlsReportMissingSystemSettingsPermission() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                systemSettingsResult = BackendResult.SystemSettingsPermissionMissing,
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setBrightness(0.4f)
        viewModel.finishBrightnessChange()

        assertEquals("Grant Modify system settings for brightness", viewModel.uiState.value.status)
    }

    @Test
    fun brightnessPreviewDoesNotWriteUntilFinished() {
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                activationState = ActivationState.Active,
                displaySnapshot = DisplaySnapshot(brightness = 0.5f, rawBrightness = 128, autoBrightness = false),
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setBrightness(0.4f)

        assertEquals(null, backend.writtenBrightness)

        viewModel.finishBrightnessChange()

        assertEquals(0.4f, backend.writtenBrightness)
    }

    @Test
    fun autoBrightnessToggleCallsBackendAndUpdatesState() {
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                activationState = ActivationState.Active,
                displaySnapshot = DisplaySnapshot(brightness = 0.5f, rawBrightness = 128, autoBrightness = false),
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setAutoBrightness(true)

        assertTrue(backend.autoBrightnessEnabled == true)
        assertTrue(viewModel.uiState.value.autoBrightness)
    }

    @Test
    fun restoreBaselineClearsPersistedInversion() {
        val store = InMemoryProfileStore(savedInversionEnabled = true)
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.restoreBaseline()

        assertFalse(viewModel.uiState.value.inversionEnabled)
        assertFalse(store.savedInversionEnabled)
    }

    @Test
    fun failedRestoreBaselineDoesNotClearPersistedInversion() {
        val store = InMemoryProfileStore(savedInversionEnabled = true)
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                restoreResult = BackendResult.PermissionMissing,
            ),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.restoreBaseline()

        assertTrue(viewModel.uiState.value.inversionEnabled)
        assertTrue(store.savedInversionEnabled)
    }

    private class FakeBackend(
        private val applyResult: BackendResult,
        private var capabilities: BackendCapabilities = BackendCapabilities(
            binderAvailable = true,
            canWriteSecureSettings = false,
            canWriteSystemSettings = false,
            activationState = ActivationState.Unknown,
        ),
        private val systemSettingsResult: BackendResult = BackendResult.Success,
        private val restoreResult: BackendResult = BackendResult.Success,
    ) : ColorBackend {
        var appliedProfile: ColorProfile? = null
        var appliedInverted: Boolean = false
        var brightness: Float? = capabilities.displaySnapshot.brightness
        var writtenBrightness: Float? = null
        var autoBrightnessEnabled: Boolean? = capabilities.displaySnapshot.autoBrightness

        override fun getCapabilities(): BackendCapabilities = capabilities

        override fun readModeSnapshot(): TclModeSnapshot = capabilities.modeSnapshot

        override fun readDisplaySnapshot(): DisplaySnapshot = capabilities.displaySnapshot

        override fun apply(profile: ColorProfile, inverted: Boolean): BackendResult {
            appliedProfile = profile
            appliedInverted = inverted
            if (applyResult == BackendResult.Success) {
                updateDisplaySnapshot(colorInversionEnabled = inverted)
            }
            return applyResult
        }

        override fun restoreBaseline(): BackendResult {
            if (restoreResult == BackendResult.Success) {
                updateDisplaySnapshot(colorInversionEnabled = false)
            }
            return restoreResult
        }

        override fun setBrightness(value: Float): BackendResult {
            writtenBrightness = value
            brightness = value
            updateDisplaySnapshot(brightness = value)
            return systemSettingsResult
        }

        override fun setAutoBrightness(enabled: Boolean): BackendResult {
            autoBrightnessEnabled = enabled
            updateDisplaySnapshot(autoBrightness = enabled)
            return systemSettingsResult
        }

        private fun updateDisplaySnapshot(
            brightness: Float? = capabilities.displaySnapshot.brightness,
            autoBrightness: Boolean? = capabilities.displaySnapshot.autoBrightness,
            colorInversionEnabled: Boolean? = capabilities.displaySnapshot.colorInversionEnabled,
        ) {
            capabilities = capabilities.copy(
                displaySnapshot = capabilities.displaySnapshot.copy(
                    brightness = brightness,
                    autoBrightness = autoBrightness,
                    colorInversionEnabled = colorInversionEnabled,
                ),
            )
        }
    }

    private class InMemoryProfileStore(
        var savedInversionEnabled: Boolean = false,
    ) : ProfileStore {
        var savedProfile: ColorProfile? = null

        override fun load(): ColorProfile? = savedProfile

        override fun save(profile: ColorProfile) {
            savedProfile = profile
        }

        override fun loadInversionEnabled(): Boolean = savedInversionEnabled

        override fun saveInversionEnabled(enabled: Boolean) {
            savedInversionEnabled = enabled
        }
    }

    private fun testScope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
}

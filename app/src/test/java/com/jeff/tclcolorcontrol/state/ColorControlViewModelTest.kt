package com.jeff.tclcolorcontrol.state

import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.BackendCapabilities
import com.jeff.tclcolorcontrol.device.BackendResult
import com.jeff.tclcolorcontrol.device.ColorBackend
import com.jeff.tclcolorcontrol.device.DisplaySnapshot
import com.jeff.tclcolorcontrol.device.ExperimentalDisplaySnapshot
import com.jeff.tclcolorcontrol.device.ScreenColorMode
import com.jeff.tclcolorcontrol.device.TclModeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ColorControlViewModelTest {
    @Test
    fun advancedColorRequiresAdvancedModeReadback() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                updateExperimentalReadback = true,
                updateAdvancedColorReadback = false,
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
            experimentalReadbackDelayMillis = 0,
            experimentalReadbackAttempts = 0,
        )

        viewModel.setScreenColorMode(ScreenColorMode.AdvancedSrgb)

        assertEquals(
            "Failed: screen color Advanced sRGB readback did not match",
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun experimentalSuccessRequiresMatchingReadback() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
            experimentalReadbackDelayMillis = 0,
            experimentalReadbackAttempts = 0,
        )

        viewModel.setImageEnhancement(true)

        assertEquals(
            "Failed: Image enhancement on readback did not match",
            viewModel.uiState.value.status,
        )
        assertFalse(viewModel.uiState.value.experimental.busy)
    }

    @Test
    fun experimentalMatchingReadbackIsReportedAsSuccess() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                updateExperimentalReadback = true,
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
            experimentalReadbackDelayMillis = 0,
            experimentalReadbackAttempts = 0,
        )

        viewModel.setImageEnhancement(true)

        assertEquals("Image enhancement on", viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.experimental.snapshot.imageEnhancementEnabled == true)
    }

    @Test
    fun saturationIsAppliedWithCurrentProfile() {
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val store = InMemoryProfileStore(savedProfile = ColorProfiles.Warm)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setSaturation(0.35f)
        viewModel.finishSaturationChange()

        assertEquals(ColorProfiles.Warm, backend.appliedProfile)
        assertEquals(0.35f, backend.appliedSaturation, 0.0001f)
        assertEquals(0.35f, store.savedSaturation!!, 0.0001f)
    }

    @Test
    fun failedSaturationApplyRestoresLastSavedValue() {
        val store = InMemoryProfileStore(savedSaturation = 0.8f)
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Failed("rejected")),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setSaturation(0.35f)
        viewModel.finishSaturationChange()

        assertEquals(0.8f, store.savedSaturation!!, 0.0001f)
        assertEquals(0.8f, viewModel.uiState.value.experimental.saturation, 0.0001f)
    }

    @Test
    fun firstLaunchMigratesSaturationFromCurrentMatrix() {
        val profile = ColorProfile.custom(red = 1f, green = 0.63738304f, blue = 0.35f)
        val store = InMemoryProfileStore(savedProfile = profile)
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                activationState = ActivationState.Active,
                modeSnapshot = TclModeSnapshot(
                    matrixActive = 1,
                    matrix =
                        "3f3b18fc,3d5313a9,3ce7d029,0,3e8020c5,3f12e4d3,3db36114,0," +
                            "3cce703b,3c839491,3e71fdde,0,0,0,0,3f800000",
                ),
            ),
        )

        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(0.65f, viewModel.uiState.value.experimental.saturation, 0.0001f)
        assertEquals(0.65f, store.savedSaturation!!, 0.0001f)
        assertEquals(0, backend.applyCount)
    }

    @Test
    fun inactiveMatrixDoesNotMigrateSaturation() {
        val profile = ColorProfile.custom(red = 1f, green = 0.63738304f, blue = 0.35f)
        val store = InMemoryProfileStore(savedProfile = profile)
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                activationState = ActivationState.Inactive,
                modeSnapshot = TclModeSnapshot(
                    matrixActive = 0,
                    matrix =
                        "3f3b18fc,3d5313a9,3ce7d029,0,3e8020c5,3f12e4d3,3db36114,0," +
                            "3cce703b,3c839491,3e71fdde,0,0,0,0,3f800000",
                ),
            ),
        )

        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(1f, viewModel.uiState.value.experimental.saturation, 0.0001f)
        assertEquals(1f, store.savedSaturation!!, 0.0001f)
    }

    @Test
    fun unknownActivationDoesNotMigrateSaturation() {
        val profile = ColorProfile.custom(red = 1f, green = 0.63738304f, blue = 0.35f)
        val store = InMemoryProfileStore(savedProfile = profile)
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                activationState = ActivationState.Unknown,
                modeSnapshot = TclModeSnapshot(
                    matrixActive = 1,
                    matrix =
                        "3f3b18fc,3d5313a9,3ce7d029,0,3e8020c5,3f12e4d3,3db36114,0," +
                            "3cce703b,3c839491,3e71fdde,0,0,0,0,3f800000",
                ),
            ),
        )

        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(1f, viewModel.uiState.value.experimental.saturation, 0.0001f)
        assertEquals(1f, store.savedSaturation!!, 0.0001f)
    }

    @Test
    fun saturationApplyPersistsTheSameCapturedValue() {
        val applyStarted = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        val saturationSaved = CountDownLatch(1)
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            applyStarted = applyStarted,
            releaseApply = releaseApply,
        )
        val store = InMemoryProfileStore(
            savedSaturation = 0.8f,
            saturationSaved = saturationSaved,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = scope,
            applyDispatcher = Dispatchers.Default,
        )

        viewModel.setSaturation(0.35f)
        viewModel.finishSaturationChange()
        assertTrue(applyStarted.await(2, TimeUnit.SECONDS))
        viewModel.setSaturation(0.9f)
        releaseApply.countDown()
        assertTrue(saturationSaved.await(2, TimeUnit.SECONDS))

        assertEquals(0.35f, backend.appliedSaturation, 0.0001f)
        assertEquals(0.35f, store.savedSaturation!!, 0.0001f)
    }

    @Test
    fun failedSaturationApplyDoesNotOverwriteNewerDrag() {
        val applyStarted = CountDownLatch(1)
        val releaseApply = CountDownLatch(1)
        val backend = FakeBackend(
            applyResult = BackendResult.Failed("rejected"),
            applyStarted = applyStarted,
            releaseApply = releaseApply,
        )
        val store = InMemoryProfileStore(savedSaturation = 0.8f)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = scope,
            applyDispatcher = Dispatchers.Default,
        )

        viewModel.setSaturation(0.35f)
        viewModel.finishSaturationChange()
        assertTrue(applyStarted.await(2, TimeUnit.SECONDS))
        viewModel.setSaturation(0.9f)
        releaseApply.countDown()
        assertTrue(waitUntil { viewModel.uiState.value.status == "Failed: rejected" })

        assertEquals(0.9f, viewModel.uiState.value.experimental.saturation, 0.0001f)
        assertEquals(0.8f, store.savedSaturation!!, 0.0001f)
    }

    @Test
    fun screenColorReadbackSerializesCompositorApply() {
        val screenColorStarted = CountDownLatch(1)
        val releaseScreenColor = CountDownLatch(1)
        val applyStarted = CountDownLatch(1)
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            updateExperimentalReadback = true,
            screenColorStarted = screenColorStarted,
            releaseScreenColor = releaseScreenColor,
            applyStarted = applyStarted,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = scope,
            applyDispatcher = Dispatchers.Default,
            experimentalReadbackDelayMillis = 0,
            experimentalReadbackAttempts = 0,
        )

        viewModel.setScreenColorMode(ScreenColorMode.AdvancedSrgb)
        assertTrue(screenColorStarted.await(2, TimeUnit.SECONDS))
        viewModel.applyCurrent()

        assertFalse(applyStarted.await(200, TimeUnit.MILLISECONDS))
        releaseScreenColor.countDown()
        assertTrue(applyStarted.await(2, TimeUnit.SECONDS))
    }

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
    fun sliderEditsPersistActiveAndSavedCustomProfiles() {
        val store = InMemoryProfileStore()
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setGreen(0.42f)

        val expected = ColorProfile.custom(
            red = ColorProfiles.Red.red,
            green = 0.42f,
            blue = ColorProfiles.Red.blue,
        )
        assertEquals(expected, store.savedProfile)
        assertEquals(expected, store.savedCustomProfile)
    }

    @Test
    fun selectingPresetAfterCustomDoesNotOverwriteSavedCustomProfile() {
        val store = InMemoryProfileStore()
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setGreen(0.42f)
        val savedCustom = store.savedCustomProfile

        viewModel.selectProfile(ColorProfiles.Deep)

        assertEquals(ColorProfiles.Deep, store.savedProfile)
        assertEquals(savedCustom, store.savedCustomProfile)
    }

    @Test
    fun legacyActiveCustomProfileIsMigratedBeforePresetSelection() {
        val legacyCustom = ColorProfile.custom(red = 0.8f, green = 0.3f, blue = 0.1f)
        val store = InMemoryProfileStore(savedProfile = legacyCustom)
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(legacyCustom, store.savedCustomProfile)

        viewModel.selectProfile(ColorProfiles.Deep)

        assertEquals(ColorProfiles.Deep, store.savedProfile)
        assertEquals(legacyCustom, store.savedCustomProfile)
    }

    @Test
    fun unsafeLegacyActiveCustomProfileIsSanitizedAndPersistedOnStartup() {
        val unsafeCustom = ColorProfile("custom", "Custom", red = 0f, green = 0f, blue = 0f)
        val expected = ColorProfile.custom(red = 0f, green = 0f, blue = 0f)
        val store = InMemoryProfileStore(savedProfile = unsafeCustom)
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.Success),
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(expected, viewModel.uiState.value.selected)
        assertEquals(expected, store.savedProfile)
        assertEquals(expected, store.savedCustomProfile)
    }

    @Test
    fun selectingUnsafeCustomProfileAppliesSanitizedProfile() {
        val unsafeCustom = ColorProfile("custom", "Custom", red = 0f, green = 0f, blue = 0f)
        val expected = ColorProfile.custom(red = 0f, green = 0f, blue = 0f)
        val store = InMemoryProfileStore()
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = false,
                activationState = ActivationState.Active,
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.selectProfile(unsafeCustom)

        assertEquals(expected, viewModel.uiState.value.selected)
        assertEquals(expected, store.savedProfile)
        assertEquals(expected, store.savedCustomProfile)
        assertEquals(expected, backend.appliedProfile)
    }

    @Test
    fun customButtonRestoresUnsafeSavedCustomAsSanitizedProfile() {
        val unsafeCustom = ColorProfile("custom", "Custom", red = 0f, green = 0f, blue = 0f)
        val expected = ColorProfile.custom(red = 0f, green = 0f, blue = 0f)
        val store = InMemoryProfileStore(savedCustomProfile = unsafeCustom)
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.enableCustomMode()

        assertEquals(expected, viewModel.uiState.value.selected)
        assertEquals(expected, store.savedProfile)
        assertEquals(expected, store.savedCustomProfile)
        assertEquals(expected, backend.appliedProfile)
    }

    @Test
    fun customButtonRestoresSavedCustomAfterPresetChainAndAppliesIt() {
        val savedCustom = ColorProfile.custom(red = 0.8f, green = 0.3f, blue = 0.1f)
        val store = InMemoryProfileStore(savedCustomProfile = savedCustom)
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.selectProfile(ColorProfiles.Warm)
        viewModel.selectProfile(ColorProfiles.Deep)
        viewModel.enableCustomMode()

        assertEquals(savedCustom, viewModel.uiState.value.selected)
        assertEquals(savedCustom, store.savedProfile)
        assertEquals(savedCustom, store.savedCustomProfile)
        assertEquals(savedCustom, backend.appliedProfile)
    }

    @Test
    fun customButtonWithoutSavedCustomAppliesCurrentProfile() {
        val store = InMemoryProfileStore()
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = store,
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.selectProfile(ColorProfiles.Deep)
        viewModel.enableCustomMode()

        assertEquals(ColorProfiles.Deep, viewModel.uiState.value.selected)
        assertEquals(ColorProfiles.Deep, store.savedProfile)
        assertEquals(null, store.savedCustomProfile)
        assertEquals(ColorProfiles.Deep, backend.appliedProfile)
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
    fun togglingInversionReappliesCurrentProfileAfterDisplayModeSettles() {
        val backend = FakeBackend(applyResult = BackendResult.Success)
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
            postInversionReapplyDelayMillis = 0L,
        )

        viewModel.setInversionEnabled(true)

        assertEquals(2, backend.applyCount)
        assertEquals(ColorProfiles.Red, backend.appliedProfile)
        assertTrue(backend.appliedInverted)
    }

    @Test
    fun failedPostInversionReapplyIsVisibleInStatus() {
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            applyResults = listOf(
                BackendResult.Success,
                BackendResult.Failed("reapply rejected"),
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
            postInversionReapplyDelayMillis = 0L,
        )

        viewModel.setInversionEnabled(true)

        assertEquals(2, backend.applyCount)
        assertEquals("Failed: reapply rejected", viewModel.uiState.value.status)
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
    fun extraDimDefaultsToSystemStrengthWhenMissing() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = false),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertFalse(viewModel.uiState.value.extraDimEnabled)
        assertEquals(0.5f, viewModel.uiState.value.extraDimStrength)
    }

    @Test
    fun extraDimToggleCallsBackendAndUpdatesState() {
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                extraDimAvailable = true,
                activationState = ActivationState.Active,
                displaySnapshot = DisplaySnapshot(extraDimEnabled = false, extraDimStrength = 0.5f),
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimEnabled(true)

        assertTrue(backend.extraDimEnabled == true)
        assertTrue(viewModel.uiState.value.extraDimEnabled)
        assertEquals(0.5f, viewModel.uiState.value.extraDimStrength)
    }

    @Test
    fun extraDimStrengthPreviewDoesNotWriteUntilFinished() {
        val backend = FakeBackend(
            applyResult = BackendResult.Success,
            capabilities = BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = true,
                canWriteSystemSettings = true,
                extraDimAvailable = true,
                activationState = ActivationState.Active,
                displaySnapshot = DisplaySnapshot(extraDimEnabled = true, extraDimStrength = 0.5f),
            ),
        )
        val viewModel = ColorControlViewModel(
            backend = backend,
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimStrength(0.8f)

        assertEquals(null, backend.writtenExtraDimStrength)

        viewModel.finishExtraDimStrengthChange()

        assertEquals(0.8f, backend.writtenExtraDimStrength)
        assertEquals(0.8f, viewModel.uiState.value.extraDimStrength)
    }

    @Test
    fun extraDimControlsRequireFeatureAvailabilityAndSecureSettingsPermission() {
        val unavailableViewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = false,
                    activationState = ActivationState.Active,
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )
        val missingPermissionViewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = false,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )
        val availableViewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = true),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        assertFalse(unavailableViewModel.uiState.value.extraDimControlsEnabled)
        assertFalse(missingPermissionViewModel.uiState.value.extraDimControlsEnabled)
        assertTrue(availableViewModel.uiState.value.extraDimControlsEnabled)
        assertTrue(availableViewModel.uiState.value.extraDimStrengthControlsEnabled)
    }

    @Test
    fun failedExtraDimToggleKeepsPreviousStateWhenSnapshotIsUnavailable() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                extraDimResult = BackendResult.Failed("rejected"),
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = null),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimEnabled(true)

        assertFalse(viewModel.uiState.value.extraDimEnabled)
        assertEquals("Failed: rejected", viewModel.uiState.value.status)
    }

    @Test
    fun successfulExtraDimToggleKeepsRequestedStateWhenSnapshotIsUnavailable() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                extraDimSnapshotReadable = false,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = null),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimEnabled(true)

        assertTrue(viewModel.uiState.value.extraDimEnabled)
        assertTrue(viewModel.uiState.value.extraDimStrengthControlsEnabled)
        assertEquals("Extra dim on", viewModel.uiState.value.status)
    }

    @Test
    fun failedExtraDimStrengthRestoresPreviousSnapshotValue() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                extraDimResult = BackendResult.Failed("rejected"),
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = true, extraDimStrength = 0.4f),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimStrength(0.9f)
        viewModel.finishExtraDimStrengthChange()

        assertEquals(0.4f, viewModel.uiState.value.extraDimStrength)
        assertEquals("Failed: rejected", viewModel.uiState.value.status)
    }

    @Test
    fun failedExtraDimStrengthRestoresLastSuccessfulValueWhenSnapshotIsUnavailable() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                extraDimResults = listOf(
                    BackendResult.Success,
                    BackendResult.Failed("rejected"),
                ),
                extraDimSnapshotReadable = false,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = true, extraDimStrength = null),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.setExtraDimStrength(0.7f)
        viewModel.finishExtraDimStrengthChange()
        viewModel.setExtraDimStrength(0.9f)
        viewModel.finishExtraDimStrengthChange()

        assertEquals(0.7f, viewModel.uiState.value.extraDimStrength)
        assertEquals("Failed: rejected", viewModel.uiState.value.status)
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

    @Test
    fun restoreBaselineTurnsExtraDimOff() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(
                applyResult = BackendResult.Success,
                capabilities = BackendCapabilities(
                    binderAvailable = true,
                    canWriteSecureSettings = true,
                    canWriteSystemSettings = true,
                    extraDimAvailable = true,
                    activationState = ActivationState.Active,
                    displaySnapshot = DisplaySnapshot(extraDimEnabled = true, extraDimStrength = 1f),
                ),
            ),
            profileStore = InMemoryProfileStore(),
            liveApplyScope = testScope(),
            applyDispatcher = Dispatchers.Unconfined,
        )

        viewModel.restoreBaseline()

        assertFalse(viewModel.uiState.value.extraDimEnabled)
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
        private val extraDimResult: BackendResult = systemSettingsResult,
        private val extraDimResults: List<BackendResult> = emptyList(),
        private val extraDimSnapshotReadable: Boolean = true,
        private val restoreResult: BackendResult = BackendResult.Success,
        private val applyResults: List<BackendResult> = emptyList(),
        private val updateExperimentalReadback: Boolean = false,
        private val updateAdvancedColorReadback: Boolean = true,
        private val applyStarted: CountDownLatch? = null,
        private val releaseApply: CountDownLatch? = null,
        private val screenColorStarted: CountDownLatch? = null,
        private val releaseScreenColor: CountDownLatch? = null,
    ) : ColorBackend {
        var appliedProfile: ColorProfile? = null
        var appliedInverted: Boolean = false
        var appliedSaturation: Float = 1f
        var applyCount: Int = 0
        var brightness: Float? = capabilities.displaySnapshot.brightness
        var writtenBrightness: Float? = null
        var autoBrightnessEnabled: Boolean? = capabilities.displaySnapshot.autoBrightness
        var extraDimEnabled: Boolean? = capabilities.displaySnapshot.extraDimEnabled
        var writtenExtraDimStrength: Float? = null
        var extraDimCallCount: Int = 0

        override fun getCapabilities(): BackendCapabilities = capabilities

        override fun readModeSnapshot(): TclModeSnapshot = capabilities.modeSnapshot

        override fun readDisplaySnapshot(): DisplaySnapshot = capabilities.displaySnapshot

        override fun readExperimentalSnapshot(): ExperimentalDisplaySnapshot = capabilities.experimentalSnapshot

        override fun apply(profile: ColorProfile, inverted: Boolean, saturation: Float): BackendResult {
            applyStarted?.countDown()
            releaseApply?.await(2, TimeUnit.SECONDS)
            val result = applyResults.getOrNull(applyCount) ?: applyResult
            applyCount += 1
            appliedProfile = profile
            appliedInverted = inverted
            appliedSaturation = saturation
            if (result == BackendResult.Success) {
                updateDisplaySnapshot(colorInversionEnabled = inverted)
            }
            return result
        }

        override fun restoreBaseline(): BackendResult {
            if (restoreResult == BackendResult.Success) {
                updateDisplaySnapshot(
                    colorInversionEnabled = false,
                    extraDimEnabled = false,
                )
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

        override fun setExtraDimEnabled(enabled: Boolean): BackendResult {
            val result = nextExtraDimResult()
            if (result == BackendResult.Success) {
                extraDimEnabled = enabled
                if (extraDimSnapshotReadable) {
                    updateDisplaySnapshot(extraDimEnabled = enabled)
                }
            }
            return result
        }

        override fun setExtraDimStrength(value: Float): BackendResult {
            val result = nextExtraDimResult()
            if (result == BackendResult.Success) {
                writtenExtraDimStrength = value
                if (extraDimSnapshotReadable) {
                    updateDisplaySnapshot(extraDimStrength = value)
                }
            }
            return result
        }

        override fun setScreenColorMode(mode: ScreenColorMode): BackendResult {
            screenColorStarted?.countDown()
            releaseScreenColor?.await(2, TimeUnit.SECONDS)
            if (systemSettingsResult is BackendResult.Success && updateExperimentalReadback) {
                val previous = capabilities.experimentalSnapshot
                capabilities = capabilities.copy(
                    experimentalSnapshot = previous.copy(
                        screenColorMode = mode,
                        rawScreenColorMode = mode.rawValue,
                        rawAdvancedColorMode = if (mode.isAdvanced && updateAdvancedColorReadback) {
                            mode.rawValue
                        } else {
                            previous.rawAdvancedColorMode
                        },
                    ),
                )
            }
            return systemSettingsResult
        }

        override fun setImageEnhancement(enabled: Boolean): BackendResult {
            if (systemSettingsResult is BackendResult.Success && updateExperimentalReadback) {
                capabilities = capabilities.copy(
                    experimentalSnapshot = capabilities.experimentalSnapshot.copy(
                        imageEnhancementEnabled = enabled,
                    ),
                )
            }
            return systemSettingsResult
        }

        override fun setVideoEnhancement(enabled: Boolean): BackendResult = systemSettingsResult

        override fun setBoldText(enabled: Boolean): BackendResult = systemSettingsResult

        override fun setHighContrastText(enabled: Boolean): BackendResult = systemSettingsResult

        private fun nextExtraDimResult(): BackendResult {
            val result = extraDimResults.getOrNull(extraDimCallCount) ?: extraDimResult
            extraDimCallCount += 1
            return result
        }

        private fun updateDisplaySnapshot(
            brightness: Float? = capabilities.displaySnapshot.brightness,
            autoBrightness: Boolean? = capabilities.displaySnapshot.autoBrightness,
            colorInversionEnabled: Boolean? = capabilities.displaySnapshot.colorInversionEnabled,
            extraDimEnabled: Boolean? = capabilities.displaySnapshot.extraDimEnabled,
            extraDimStrength: Float? = capabilities.displaySnapshot.extraDimStrength,
        ) {
            capabilities = capabilities.copy(
                displaySnapshot = capabilities.displaySnapshot.copy(
                    brightness = brightness,
                    autoBrightness = autoBrightness,
                    colorInversionEnabled = colorInversionEnabled,
                    extraDimEnabled = extraDimEnabled,
                    extraDimStrength = extraDimStrength,
                ),
            )
        }

        fun setExperimentalSnapshot(snapshot: ExperimentalDisplaySnapshot) {
            capabilities = capabilities.copy(experimentalSnapshot = snapshot)
        }
    }

    private class InMemoryProfileStore(
        var savedInversionEnabled: Boolean = false,
        var savedProfile: ColorProfile? = null,
        var savedCustomProfile: ColorProfile? = null,
        var savedSaturation: Float? = null,
        private val saturationSaved: CountDownLatch? = null,
    ) : ProfileStore {
        override fun load(): ColorProfile? = savedProfile

        override fun save(profile: ColorProfile) {
            savedProfile = profile
        }

        override fun loadCustom(): ColorProfile? = savedCustomProfile

        override fun saveCustom(profile: ColorProfile) {
            savedCustomProfile = ColorProfile.custom(profile.red, profile.green, profile.blue)
        }

        override fun loadInversionEnabled(): Boolean = savedInversionEnabled

        override fun saveInversionEnabled(enabled: Boolean) {
            savedInversionEnabled = enabled
        }

        override fun loadSaturation(): Float? = savedSaturation

        override fun saveSaturation(value: Float) {
            savedSaturation = value
            saturationSaved?.countDown()
        }
    }

    private fun testScope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

    private fun waitUntil(
        timeoutMillis: Long = 2_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }
}

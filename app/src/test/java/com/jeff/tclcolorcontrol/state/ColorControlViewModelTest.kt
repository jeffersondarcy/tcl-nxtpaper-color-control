package com.jeff.tclcolorcontrol.state

import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import com.jeff.tclcolorcontrol.device.ActivationState
import com.jeff.tclcolorcontrol.device.BackendCapabilities
import com.jeff.tclcolorcontrol.device.BackendResult
import com.jeff.tclcolorcontrol.device.ColorBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorControlViewModelTest {
    @Test
    fun permissionMissingResultIsVisibleInStatus() {
        val viewModel = ColorControlViewModel(
            backend = FakeBackend(applyResult = BackendResult.PermissionMissing),
            profileStore = InMemoryProfileStore(),
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
        val viewModel = ColorControlViewModel(backend = backend, profileStore = store)

        viewModel.applyProfileId("deep")

        assertEquals(ColorProfiles.Deep, viewModel.uiState.value.selected)
        assertEquals(ColorProfiles.Deep, backend.appliedProfile)
        assertEquals(ColorProfiles.Deep, store.savedProfile)
    }

    private class FakeBackend(
        private val applyResult: BackendResult,
    ) : ColorBackend {
        var appliedProfile: ColorProfile? = null

        override fun getCapabilities(): BackendCapabilities =
            BackendCapabilities(
                binderAvailable = true,
                canWriteSecureSettings = false,
                activationState = ActivationState.Unknown,
            )

        override fun apply(profile: ColorProfile): BackendResult {
            appliedProfile = profile
            return applyResult
        }

        override fun restoreBaseline(): BackendResult = BackendResult.Success
    }

    private class InMemoryProfileStore : ProfileStore {
        var savedProfile: ColorProfile? = null

        override fun load(): ColorProfile? = savedProfile

        override fun save(profile: ColorProfile) {
            savedProfile = profile
        }
    }
}

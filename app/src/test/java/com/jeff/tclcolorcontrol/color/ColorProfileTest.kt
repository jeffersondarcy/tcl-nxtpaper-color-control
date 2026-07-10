package com.jeff.tclcolorcontrol.color

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorProfileTest {
    @Test
    fun redProfileCreatesExpectedMatrix() {
        assertArrayEquals(
            floatArrayOf(
                1.0f, 0f, 0f, 0f,
                0f, 0.25f, 0f, 0f,
                0f, 0f, 0.02f, 0f,
                0f, 0f, 0f, 1.0f,
            ),
            ColorProfiles.Red.toMatrix(),
            0.0001f,
        )
    }

    @Test
    fun customProfileClampsChannels() {
        val profile = ColorProfile.custom(red = 2f, green = -1f, blue = 0.5f)

        assertEquals(1f, profile.red, 0.0001f)
        assertEquals(0f, profile.green, 0.0001f)
        assertEquals(0.5f, profile.blue, 0.0001f)
    }

    @Test
    fun customProfileCannotBecomeBlackWhenAllChannelsAreZero() {
        val profile = ColorProfile.custom(red = 0f, green = 0f, blue = 0f)

        val expectedChannel = ColorProfile.MIN_CUSTOM_CHANNEL_SUM / 3f
        assertEquals(expectedChannel, profile.red, 0.0001f)
        assertEquals(expectedChannel, profile.green, 0.0001f)
        assertEquals(expectedChannel, profile.blue, 0.0001f)
    }

    @Test
    fun customProfileDistributesMissingSumEvenlyWhenTooDim() {
        val profile = ColorProfile.custom(red = 0f, green = 0.05f, blue = 0.09f)
        val bump = (ColorProfile.MIN_CUSTOM_CHANNEL_SUM - 0.14f) / 3f

        assertEquals(bump, profile.red, 0.0001f)
        assertEquals(0.05f + bump, profile.green, 0.0001f)
        assertEquals(0.09f + bump, profile.blue, 0.0001f)
    }

    @Test
    fun customProfileChannelEditStopsAtMinimumTotal() {
        val profile = ColorProfile.customAfterChannelEdit(
            red = 0f,
            green = 0.05f,
            blue = 0.09f,
            editedChannel = ColorChannel.Red,
        )

        assertEquals(0.06f, profile.red, 0.0001f)
        assertEquals(0.05f, profile.green, 0.0001f)
        assertEquals(0.09f, profile.blue, 0.0001f)
    }

    @Test
    fun baselineProfileIsNeutralMatrix() {
        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
            ColorProfiles.Baseline.toMatrix(),
            0.0001f,
        )
    }

    @Test
    fun fullSaturationKeepsExistingProfileMatrix() {
        assertArrayEquals(
            ColorProfiles.Warm.toMatrix(),
            ColorProfiles.Warm.toMatrix(saturation = 1f),
            0.0001f,
        )
    }

    @Test
    fun zeroSaturationUsesAospLuminanceWithoutBlackOutput() {
        val matrix = ColorProfiles.Baseline.toMatrix(saturation = 0f)

        assertArrayEquals(
            floatArrayOf(
                0.231f, 0.231f, 0.231f, 0f,
                0.715f, 0.715f, 0.715f, 0f,
                0.072f, 0.072f, 0.072f, 0f,
                0f, 0f, 0f, 1f,
            ),
            matrix,
            0.0001f,
        )
    }

    @Test
    fun saturationIsClampedToSupportedRange() {
        assertArrayEquals(
            ColorProfiles.Deep.toMatrix(saturation = 1f),
            ColorProfiles.Deep.toMatrix(saturation = 3f),
            0.0001f,
        )
    }

    @Test
    fun saturationIsInferredFromCurrentTclMatrix() {
        val profile = ColorProfile.custom(red = 1f, green = 0.63738304f, blue = 0.35f)
        val currentMatrix =
            "3f3b18fc,3d5313a9,3ce7d029,0,3e8020c5,3f12e4d3,3db36114,0," +
                "3cce703b,3c839491,3e71fdde,0,0,0,0,3f800000"

        assertEquals(0.65f, inferSaturationFromMatrix(profile, currentMatrix)!!, 0.0001f)
    }

    @Test
    fun saturationInferenceRejectsAnIncompatibleMatrix() {
        val identityMatrix =
            "3f800000,0,0,0,0,3f800000,0,0,0,0,3f800000,0,0,0,0,3f800000"

        assertNull(inferSaturationFromMatrix(ColorProfiles.Warm, identityMatrix))
        assertNull(inferSaturationFromMatrix(ColorProfiles.Warm, "not-a-matrix"))
    }
}

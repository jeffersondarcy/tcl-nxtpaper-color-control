package com.jeff.tclcolorcontrol.color

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}

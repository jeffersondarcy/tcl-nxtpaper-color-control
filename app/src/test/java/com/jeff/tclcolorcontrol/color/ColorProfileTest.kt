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
    fun invertedRedProfileCreatesExpectedAffineMatrix() {
        assertArrayEquals(
            floatArrayOf(
                -1.0f, 0f, 0f, 1.0f,
                0f, -0.25f, 0f, 0.25f,
                0f, 0f, -0.02f, 0.02f,
                0f, 0f, 0f, 1.0f,
            ),
            ColorProfiles.Red.toMatrix(inverted = true),
            0.0001f,
        )
    }

    @Test
    fun invertedRedProfileKeepsWarmOutputAfterInversion() {
        val matrix = ColorProfiles.Red.toMatrix(inverted = true)

        assertArrayEquals(
            floatArrayOf(0f, 0f, 0f),
            matrix.applyTo(red = 1f, green = 1f, blue = 1f),
            0.0001f,
        )
        assertArrayEquals(
            floatArrayOf(1f, 0.25f, 0.02f),
            matrix.applyTo(red = 0f, green = 0f, blue = 0f),
            0.0001f,
        )
        assertEquals(0f, matrix.applyTo(red = 0f, green = 0f, blue = 1f)[2], 0.0001f)
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

private fun FloatArray.applyTo(red: Float, green: Float, blue: Float): FloatArray =
    floatArrayOf(
        this[0] * red + this[1] * green + this[2] * blue + this[3],
        this[4] * red + this[5] * green + this[6] * blue + this[7],
        this[8] * red + this[9] * green + this[10] * blue + this[11],
    )

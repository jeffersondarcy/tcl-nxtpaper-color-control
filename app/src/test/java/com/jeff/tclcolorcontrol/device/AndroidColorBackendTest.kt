package com.jeff.tclcolorcontrol.device

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidColorBackendTest {
    @Test
    fun brightnessEndpointMapsToMinimumWritableValue() {
        assertEquals(1, 0f.toScreenBrightnessSetting())
    }

    @Test
    fun brightnessSettingMapsFullRange() {
        assertEquals(128, 0.5f.toScreenBrightnessSetting())
        assertEquals(255, 1f.toScreenBrightnessSetting())
    }

    @Test
    fun brightnessSettingClampsOutOfRangeValues() {
        assertEquals(1, (-0.5f).toScreenBrightnessSetting())
        assertEquals(255, 1.5f.toScreenBrightnessSetting())
    }

    @Test
    fun nearZeroBrightnessNeverWritesSpecialRawZero() {
        assertEquals(1, (1f / 510f).toScreenBrightnessSetting())
        assertEquals(1, (1f / 255f).toScreenBrightnessSetting())
    }

    @Test
    fun extraDimLevelMapsFullRange() {
        assertEquals(0, 0f.toExtraDimLevel())
        assertEquals(50, 0.5f.toExtraDimLevel())
        assertEquals(100, 1f.toExtraDimLevel())
    }

    @Test
    fun extraDimLevelClampsOutOfRangeValues() {
        assertEquals(0, (-0.5f).toExtraDimLevel())
        assertEquals(100, 1.5f.toExtraDimLevel())
    }

    @Test
    fun extraDimStrengthMapsFullRange() {
        assertEquals(0f, 0.toExtraDimStrength())
        assertEquals(0.5f, 50.toExtraDimStrength())
        assertEquals(1f, 100.toExtraDimStrength())
    }

    @Test
    fun extraDimStrengthClampsOutOfRangeValues() {
        assertEquals(0f, (-10).toExtraDimStrength())
        assertEquals(1f, 120.toExtraDimStrength())
    }
}

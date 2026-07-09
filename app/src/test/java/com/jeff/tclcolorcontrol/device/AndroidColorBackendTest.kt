package com.jeff.tclcolorcontrol.device

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidColorBackendTest {
    @Test
    fun brightnessSettingAllowsZero() {
        assertEquals(0, 0f.toScreenBrightnessSetting())
    }

    @Test
    fun brightnessSettingMapsFullRange() {
        assertEquals(128, 0.5f.toScreenBrightnessSetting())
        assertEquals(255, 1f.toScreenBrightnessSetting())
    }

    @Test
    fun brightnessSettingClampsOutOfRangeValues() {
        assertEquals(0, (-0.5f).toScreenBrightnessSetting())
        assertEquals(255, 1.5f.toScreenBrightnessSetting())
    }
}

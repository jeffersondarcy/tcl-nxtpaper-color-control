package com.jeff.tclcolorcontrol.color

import kotlin.math.roundToInt

data class ColorProfile(
    val id: String,
    val label: String,
    val red: Float,
    val green: Float,
    val blue: Float,
) {
    init {
        require(red in CHANNEL_RANGE) { "red must be in 0.0..1.0" }
        require(green in CHANNEL_RANGE) { "green must be in 0.0..1.0" }
        require(blue in CHANNEL_RANGE) { "blue must be in 0.0..1.0" }
    }

    fun toMatrix(inverted: Boolean = false): FloatArray = if (inverted) {
        toInvertedMatrix()
    } else {
        toNormalMatrix()
    }

    private fun toNormalMatrix(): FloatArray = floatArrayOf(
        red, 0f, 0f, 0f,
        0f, green, 0f, 0f,
        0f, 0f, blue, 0f,
        0f, 0f, 0f, 1f,
    )

    private fun toInvertedMatrix(): FloatArray = floatArrayOf(
        -red, 0f, 0f, red,
        0f, -green, 0f, green,
        0f, 0f, -blue, blue,
        0f, 0f, 0f, 1f,
    )

    companion object {
        val CHANNEL_RANGE = 0f..1f

        fun custom(red: Float, green: Float, blue: Float): ColorProfile =
            ColorProfile(
                id = "custom",
                label = "Custom",
                red = red.clampChannel(),
                green = green.clampChannel(),
                blue = blue.clampChannel(),
            )
    }
}

object ColorProfiles {
    val Baseline = ColorProfile("baseline", "Baseline", 1.00f, 1.00f, 1.00f)
    val Warm = ColorProfile("warm", "Warm", 1.00f, 0.75f, 0.35f)
    val Deep = ColorProfile("deep", "Deep", 1.00f, 0.60f, 0.20f)
    val ExtraDeep = ColorProfile("extra_deep", "Extra", 1.00f, 0.45f, 0.05f)
    val Red = ColorProfile("red", "Red", 1.00f, 0.25f, 0.02f)

    val presets = listOf(Baseline, Warm, Deep, ExtraDeep, Red)

    fun byId(id: String): ColorProfile? = presets.firstOrNull { it.id == id }
}

fun Float.clampChannel(): Float = coerceIn(0f, 1f)

fun Float.percentLabel(): String = "${(this * 100f).roundToInt()}%"

package com.jeff.tclcolorcontrol.color

import kotlin.math.abs
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

    fun toMatrix(saturation: Float = 1f): FloatArray {
        val clampedSaturation = saturation.coerceIn(CHANNEL_RANGE)
        val desaturation = 1f - clampedSaturation
        val redLuminance = 0.231f * desaturation
        val greenLuminance = 0.715f * desaturation
        val blueLuminance = 0.072f * desaturation

        return floatArrayOf(
            red * (redLuminance + clampedSaturation), green * redLuminance, blue * redLuminance, 0f,
            red * greenLuminance, green * (greenLuminance + clampedSaturation), blue * greenLuminance, 0f,
            red * blueLuminance, green * blueLuminance, blue * (blueLuminance + clampedSaturation), 0f,
            0f, 0f, 0f, 1f,
        )
    }

    companion object {
        val CHANNEL_RANGE = 0f..1f
        const val MIN_CUSTOM_CHANNEL_SUM = 0.20f

        fun custom(red: Float, green: Float, blue: Float): ColorProfile =
            safeCustomProfile(red, green, blue)

        fun customAfterChannelEdit(
            red: Float,
            green: Float,
            blue: Float,
            editedChannel: ColorChannel,
        ): ColorProfile =
            safeCustomProfileAfterChannelEdit(red, green, blue, editedChannel)
    }
}

enum class ColorChannel {
    Red,
    Green,
    Blue,
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

internal fun inferSaturationFromMatrix(
    profile: ColorProfile,
    serializedMatrix: String?,
    tolerance: Float = 0.01f,
): Float? {
    val matrix = serializedMatrix?.toHexFloatMatrix() ?: return null
    val candidates = buildList {
        if (profile.red > INFERENCE_CHANNEL_EPSILON) {
            add(((matrix[0] / profile.red) - 0.231f) / 0.769f)
        }
        if (profile.green > INFERENCE_CHANNEL_EPSILON) {
            add(((matrix[5] / profile.green) - 0.715f) / 0.285f)
        }
        if (profile.blue > INFERENCE_CHANNEL_EPSILON) {
            add(((matrix[10] / profile.blue) - 0.072f) / 0.928f)
        }
    }.filter { it.isFinite() && it in INFERENCE_RANGE }
    if (candidates.isEmpty()) return null

    val saturation = candidates.sorted()[candidates.size / 2].coerceIn(0f, 1f)
    val reconstructed = profile.toMatrix(saturation)
    return saturation.takeIf { inferred ->
        inferred.isFinite() && matrix.indices.all { index ->
            abs(matrix[index] - reconstructed[index]) <= tolerance
        }
    }
}

private fun String.toHexFloatMatrix(): FloatArray? {
    val values = split(',')
    if (values.size != MATRIX_ELEMENT_COUNT) return null
    return runCatching {
        FloatArray(values.size) { index ->
            Float.fromBits(values[index].toLong(16).toInt())
        }
    }.getOrNull()
}

private const val MATRIX_ELEMENT_COUNT = 16
private const val INFERENCE_CHANNEL_EPSILON = 0.001f
private val INFERENCE_RANGE = -0.05f..1.05f

private fun safeCustomProfile(red: Float, green: Float, blue: Float): ColorProfile {
    val channels = floatArrayOf(red.clampChannel(), green.clampChannel(), blue.clampChannel())
    val sum = channels.sum()
    if (sum < ColorProfile.MIN_CUSTOM_CHANNEL_SUM) {
        val bump = (ColorProfile.MIN_CUSTOM_CHANNEL_SUM - sum) / channels.size
        channels.indices.forEach { index ->
            channels[index] = (channels[index] + bump).clampChannel()
        }
    }
    return customProfileFromChannels(channels)
}

private fun safeCustomProfileAfterChannelEdit(
    red: Float,
    green: Float,
    blue: Float,
    editedChannel: ColorChannel,
): ColorProfile {
    val channels = floatArrayOf(red.clampChannel(), green.clampChannel(), blue.clampChannel())
    val editedIndex = editedChannel.index
    val otherSum = channels.sum() - channels[editedIndex]
    val minimumEditedValue = (ColorProfile.MIN_CUSTOM_CHANNEL_SUM - otherSum).coerceIn(ColorProfile.CHANNEL_RANGE)
    channels[editedIndex] = channels[editedIndex].coerceAtLeast(minimumEditedValue)
    return customProfileFromChannels(channels)
}

private val ColorChannel.index: Int
    get() = when (this) {
        ColorChannel.Red -> 0
        ColorChannel.Green -> 1
        ColorChannel.Blue -> 2
    }

private fun customProfileFromChannels(channels: FloatArray): ColorProfile =
    ColorProfile(
        id = "custom",
        label = "Custom",
        red = channels[0],
        green = channels[1],
        blue = channels[2],
    )

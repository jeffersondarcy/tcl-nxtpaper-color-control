package com.jeff.tclcolorcontrol.state

import android.content.Context
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles

interface ProfileStore {
    fun load(): ColorProfile?
    fun save(profile: ColorProfile)
    fun loadCustom(): ColorProfile?
    fun saveCustom(profile: ColorProfile)
    fun loadInversionEnabled(): Boolean
    fun saveInversionEnabled(enabled: Boolean)
}

class SharedPreferencesProfileStore(
    context: Context,
) : ProfileStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): ColorProfile? {
        val id = preferences.getString(KEY_ID, null) ?: return null
        ColorProfiles.byId(id)?.let { return it }

        val red = preferences.getFloat(KEY_RED, Float.NaN)
        val green = preferences.getFloat(KEY_GREEN, Float.NaN)
        val blue = preferences.getFloat(KEY_BLUE, Float.NaN)
        if (red.isNaN() || green.isNaN() || blue.isNaN()) return null
        return ColorProfile.custom(red, green, blue)
    }

    override fun save(profile: ColorProfile) {
        preferences.edit()
            .putString(KEY_ID, profile.id)
            .putFloat(KEY_RED, profile.red)
            .putFloat(KEY_GREEN, profile.green)
            .putFloat(KEY_BLUE, profile.blue)
            .apply()
    }

    override fun loadCustom(): ColorProfile? {
        val red = preferences.getFloat(KEY_CUSTOM_RED, Float.NaN)
        val green = preferences.getFloat(KEY_CUSTOM_GREEN, Float.NaN)
        val blue = preferences.getFloat(KEY_CUSTOM_BLUE, Float.NaN)
        if (!red.isNaN() && !green.isNaN() && !blue.isNaN()) {
            return ColorProfile.custom(red, green, blue)
        }

        if (preferences.getString(KEY_ID, null) == CUSTOM_PROFILE_ID) {
            return load()
        }
        return null
    }

    override fun saveCustom(profile: ColorProfile) {
        val customProfile = ColorProfile.custom(profile.red, profile.green, profile.blue)
        preferences.edit()
            .putFloat(KEY_CUSTOM_RED, customProfile.red)
            .putFloat(KEY_CUSTOM_GREEN, customProfile.green)
            .putFloat(KEY_CUSTOM_BLUE, customProfile.blue)
            .apply()
    }

    override fun loadInversionEnabled(): Boolean =
        preferences.getBoolean(KEY_INVERSION_ENABLED, false)

    override fun saveInversionEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_INVERSION_ENABLED, enabled)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "color_profiles"
        const val KEY_ID = "id"
        const val KEY_RED = "red"
        const val KEY_GREEN = "green"
        const val KEY_BLUE = "blue"
        const val KEY_CUSTOM_RED = "custom_red"
        const val KEY_CUSTOM_GREEN = "custom_green"
        const val KEY_CUSTOM_BLUE = "custom_blue"
        const val KEY_INVERSION_ENABLED = "inversion_enabled"
        const val CUSTOM_PROFILE_ID = "custom"
    }
}

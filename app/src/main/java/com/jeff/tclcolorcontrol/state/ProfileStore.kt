package com.jeff.tclcolorcontrol.state

import android.content.Context
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles

interface ProfileStore {
    fun load(): ColorProfile?
    fun save(profile: ColorProfile)
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

    private companion object {
        const val PREFERENCES_NAME = "color_profiles"
        const val KEY_ID = "id"
        const val KEY_RED = "red"
        const val KEY_GREEN = "green"
        const val KEY_BLUE = "blue"
    }
}

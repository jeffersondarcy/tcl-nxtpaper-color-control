package com.jeff.tclcolorcontrol

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon

object QuickSettingsTilePrompt {
    fun isKnownAdded(context: Context): Boolean =
        preferences(context).getBoolean(KEY_TILE_KNOWN_ADDED, false)

    fun wasAutoPrompted(context: Context): Boolean =
        preferences(context).getBoolean(KEY_AUTO_PROMPTED, false)

    fun markAutoPrompted(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_AUTO_PROMPTED, true)
            .apply()
    }

    fun markTileRemoved(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_TILE_KNOWN_ADDED, false)
            .apply()
    }

    fun requestAddTile(activity: Activity, onResult: (Int) -> Unit) {
        val statusBarManager = activity.getSystemService(StatusBarManager::class.java)
        val component = ComponentName(activity, ColorControlTileService::class.java)
        val icon = Icon.createWithResource(activity, R.drawable.ic_launcher)
        statusBarManager.requestAddTileService(
            component,
            activity.getString(R.string.quick_settings_tile_label),
            icon,
            activity.mainExecutor,
        ) { result ->
            if (result.isTileAddedResult()) {
                preferences(activity).edit()
                    .putBoolean(KEY_TILE_KNOWN_ADDED, true)
                    .apply()
            }
            onResult(result)
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "quick_settings_tile"
    private const val KEY_TILE_KNOWN_ADDED = "tile_known_added"
    private const val KEY_AUTO_PROMPTED = "auto_prompted"
}

fun Int.isTileAddedResult(): Boolean =
    this == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
        this == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED

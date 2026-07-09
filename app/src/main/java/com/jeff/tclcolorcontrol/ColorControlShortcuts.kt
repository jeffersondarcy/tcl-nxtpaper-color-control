package com.jeff.tclcolorcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

object ColorControlShortcuts {
    fun publish(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_OPEN_COLOR_CONTROLS)
            .setShortLabel(context.getString(R.string.shortcut_open_panel_short_label))
            .setLongLabel(context.getString(R.string.shortcut_open_panel_long_label))
            .setDisabledMessage(context.getString(R.string.shortcut_open_panel_disabled))
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher))
            .setIntent(openPanelIntent(context))
            .build()
        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }

    private fun openPanelIntent(context: Context): Intent =
        Intent(context, ColorControlActivity::class.java)
            .setAction(ColorControlActivity.ACTION_OPEN_PANEL)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )

    private const val SHORTCUT_OPEN_COLOR_CONTROLS = "open_color_controls"
}

class ColorControlShortcutRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ColorControlShortcuts.publish(context)
        }
    }
}

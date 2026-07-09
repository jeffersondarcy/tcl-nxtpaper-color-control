package com.jeff.tclcolorcontrol

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ColorControlTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        ColorControlShortcuts.publish(this)
        qsTile?.apply {
            label = getString(R.string.quick_settings_tile_label)
            subtitle = getString(R.string.quick_settings_tile_subtitle)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { launchColorControl() }
        } else {
            launchColorControl()
        }
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        QuickSettingsTilePrompt.markTileRemoved(this)
    }

    private fun launchColorControl() {
        val intent = Intent(this, ColorControlActivity::class.java)
            .setAction(ColorControlActivity.ACTION_OPEN_PANEL)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            .putExtra(ColorControlActivity.EXTRA_FROM_TILE, true)
        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_COLOR_CONTROL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
    }

    private companion object {
        const val REQUEST_OPEN_COLOR_CONTROL = 1001
    }
}

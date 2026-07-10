package com.jeff.tclcolorcontrol

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity

class ColorControlActivity : ComponentActivity() {
    private var requestedOverlayPermission = false
    private var pendingEntryIntent: Intent? = null
    private var tilePromptInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ColorControlShortcuts.publish(this)
        pendingEntryIntent = intent
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEntryIntent = intent
    }

    override fun onResume() {
        super.onResume()
        if (pendingEntryIntent == null && requestedOverlayPermission) {
            pendingEntryIntent = intent
        }
        handlePendingEntryIntent()
    }

    private fun handlePendingEntryIntent() {
        val entryIntent = pendingEntryIntent ?: return
        if (Settings.canDrawOverlays(this)) {
            requestedOverlayPermission = false
            pendingEntryIntent = null
            showOverlayAfterOptionalTilePrompt(entryIntent)
        } else if (requestedOverlayPermission) {
            pendingEntryIntent = null
            Toast.makeText(
                this,
                "Display over other apps is required for the floating color panel",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        } else {
            requestOverlayPermission()
        }
    }

    private fun showOverlayAfterOptionalTilePrompt(intent: Intent?) {
        if (!shouldAutoPromptForTile(intent)) {
            showOverlay()
            return
        }
        tilePromptInProgress = true
        QuickSettingsTilePrompt.requestAddTile(this) {
            QuickSettingsTilePrompt.markAutoPrompted(this)
            tilePromptInProgress = false
            if (!isFinishing && !isDestroyed) {
                showOverlay()
            }
        }
    }

    private fun shouldAutoPromptForTile(intent: Intent?): Boolean =
        !tilePromptInProgress &&
            !intent.isQuickEntry() &&
            !QuickSettingsTilePrompt.isKnownAdded(this) &&
            !QuickSettingsTilePrompt.wasAutoPrompted(this)

    private fun showOverlay() {
        val serviceIntent = ColorControlOverlayService.showExpandedIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun requestOverlayPermission() {
        requestedOverlayPermission = true
        Toast.makeText(
            this,
            "Grant Display over other apps for the floating color panel",
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        )
    }

    companion object {
        const val ACTION_OPEN_PANEL = "com.jeff.tclcolorcontrol.action.OPEN_PANEL"
        const val EXTRA_FROM_TILE = "from_tile"
    }
}

private fun Intent?.isQuickEntry(): Boolean =
    this?.getBooleanExtra(ColorControlActivity.EXTRA_FROM_TILE, false) == true ||
        this?.action == ColorControlActivity.ACTION_OPEN_PANEL

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
            showOverlay(entryIntent)
        } else {
            requestOverlayPermission()
        }
    }

    private fun showOverlay(intent: Intent?) {
        val serviceIntent = ColorControlOverlayService.showExpandedIntent(this, intent)
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
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_APPLY = "apply"
        const val EXTRA_RESTORE = "restore"
        const val EXTRA_FROM_TILE = "from_tile"
    }
}

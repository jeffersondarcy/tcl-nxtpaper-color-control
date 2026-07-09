package com.jeff.tclcolorcontrol

import android.app.Activity
import android.app.StatusBarManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeff.tclcolorcontrol.device.AndroidColorBackend
import com.jeff.tclcolorcontrol.state.ColorControlViewModel
import com.jeff.tclcolorcontrol.state.ColorControlViewModelFactory
import com.jeff.tclcolorcontrol.state.SharedPreferencesProfileStore
import com.jeff.tclcolorcontrol.ui.ColorControlScreen
import com.jeff.tclcolorcontrol.ui.TclColorControlTheme

class ColorControlActivity : ComponentActivity() {
    private var legacyPanelPosition = PanelPosition.TopCenter
    private var panelCoordinates: PanelWindowCoordinates? = null
    private var dragCoordinates: PanelWindowDragCoordinates? = null
    private var lastPanelWidthPx = 0
    private var lastPanelHeightPx = 0
    private val tileKnownAddedState = mutableStateOf(false)
    private val quickEntryState = mutableStateOf(false)

    private val viewModel: ColorControlViewModel by viewModels {
        ColorControlViewModelFactory(
            backend = AndroidColorBackend(this),
            profileStore = SharedPreferencesProfileStore(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        legacyPanelPosition = loadLegacyPanelPosition()
        tileKnownAddedState.value = QuickSettingsTilePrompt.isKnownAdded(this)
        quickEntryState.value = intent.isQuickEntry()
        ColorControlShortcuts.publish(this)
        configureDialogWindow(legacyPanelPosition)
        handleIntent(intent)

        setContent {
            TclColorControlTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ColorControlScreen(
                    state = state,
                    onSelectProfile = viewModel::selectProfile,
                    onRedChange = viewModel::setRed,
                    onGreenChange = viewModel::setGreen,
                    onBlueChange = viewModel::setBlue,
                    onSliderFinished = viewModel::finishSliderChange,
                    onInversionChange = viewModel::setInversionEnabled,
                    onAutoBrightnessChange = viewModel::setAutoBrightness,
                    onBrightnessChange = viewModel::setBrightness,
                    onBrightnessFinished = viewModel::finishBrightnessChange,
                    onGrantSystemSettings = ::openWriteSettingsPanel,
                    onEnableCustom = viewModel::enableCustomMode,
                    onSwitchClassic = viewModel::switchToClassicSafeMode,
                    onRestore = viewModel::restoreBaseline,
                    onMovePanel = ::movePanelBy,
                    onMovePanelFinished = ::saveCurrentPanelCoordinates,
                    onPanelSizeChanged = ::handlePanelSizeChanged,
                    showAddTile = !quickEntryState.value && !tileKnownAddedState.value,
                    onAddTile = { requestQuickSettingsTile(auto = false) },
                    onDismiss = ::finish,
                )
            }
        }
        window.decorView.post {
            initializePanelCoordinates()
        }
        if (!quickEntryState.value) {
            window.decorView.post {
                requestQuickSettingsTile(auto = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        quickEntryState.value = intent.isQuickEntry()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_RESTORE, false)) {
            viewModel.restoreBaseline()
            return
        }
        val profileId = intent.getStringExtra(EXTRA_PROFILE) ?: return
        if (intent.getBooleanExtra(EXTRA_APPLY, false)) {
            viewModel.applyProfileId(profileId)
        } else {
            viewModel.selectProfileId(profileId)
        }
    }

    private fun Activity.configureDialogWindow(position: PanelPosition) {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window.setGravity(position.gravity)
        val attrs = window.attributes
        val offset = resources.displayMetrics.heightPixels / 12
        attrs.x = if (position.alignsEnd) 16.dpToPx() else 0
        attrs.y = offset
        window.attributes = attrs
    }

    private fun initializePanelCoordinates() {
        val saved = loadExactPanelCoordinates()
        val bounds = currentPanelBounds()
        val next = saved?.let { PanelWindowPositioner.clamp(it, bounds) }
            ?: PanelWindowPositioner.fromAnchor(
                anchor = legacyPanelPosition.anchor,
                bounds = bounds,
                topOffsetPx = resources.displayMetrics.heightPixels / 12,
                endOffsetPx = 16.dpToPx(),
            )
        panelCoordinates = next
        dragCoordinates = next.toDragCoordinates()
        applyPanelCoordinates(next)
        savePanelCoordinates(next)
    }

    private fun movePanelBy(deltaX: Float, deltaY: Float) {
        if (panelCoordinates == null || dragCoordinates == null) {
            initializePanelCoordinates()
        }
        val current = dragCoordinates ?: return
        val move = PanelWindowPositioner.moveBy(
            coordinates = current,
            deltaX = deltaX,
            deltaY = deltaY,
            bounds = currentPanelBounds(),
        )
        dragCoordinates = move.dragCoordinates
        panelCoordinates = move.windowCoordinates
        applyPanelCoordinates(move.windowCoordinates)
    }

    private fun saveCurrentPanelCoordinates() {
        panelCoordinates?.let(::savePanelCoordinates)
    }

    private fun handlePanelSizeChanged(widthPx: Int, heightPx: Int) {
        lastPanelWidthPx = widthPx
        lastPanelHeightPx = heightPx
        clampPanelToCurrentSize()
    }

    private fun clampPanelToCurrentSize() {
        val current = panelCoordinates ?: return
        val next = PanelWindowPositioner.clamp(current, currentPanelBounds())
        if (next != current) {
            panelCoordinates = next
            dragCoordinates = next.toDragCoordinates()
            applyPanelCoordinates(next)
            savePanelCoordinates(next)
        }
    }

    private fun applyPanelCoordinates(coordinates: PanelWindowCoordinates) {
        window.setGravity(Gravity.TOP or Gravity.START)
        val attrs = window.attributes
        attrs.x = coordinates.xPx
        attrs.y = coordinates.yPx
        window.attributes = attrs
    }

    private fun currentPanelBounds(): PanelWindowBounds {
        val metrics = resources.displayMetrics
        val decorView = window.decorView
        return PanelWindowBounds(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            windowWidthPx = lastPanelWidthPx.takeIf { it > 0 } ?: decorView.width.coerceAtLeast(0),
            windowHeightPx = lastPanelHeightPx.takeIf { it > 0 } ?: decorView.height.coerceAtLeast(0),
            marginPx = 12.dpToPx(),
        )
    }

    private fun requestQuickSettingsTile(auto: Boolean) {
        if (tileKnownAddedState.value) return
        if (auto && QuickSettingsTilePrompt.wasAutoPrompted(this)) return
        QuickSettingsTilePrompt.requestAddTile(this) { result ->
            if (auto && result.shouldRememberAutoPrompt()) {
                QuickSettingsTilePrompt.markAutoPrompted(this)
            }
            if (result.isTileAddedResult()) {
                tileKnownAddedState.value = true
            }
            if (!auto) {
                Toast.makeText(this, result.tileRequestToast(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openWriteSettingsPanel() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun Int.shouldRememberAutoPrompt(): Boolean =
        this == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            this == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ||
            this == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED

    private fun Int.tileRequestToast(): String =
        when (this) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> getString(R.string.quick_settings_tile_added)
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> getString(R.string.quick_settings_tile_already_added)
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> getString(R.string.quick_settings_tile_not_added)
            else -> getString(R.string.quick_settings_tile_add_failed)
        }

    private fun loadLegacyPanelPosition(): PanelPosition {
        val key = getSharedPreferences(PANEL_PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_PANEL_POSITION, null)
        return PanelPosition.entries.firstOrNull { it.key == key } ?: PanelPosition.TopCenter
    }

    private fun loadExactPanelCoordinates(): PanelWindowCoordinates? {
        val preferences = getSharedPreferences(PANEL_PREFERENCES_NAME, MODE_PRIVATE)
        if (!preferences.contains(KEY_PANEL_X_PX) || !preferences.contains(KEY_PANEL_Y_PX)) {
            return null
        }
        return PanelWindowCoordinates(
            xPx = preferences.getInt(KEY_PANEL_X_PX, 0),
            yPx = preferences.getInt(KEY_PANEL_Y_PX, 0),
        )
    }

    private fun savePanelCoordinates(coordinates: PanelWindowCoordinates) {
        getSharedPreferences(PANEL_PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_PANEL_X_PX, coordinates.xPx)
            .putInt(KEY_PANEL_Y_PX, coordinates.yPx)
            .apply()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun Intent?.isQuickEntry(): Boolean =
        this?.getBooleanExtra(EXTRA_FROM_TILE, false) == true || this?.action == ACTION_OPEN_PANEL

    companion object {
        const val ACTION_OPEN_PANEL = "com.jeff.tclcolorcontrol.action.OPEN_PANEL"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_APPLY = "apply"
        const val EXTRA_RESTORE = "restore"
        const val EXTRA_FROM_TILE = "from_tile"
        private const val PANEL_PREFERENCES_NAME = "panel_window"
        private const val KEY_PANEL_POSITION = "position"
        private const val KEY_PANEL_X_PX = "x_px"
        private const val KEY_PANEL_Y_PX = "y_px"
    }
}

private enum class PanelPosition(
    val key: String,
    val label: String,
    val gravity: Int,
    val anchor: PanelWindowAnchor,
    val alignsEnd: Boolean = false,
) {
    TopCenter("top_center", "Top", Gravity.TOP or Gravity.CENTER_HORIZONTAL, PanelWindowAnchor.TopCenter),
    TopRight("top_right", "Right", Gravity.TOP or Gravity.END, PanelWindowAnchor.TopRight, alignsEnd = true),
    BottomRight("bottom_right", "Bottom right", Gravity.BOTTOM or Gravity.END, PanelWindowAnchor.BottomRight, alignsEnd = true),
    BottomCenter("bottom_center", "Bottom", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, PanelWindowAnchor.BottomCenter);
}

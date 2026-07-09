package com.jeff.tclcolorcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.runtime.R as LifecycleRuntimeR
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.R as SavedStateR
import com.jeff.tclcolorcontrol.device.AndroidColorBackend
import com.jeff.tclcolorcontrol.state.ColorControlViewModel
import com.jeff.tclcolorcontrol.state.SharedPreferencesProfileStore
import com.jeff.tclcolorcontrol.ui.ColorControlScreen
import com.jeff.tclcolorcontrol.ui.TclColorControlTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ColorControlOverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isCollapsedState = mutableStateOf(false)
    private lateinit var windowManager: WindowManager
    private lateinit var viewModel: ColorControlViewModel
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var panelCoordinates: PanelWindowCoordinates? = null
    private var dragCoordinates: PanelWindowDragCoordinates? = null
    private var lastPanelWidthPx = 0
    private var lastPanelHeightPx = 0

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        savedStateRegistryController.performAttach()
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        viewModel = ColorControlViewModel(
            backend = AndroidColorBackend(this),
            profileStore = SharedPreferencesProfileStore(this),
            liveApplyScope = serviceScope,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, overlayNotification())
        when (intent?.action ?: ACTION_SHOW_EXPANDED) {
            ACTION_CLOSE -> stopOverlay()
            ACTION_SHOW_EXPANDED -> {
                showOverlay()
                setPanelCollapsed(false)
                handleColorIntent(intent)
            }
            else -> {
                showOverlay()
                handleColorIntent(intent)
            }
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        closeOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val view = ComposeView(this)
        view.setTag(LifecycleRuntimeR.id.view_tree_lifecycle_owner, this)
        view.setTag(SavedStateR.id.view_tree_saved_state_registry_owner, this)
        view.setContent {
            TclColorControlTheme {
                val state by viewModel.uiState.collectAsState()
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
                    isCollapsed = isCollapsedState.value,
                    onCollapsedChange = ::setPanelCollapsed,
                    onMovePanel = ::movePanelBy,
                    onMovePanelFinished = ::saveCurrentPanelCoordinates,
                    onPanelSizeChanged = ::handlePanelSizeChanged,
                    onDismiss = ::stopOverlay,
                )
            }
        }
        overlayView = view
        layoutParams = initialLayoutParams()
        windowManager.addView(view, layoutParams)
        view.post {
            initializePanelCoordinates()
        }
    }

    private fun setPanelCollapsed(isCollapsed: Boolean) {
        isCollapsedState.value = isCollapsed
        overlayView?.post {
            overlayView?.requestLayout()
            layoutParams?.let { params ->
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                overlayView?.let { windowManager.updateViewLayout(it, params) }
            }
            lastPanelWidthPx = 0
            lastPanelHeightPx = 0
            clampPanelToCurrentSize()
        }
    }

    private fun stopOverlay() {
        closeOverlay()
        stopSelf()
    }

    private fun closeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
    }

    private fun initialLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val saved = loadExactPanelCoordinates()
            if (saved != null) {
                x = saved.xPx
                y = saved.yPx
            } else {
                x = 0
                y = resources.displayMetrics.heightPixels / 12
            }
        }

    private fun overlayNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }

    private fun handleColorIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(ColorControlActivity.EXTRA_RESTORE, false)) {
            viewModel.restoreBaseline()
            return
        }
        val profileId = intent.getStringExtra(ColorControlActivity.EXTRA_PROFILE) ?: return
        if (intent.getBooleanExtra(ColorControlActivity.EXTRA_APPLY, false)) {
            viewModel.applyProfileId(profileId)
        } else {
            viewModel.selectProfileId(profileId)
        }
    }

    private fun openWriteSettingsPanel() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                android.net.Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun initializePanelCoordinates() {
        val bounds = currentPanelBounds()
        val next = loadExactPanelCoordinates()?.let { PanelWindowPositioner.clamp(it, bounds) }
            ?: loadLegacyPanelCoordinates(bounds)
            ?: PanelWindowPositioner.fromAnchor(
                anchor = PanelWindowAnchor.TopCenter,
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

    private fun loadLegacyPanelCoordinates(bounds: PanelWindowBounds): PanelWindowCoordinates? {
        val key = getSharedPreferences(PANEL_PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_PANEL_POSITION, null)
        return PanelWindowPositioner.fromLegacyPositionKey(
            positionKey = key,
            bounds = bounds,
            topOffsetPx = resources.displayMetrics.heightPixels / 12,
            endOffsetPx = 16.dpToPx(),
        )
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
        val params = layoutParams ?: return
        params.x = coordinates.xPx
        params.y = coordinates.yPx
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun currentPanelBounds(): PanelWindowBounds {
        val metrics = resources.displayMetrics
        return PanelWindowBounds(
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            windowWidthPx = lastPanelWidthPx.takeIf { it > 0 } ?: overlayView?.width?.coerceAtLeast(0) ?: 0,
            windowHeightPx = lastPanelHeightPx.takeIf { it > 0 } ?: overlayView?.height?.coerceAtLeast(0) ?: 0,
            marginPx = 12.dpToPx(),
        )
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

    companion object {
        private const val ACTION_SHOW_EXPANDED = "com.jeff.tclcolorcontrol.action.SHOW_OVERLAY_EXPANDED"
        private const val ACTION_CLOSE = "com.jeff.tclcolorcontrol.action.CLOSE_OVERLAY"
        private const val NOTIFICATION_CHANNEL_ID = "floating_color_controls"
        private const val NOTIFICATION_ID = 2001
        private const val PANEL_PREFERENCES_NAME = "panel_window"
        private const val KEY_PANEL_POSITION = "position"
        private const val KEY_PANEL_X_PX = "x_px"
        private const val KEY_PANEL_Y_PX = "y_px"

        fun showExpandedIntent(context: Context, sourceIntent: Intent? = null): Intent =
            Intent(context, ColorControlOverlayService::class.java)
                .setAction(ACTION_SHOW_EXPANDED)
                .copyColorExtrasFrom(sourceIntent)
    }
}

private fun Intent.copyColorExtrasFrom(sourceIntent: Intent?): Intent {
    if (sourceIntent == null) return this
    sourceIntent.getStringExtra(ColorControlActivity.EXTRA_PROFILE)?.let {
        putExtra(ColorControlActivity.EXTRA_PROFILE, it)
    }
    putExtra(
        ColorControlActivity.EXTRA_APPLY,
        sourceIntent.getBooleanExtra(ColorControlActivity.EXTRA_APPLY, false),
    )
    putExtra(
        ColorControlActivity.EXTRA_RESTORE,
        sourceIntent.getBooleanExtra(ColorControlActivity.EXTRA_RESTORE, false),
    )
    return this
}

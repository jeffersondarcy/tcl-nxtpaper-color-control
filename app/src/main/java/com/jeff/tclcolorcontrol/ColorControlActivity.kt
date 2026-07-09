package com.jeff.tclcolorcontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jeff.tclcolorcontrol.device.AndroidColorBackend
import com.jeff.tclcolorcontrol.state.ColorControlViewModel
import com.jeff.tclcolorcontrol.state.ColorControlViewModelFactory
import com.jeff.tclcolorcontrol.state.SharedPreferencesProfileStore
import com.jeff.tclcolorcontrol.ui.ColorControlScreen
import com.jeff.tclcolorcontrol.ui.TclColorControlTheme

class ColorControlActivity : ComponentActivity() {
    private val viewModel: ColorControlViewModel by viewModels {
        ColorControlViewModelFactory(
            backend = AndroidColorBackend(this),
            profileStore = SharedPreferencesProfileStore(this),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        configureDialogWindow()
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
                    onApply = viewModel::applyCurrent,
                    onRestore = viewModel::restoreBaseline,
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    private fun Activity.configureDialogWindow() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        val attrs = window.attributes
        attrs.y = resources.displayMetrics.heightPixels / 12
        window.attributes = attrs
    }

    private companion object {
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_APPLY = "apply"
        const val EXTRA_RESTORE = "restore"
    }
}

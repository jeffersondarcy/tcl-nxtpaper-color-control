package com.jeff.tclcolorcontrol.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles
import kotlin.math.roundToInt

class AndroidColorBackend(
    private val context: Context,
) : ColorBackend {
    override fun getCapabilities(): BackendCapabilities =
        BackendCapabilities(
            binderAvailable = findTclBinder() != null,
            canWriteSecureSettings = canWriteSecureSettings(),
            canWriteSystemSettings = canWriteSystemSettings(),
            activationState = readActivationState(),
            modeSnapshot = readModeSnapshot(),
            displaySnapshot = readDisplaySnapshot(),
        )

    override fun readModeSnapshot(): TclModeSnapshot =
        TclModeSnapshot(
            eyeProtectStatus = getSystemInt(EYEPROTECT_STATUS),
            eyeProtectKind = getSystemInt(EYEPROTECT_KIND),
            eyeProtectClassicMode = getSecureInt(EYEPROTECT_CLASSIC_MODE),
            eyeProtectPersonalizedSet = getSystemInt(EYEPROTECT_PERSONALIZED_SET),
            colorModeValue = getSystemInt(COLOR_MODE_VALUE),
            advancedColorModeValue = getSystemInt(ADVANCED_COLOR_MODE_VALUE),
            matrixActive = getSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED),
            matrix = getSecureString(TCL_COLOR_TEMPERATURE_MATRIX),
        )

    override fun readDisplaySnapshot(): DisplaySnapshot {
        val rawBrightness = getSystemInt(Settings.System.SCREEN_BRIGHTNESS)
        val brightness = rawBrightness?.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)?.toFloat()
            ?.div(MAX_BRIGHTNESS.toFloat())
        val autoBrightness = when (getSystemInt(Settings.System.SCREEN_BRIGHTNESS_MODE)) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> true
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> false
            else -> null
        }
        return DisplaySnapshot(
            brightness = brightness,
            rawBrightness = rawBrightness,
            autoBrightness = autoBrightness,
            colorInversionEnabled = when (getSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)) {
                1 -> true
                0 -> false
                else -> null
            },
        )
    }

    override fun apply(profile: ColorProfile, inverted: Boolean): BackendResult {
        val binder = findTclBinder() ?: return BackendResult.BinderUnavailable
        val matrix = profile.toMatrix()

        if (!canWriteSecureSettings()) {
            val matrixResult = binder.callSetSurfaceFlingerMatrix(matrix)
            return if (matrixResult is BackendResult.Success) {
                BackendResult.PermissionMissing
            } else {
                matrixResult
            }
        }

        val previousActivationValue = getSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED)
        val previousInversionValue = getSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED)
        val activationResult = putSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, 1)
        if (activationResult !is BackendResult.Success) return activationResult

        val desiredInversionValue = if (inverted) 1 else 0
        val inversionResult = putSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, desiredInversionValue)
        if (inversionResult !is BackendResult.Success) {
            restoreSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, previousActivationValue)
            return inversionResult
        }

        val matrixResult = binder.callSetSurfaceFlingerMatrix(matrix)
        if (matrixResult !is BackendResult.Success) {
            restoreSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, previousInversionValue)
            restoreSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, previousActivationValue)
        }
        return matrixResult
    }

    override fun setBrightness(value: Float): BackendResult {
        if (!canWriteSystemSettings()) return BackendResult.SystemSettingsPermissionMissing
        return putSystemInt(
            Settings.System.SCREEN_BRIGHTNESS,
            (value.coerceIn(BRIGHTNESS_RANGE) * MAX_BRIGHTNESS).roundToInt()
                .coerceIn(MIN_WRITABLE_BRIGHTNESS, MAX_BRIGHTNESS),
        )
    }

    override fun setAutoBrightness(enabled: Boolean): BackendResult {
        if (!canWriteSystemSettings()) return BackendResult.SystemSettingsPermissionMissing
        return putSystemInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (enabled) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            },
        )
    }

    override fun restoreBaseline(): BackendResult {
        val binder = findTclBinder() ?: return BackendResult.BinderUnavailable
        val matrixResult = binder.callSetSurfaceFlingerMatrix(ColorProfiles.Baseline.toMatrix())
        if (matrixResult !is BackendResult.Success) return matrixResult

        return if (canWriteSecureSettings()) {
            val inversionResult = putSecureInt(ACCESSIBILITY_DISPLAY_INVERSION_ENABLED, 0)
            if (inversionResult !is BackendResult.Success) {
                inversionResult
            } else {
                putSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, 0)
            }
        } else {
            BackendResult.PermissionMissing
        }
    }

    private fun findTclBinder(): IBinder? = runCatching {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        getService.isAccessible = true
        getService.invoke(null, TCL_NXTVISION_SERVICE) as? IBinder
    }.getOrNull()

    private fun IBinder.callSetSurfaceFlingerMatrix(matrix: FloatArray): BackendResult {
        if (matrix.size != MATRIX_SIZE) {
            return BackendResult.Failed("Matrix must contain $MATRIX_SIZE floats")
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TCL_NXTVISION_INTERFACE)
            data.writeInt(matrix.size)
            matrix.forEach(data::writeFloat)
            val transacted = transact(TRANSACTION_SET_SF_CLIENT_MATRIX, data, reply, 0)
            if (!transacted) {
                BackendResult.Failed("Binder transaction returned false")
            } else {
                reply.readException()
                BackendResult.Success
            }
        } catch (error: Throwable) {
            BackendResult.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun canWriteSecureSettings(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun canWriteSystemSettings(): Boolean =
        Settings.System.canWrite(context)

    private fun readActivationState(): ActivationState =
        runCatching {
            when (Settings.Secure.getString(context.contentResolver, TCL_COLOR_TEMPERATURE_ACTIVATED)) {
                "1" -> ActivationState.Active
                "0" -> ActivationState.Inactive
                else -> ActivationState.Unknown
            }
        }.getOrDefault(ActivationState.Unknown)

    private fun putSecureInt(name: String, value: Int): BackendResult =
        runCatching {
            Settings.Secure.putInt(context.contentResolver, name, value)
            BackendResult.Success
        }.getOrElse { error ->
            BackendResult.Failed(error.message ?: error.javaClass.simpleName)
        }

    private fun restoreSecureInt(name: String, value: Int?) {
        runCatching {
            if (value == null) {
                Settings.Secure.putString(context.contentResolver, name, null)
            } else {
                Settings.Secure.putInt(context.contentResolver, name, value)
            }
        }
    }

    private fun putSystemInt(name: String, value: Int): BackendResult =
        runCatching {
            Settings.System.putInt(context.contentResolver, name, value)
            BackendResult.Success
        }.getOrElse { error ->
            BackendResult.Failed(error.message ?: error.javaClass.simpleName)
        }

    private fun getSecureString(name: String): String? =
        runCatching {
            Settings.Secure.getString(context.contentResolver, name)
        }.getOrNull()

    private fun getSecureInt(name: String): Int? =
        getSecureString(name)?.toIntOrNull()

    private fun getSystemString(name: String): String? =
        runCatching {
            Settings.System.getString(context.contentResolver, name)
        }.getOrNull()

    private fun getSystemInt(name: String): Int? =
        getSystemString(name)?.toIntOrNull()

    private companion object {
        const val TCL_NXTVISION_SERVICE = "tct_nxtvision"
        const val TCL_NXTVISION_INTERFACE = "tct.nxtvision.ITctComponentNxtvisionManager"
        const val TRANSACTION_SET_SF_CLIENT_MATRIX = 12
        const val MATRIX_SIZE = 16
        const val MIN_BRIGHTNESS = 0
        const val MIN_WRITABLE_BRIGHTNESS = 8
        const val MAX_BRIGHTNESS = 255
        const val ACCESSIBILITY_DISPLAY_INVERSION_ENABLED = "accessibility_display_inversion_enabled"
        const val TCL_COLOR_TEMPERATURE_ACTIVATED = "tct_color_temperature_activated"
        const val TCL_COLOR_TEMPERATURE_MATRIX = "tct_color_temperature_matrix"
        const val EYEPROTECT_STATUS = "eyeprotect_status"
        const val EYEPROTECT_KIND = "eyeprotect_kind"
        const val EYEPROTECT_CLASSIC_MODE = "eyeprotect_classic_mode"
        const val EYEPROTECT_PERSONALIZED_SET = "eyeprotect_persionalize_set"
        const val COLOR_MODE_VALUE = "color_mode_value"
        const val ADVANCED_COLOR_MODE_VALUE = "adv_color_mode_value"
        val BRIGHTNESS_RANGE = 0f..1f
    }
}

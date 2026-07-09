package com.jeff.tclcolorcontrol.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import com.jeff.tclcolorcontrol.color.ColorProfile
import com.jeff.tclcolorcontrol.color.ColorProfiles

class AndroidColorBackend(
    private val context: Context,
) : ColorBackend {
    override fun getCapabilities(): BackendCapabilities =
        BackendCapabilities(
            binderAvailable = findTclBinder() != null,
            canWriteSecureSettings = canWriteSecureSettings(),
            activationState = readActivationState(),
            modeSnapshot = readModeSnapshot(),
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

    override fun apply(profile: ColorProfile): BackendResult {
        val binder = findTclBinder() ?: return BackendResult.BinderUnavailable
        val matrixResult = binder.callSetSurfaceFlingerMatrix(profile.toMatrix())
        if (matrixResult !is BackendResult.Success) return matrixResult

        return if (canWriteSecureSettings()) {
            putSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, 1)
        } else {
            BackendResult.PermissionMissing
        }
    }

    override fun restoreBaseline(): BackendResult {
        val binder = findTclBinder() ?: return BackendResult.BinderUnavailable
        val matrixResult = binder.callSetSurfaceFlingerMatrix(ColorProfiles.Baseline.toMatrix())
        if (matrixResult !is BackendResult.Success) return matrixResult

        return if (canWriteSecureSettings()) {
            putSecureInt(TCL_COLOR_TEMPERATURE_ACTIVATED, 0)
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
        const val TCL_COLOR_TEMPERATURE_ACTIVATED = "tct_color_temperature_activated"
        const val TCL_COLOR_TEMPERATURE_MATRIX = "tct_color_temperature_matrix"
        const val EYEPROTECT_STATUS = "eyeprotect_status"
        const val EYEPROTECT_KIND = "eyeprotect_kind"
        const val EYEPROTECT_CLASSIC_MODE = "eyeprotect_classic_mode"
        const val EYEPROTECT_PERSONALIZED_SET = "eyeprotect_persionalize_set"
        const val COLOR_MODE_VALUE = "color_mode_value"
        const val ADVANCED_COLOR_MODE_VALUE = "adv_color_mode_value"
    }
}

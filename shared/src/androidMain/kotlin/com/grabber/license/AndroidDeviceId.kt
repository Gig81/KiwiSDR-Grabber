package com.grabber.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.grabber.app.AppLogger

/** ID dispositivo per la licenza Android: `GRAB-A-XXXX-XXXX-XXXX-XXXX` da `ANDROID_ID`. */
object AndroidDeviceId {
    private const val TAG = "AndroidDeviceId"

    // ANDROID_ID: legato ad app, utente e chiave di firma; nessun permesso, non pubblicitario.
    @SuppressLint("HardwareIds")
    fun read(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: SecurityException) {
            AppLogger.error(TAG, "ANDROID_ID non leggibile", e)
            null
        }
        return DeviceIds.androidMachineIdFrom(androidId)
    }
}

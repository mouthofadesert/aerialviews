package com.neilturner.aerialviews.utils

import android.os.Build
import java.util.Locale

object DeviceHelper {

    // Based on
    // https://stackoverflow.com/a/27836910/247257

    fun androidVersion(): String {
        return "v${Build.VERSION.RELEASE}"
    }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.lowercase(Locale.getDefault())
            .startsWith(manufacturer.lowercase(Locale.getDefault()))
        ) {
            capitalize(model)
        } else {
            capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String): String {
        if (s.isEmpty()) {
            return ""
        }
        val first = s[0]
        return if (Character.isUpperCase(first)) {
            s
        } else {
            first.uppercaseChar().toString() + s.substring(1)
        }
    }

    fun isFireTV(): Boolean = deviceName().contains("AFT", true)

    fun isNvidaShield(): Boolean = deviceName().contains("NVIDIA", true)

    fun isChromecastWithGoogleTV(): Boolean = deviceName().contains("Google Chromecast", true)
}
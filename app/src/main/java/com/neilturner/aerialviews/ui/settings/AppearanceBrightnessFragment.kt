package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class AppearanceBrightnessFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_brightness, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Brightness", this)
    }
}
package com.taqijafri.wotdsolver.ui

import android.app.Activity
import android.os.Bundle
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.data.SettingsStore

/** Applies the user's theme choice before anything is inflated. */
open class BaseActivity : Activity() {

    protected lateinit var settings: SettingsStore
    private var appliedDark = true

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = SettingsStore(this)
        appliedDark = settings.darkMode
        setTheme(
            if (appliedDark) R.style.Theme_WotdSolver_Dark
            else R.style.Theme_WotdSolver_Light
        )
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // theme may have been changed in Settings while we were paused
        if (::settings.isInitialized && settings.darkMode != appliedDark) recreate()
    }
}

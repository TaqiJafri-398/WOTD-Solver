package com.taqijafri.wotdsolver.data

import android.content.Context

/** Simple SharedPreferences-backed settings. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("wotd_settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(v) { prefs.edit().putBoolean("dark_mode", v).apply() }

    var autoAnalyze: Boolean
        get() = prefs.getBoolean("auto_analyze", true)
        set(v) { prefs.edit().putBoolean("auto_analyze", v).apply() }

    var historyEnabled: Boolean
        get() = prefs.getBoolean("history_enabled", true)
        set(v) { prefs.edit().putBoolean("history_enabled", v).apply() }
}

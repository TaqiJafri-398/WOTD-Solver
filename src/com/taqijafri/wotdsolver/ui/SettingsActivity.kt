package com.taqijafri.wotdsolver.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.data.HistoryStore

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rbDark = findViewById<RadioButton>(R.id.rbDark)
        val rbLight = findViewById<RadioButton>(R.id.rbLight)
        if (settings.darkMode) rbDark.isChecked = true else rbLight.isChecked = true

        findViewById<RadioGroup>(R.id.themeGroup).setOnCheckedChangeListener { _, id ->
            settings.darkMode = (id == R.id.rbDark)
            recreate()
        }

        val cbAuto = findViewById<CheckBox>(R.id.cbAutoAnalyze)
        cbAuto.isChecked = settings.autoAnalyze
        cbAuto.setOnCheckedChangeListener { _, v -> settings.autoAnalyze = v }

        val cbHist = findViewById<CheckBox>(R.id.cbHistory)
        cbHist.isChecked = settings.historyEnabled
        cbHist.setOnCheckedChangeListener { _, v -> settings.historyEnabled = v }

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            HistoryStore(this).clear()
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnManualInput).setOnClickListener {
            startActivity(Intent(this, ManualInputActivity::class.java))
        }
        findViewById<Button>(R.id.btnAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }
}

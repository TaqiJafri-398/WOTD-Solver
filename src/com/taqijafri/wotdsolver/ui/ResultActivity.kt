package com.taqijafri.wotdsolver.ui

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.wotdsolver.ImageHolder
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.ScreenshotAnalyzer
import com.taqijafri.wotdsolver.data.WordDatabase
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer
import com.taqijafri.wotdsolver.solver.SolverCore

class ResultActivity : BaseActivity() {

    private data class ViewModel(
        val result: PuzzleAnalyzer.AnalysisResult,
        val totalCandidates: Int,
        val fromHistory: Boolean
    )

    private lateinit var tvWordLength: TextView
    private lateinit var guessesContainer: LinearLayout
    private lateinit var tvUncertainHint: TextView
    private lateinit var tvWarnings: TextView
    private lateinit var tvCandidatesTitle: TextView
    private lateinit var tvCandidatesNote: TextView
    private lateinit var candidatesContainer: LinearLayout
    private lateinit var tvBestTitle: TextView
    private lateinit var tvBestGuess: TextView
    private lateinit var tvBestReason: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvWordLength = findViewById(R.id.tvWordLength)
        guessesContainer = findViewById(R.id.guessesContainer)
        tvUncertainHint = findViewById(R.id.tvUncertainHint)
        tvWarnings = findViewById(R.id.tvWarnings)
        tvCandidatesTitle = findViewById(R.id.tvCandidatesTitle)
        tvCandidatesNote = findViewById(R.id.tvCandidatesNote)
        candidatesContainer = findViewById(R.id.candidatesContainer)
        tvBestTitle = findViewById(R.id.tvBestTitle)
        tvBestGuess = findViewById(R.id.tvBestGuess)
        tvBestReason = findViewById(R.id.tvBestReason)

        findViewById<Button>(R.id.btnAnalyzeAgain).setOnClickListener { reanalyze() }
        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            startActivity(Intent(this, EditDetectionActivity::class.java))
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            startActivity(Intent(this, ManualInputActivity::class.java))
        }
        findViewById<Button>(R.id.btnNew).setOnClickListener {
            ImageHolder.clearImage()
            ImageHolder.analysis = null
            ImageHolder.historyId = null
            ImageHolder.historyCandidateCount = 0
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun currentModel(): ViewModel? {
        val a = ImageHolder.analysis ?: return null
        val total = if (ImageHolder.historyId != null) ImageHolder.historyCandidateCount
        else a.candidates.size
        return ViewModel(a, total, fromHistory = ImageHolder.historyId != null)
    }

    private fun render() {
        val vm = currentModel()
        if (vm == null) {
            finish()
            return
        }
        val r = vm.result

        tvWordLength.text = getString(R.string.detected_word_length, r.wordLength)

        guessesContainer.removeAllViews()
        var anyUncertain = false
        for (g in r.guesses) {
            guessesContainer.addView(
                TileViews.makeTileRow(this, g.word, g.states, g.uncertain)
            )
            val pattern = TextView(this).apply {
                text = SolverCore.patternEmoji(g.states)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, 2, 0, 10)
            }
            guessesContainer.addView(pattern)
            if (g.uncertain.any { it }) anyUncertain = true
        }
        tvUncertainHint.visibility = if (anyUncertain) View.VISIBLE else View.GONE

        if (r.warnings.isNotEmpty()) {
            tvWarnings.visibility = View.VISIBLE
            tvWarnings.text = r.warnings.joinToString("\n\n")
        } else {
            tvWarnings.visibility = View.GONE
        }

        val total = vm.totalCandidates
        if (total == 1) {
            tvCandidatesTitle.text = getString(R.string.confirmed_answer)
            tvCandidatesNote.text = getString(R.string.confirmed_note)
        } else {
            tvCandidatesTitle.text = getString(R.string.possible_answers, total)
            tvCandidatesNote.text = getString(R.string.possible_answer_note)
        }
        candidatesContainer.removeAllViews()
        val shown = r.candidates.take(80)
        shown.forEachIndexed { i, w ->
            candidatesContainer.addView(TileViews.candidateRow(this, i + 1, w, showDivider = i > 0))
        }
        if (total > shown.size) {
            val more = TextView(this).apply {
                text = getString(R.string.showing_top, shown.size, total)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(8, 8, 8, 8)
            }
            candidatesContainer.addView(more)
        }
        if (total == 0) {
            candidatesContainer.addView(TextView(this).apply {
                text = getString(R.string.no_candidates)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
        }

        if (r.bestGuessWord.isNotEmpty()) {
            findViewById<LinearLayout>(R.id.bestCard).visibility = View.VISIBLE
            tvBestTitle.visibility = View.VISIBLE
            tvBestGuess.visibility = View.VISIBLE
            tvBestReason.visibility = View.VISIBLE
            tvBestGuess.text = r.bestGuessWord
            tvBestReason.text = r.bestGuessReason
        } else {
            findViewById<LinearLayout>(R.id.bestCard).visibility = View.GONE
            tvBestTitle.visibility = View.GONE
            tvBestGuess.visibility = View.GONE
            tvBestReason.visibility = View.GONE
        }
    }

    private fun reanalyze() {
        val bmp = ImageHolder.bitmap
        if (bmp == null || bmp.isRecycled) {
            Toast.makeText(this, R.string.select_first, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.analyzing, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val db = WordDatabase(resources, packageName)
                val result = ScreenshotAnalyzer.analyze(bmp, db)
                runOnUiThread {
                    ImageHolder.analysis = result
                    ImageHolder.historyId = null
                    ImageHolder.historyCandidateCount = 0
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.analysis_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

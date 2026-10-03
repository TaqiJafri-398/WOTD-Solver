package com.taqijafri.wotdsolver.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.taqijafri.wotdsolver.ImageHolder
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.data.WordDatabase
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer
import com.taqijafri.wotdsolver.solver.SolverCore

/**
 * Lets the user fix automatic detection mistakes: tap a tile to cycle its
 * color, long-press to change its letter, then re-solve.
 */
class EditDetectionActivity : BaseActivity() {

    private class TileEdit(var letter: Char, var state: Int, val uncertain: Boolean)

    private val rows = ArrayList<ArrayList<TileEdit>>()
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analysis = ImageHolder.analysis
        if (analysis == null || analysis.guesses.isEmpty()) {
            finish()
            return
        }
        setContentView(R.layout.activity_edit)
        container = findViewById(R.id.editGridContainer)

        analysis.guesses.forEach { g ->
            val row = ArrayList<TileEdit>()
            g.word.forEachIndexed { i, ch ->
                row.add(TileEdit(ch, g.states[i], g.uncertain.getOrElse(i) { false }))
            }
            rows.add(row)
        }
        buildGrid()

        findViewById<Button>(R.id.btnSaveResolve).setOnClickListener { saveAndResolve() }
    }

    private fun buildGrid() {
        container.removeAllViews()
        for (row in rows) {
            val ll = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (tile in row) {
                val b = Button(this)
                styleTile(b, tile)
                b.setOnClickListener {
                    tile.state = (tile.state + 1) % 3
                    styleTile(b, tile)
                }
                b.setOnLongClickListener {
                    showLetterPicker(tile) { styleTile(b, tile) }
                    true
                }
                ll.addView(b)
            }
            container.addView(ll)
        }
    }

    private fun styleTile(b: Button, tile: TileEdit) {
        val size = TileViews.dp(this, 46)
        val m = TileViews.dp(this, 3)
        b.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(m, m, m, m) }
        b.background = TileViews.tileBackground(this, TileViews.stateColor(tile.state))
        b.text = when {
            tile.letter == '?' -> "?"
            tile.uncertain -> "${tile.letter}?"
            else -> tile.letter.toString()
        }
        b.setTextColor(0xFFFFFFFF.toInt())
        b.textSize = if (tile.uncertain) 14f else 20f
    }

    private fun showLetterPicker(tile: TileEdit, onDone: () -> Unit) {
        val items = ('A'..'Z').map { it.toString() }.toMutableList()
        items.add(getString(R.string.clear_letter))
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_letter)
            .setItems(items.toTypedArray()) { _, which ->
                tile.letter = if (which < 26) ('A' + which) else '?'
                onDone()
            }
            .show()
    }

    private fun saveAndResolve() {
        val analysis = ImageHolder.analysis ?: run { finish(); return }
        val newGuesses = rows.map { row ->
            PuzzleAnalyzer.DetectedGuess(
                row.joinToString("") { it.letter.toString() },
                row.map { it.state },
                row.map { it.uncertain }
            )
        }
        val db = WordDatabase(resources, packageName)
        val solverGuesses = newGuesses.mapNotNull { g ->
            if (g.word.all { it in 'A'..'Z' }) SolverCore.Guess(g.word, g.states) else null
        }
        val candidates = SolverCore.solve(db.words(analysis.wordLength), solverGuesses)
        val (bg, reason) = SolverCore.bestNextGuess(candidates)
        val warnings = ArrayList<String>()
        if (solverGuesses.size != newGuesses.size) {
            warnings.add("Some tiles still have no letter (?) and were skipped.")
        }
        if (candidates.isEmpty() && solverGuesses.isNotEmpty()) {
            warnings.add(getString(R.string.no_candidates))
        }
        ImageHolder.analysis = analysis.copy(
            guesses = newGuesses,
            warnings = warnings,
            candidates = candidates,
            bestGuessWord = bg,
            bestGuessReason = reason
        )
        Toast.makeText(this, R.string.detection_updated, Toast.LENGTH_SHORT).show()
        finish()
    }
}

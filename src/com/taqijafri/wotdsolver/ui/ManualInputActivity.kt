package com.taqijafri.wotdsolver.ui

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.data.WordDatabase
import com.taqijafri.wotdsolver.solver.SolverCore
import com.taqijafri.wotdsolver.vision.VisionCore

/**
 * Manual fallback: the user types guesses and taps each letter's color box to
 * cycle gray → yellow → green. Uses the same [SolverCore] as auto analysis.
 */
class ManualInputActivity : BaseActivity() {

    private var wordLength = 5
    private lateinit var db: WordDatabase
    private lateinit var lengthRow: LinearLayout
    private lateinit var guessesContainer: LinearLayout
    private lateinit var tvManualCandidatesTitle: TextView
    private lateinit var manualCandidatesContainer: LinearLayout
    private lateinit var tvManualBest: TextView

    /** Parallel to guessesContainer children: per-row letter states. */
    private val rowStates = ArrayList<IntArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual)
        db = WordDatabase(resources, packageName)

        lengthRow = findViewById(R.id.lengthRow)
        guessesContainer = findViewById(R.id.guessesContainer)
        tvManualCandidatesTitle = findViewById(R.id.tvManualCandidatesTitle)
        manualCandidatesContainer = findViewById(R.id.manualCandidatesContainer)
        tvManualBest = findViewById(R.id.tvManualBest)

        buildLengthRow()
        findViewById<Button>(R.id.btnAddGuess).setOnClickListener { addGuessRow() }
        findViewById<Button>(R.id.btnSolve).setOnClickListener { solve() }
        findViewById<Button>(R.id.btnClearManual).setOnClickListener {
            guessesContainer.removeAllViews()
            rowStates.clear()
            clearResults()
        }
        addGuessRow()
    }

    // ---------------- word length ----------------

    private fun buildLengthRow() {
        lengthRow.removeAllViews()
        for (n in 3..8) {
            val b = Button(this)
            b.text = n.toString()
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(TileViews.dp(this, 4), 0, TileViews.dp(this, 4), 0)
            b.layoutParams = lp
            b.background = resources.getDrawable(
                if (n == wordLength) R.drawable.btn_primary else R.drawable.btn_secondary, theme
            )
            b.setTextColor(
                if (n == wordLength) 0xFF1E2329.toInt() else 0xFFFFFFFF.toInt()
            )
            b.setOnClickListener {
                if (wordLength != n) {
                    wordLength = n
                    buildLengthRow()
                    guessesContainer.removeAllViews()
                    rowStates.clear()
                    clearResults()
                    addGuessRow()
                }
            }
            lengthRow.addView(b)
        }
    }

    // ---------------- guess rows ----------------

    private fun addGuessRow() {
        val states = IntArray(wordLength) { VisionCore.GRAY }
        rowStates.add(states)

        val rowLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, TileViews.dp(this@ManualInputActivity, 6), 0, 0)
        }

        val et = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint = getString(R.string.enter_letters, wordLength)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(wordLength))
            textSize = 18f
        }
        rowLayout.addView(et)

        for (i in 0 until wordLength) {
            val b = Button(this)
            val lp = LinearLayout.LayoutParams(TileViews.dp(this, 42), TileViews.dp(this, 50))
            lp.setMargins(TileViews.dp(this, 2), 0, TileViews.dp(this, 2), 0)
            b.layoutParams = lp
            paintStateButton(b, states[i], null)
            b.setOnClickListener {
                states[i] = (states[i] + 1) % 3
                paintStateButton(b, states[i], et.text.toString().uppercase().getOrNull(i))
            }
            rowLayout.addView(b)
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val word = s.toString().uppercase()
                for (i in 0 until wordLength) {
                    val b = rowLayout.getChildAt(i + 1) as Button
                    paintStateButton(b, states[i], word.getOrNull(i))
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val del = Button(this).apply {
            text = "×"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                TileViews.dp(this@ManualInputActivity, 44),
                TileViews.dp(this@ManualInputActivity, 50)
            )
            background = resources.getDrawable(R.drawable.btn_secondary, theme)
            setTextColor(0xFFFFFFFF.toInt())
        }
        del.setOnClickListener {
            guessesContainer.removeView(rowLayout)
            rowStates.remove(states)
        }
        rowLayout.addView(del)

        guessesContainer.addView(rowLayout)
    }

    private fun paintStateButton(b: Button, state: Int, letter: Char?) {
        b.background = TileViews.tileBackground(this, TileViews.stateColor(state))
        b.text = letter?.toString() ?: "·"
        b.setTextColor(0xFFFFFFFF.toInt())
        b.textSize = 16f
    }

    // ---------------- solving ----------------

    private fun solve() {
        val guesses = ArrayList<SolverCore.Guess>()
        for (idx in 0 until guessesContainer.childCount) {
            val rowLayout = guessesContainer.getChildAt(idx) as LinearLayout
            val et = rowLayout.getChildAt(0) as EditText
            val word = et.text.toString().trim().uppercase()
            if (word.isEmpty()) continue
            if (word.length != wordLength || !word.all { it in 'A'..'Z' }) {
                Toast.makeText(this, getString(R.string.invalid_guess, wordLength), Toast.LENGTH_SHORT).show()
                return
            }
            guesses.add(SolverCore.Guess(word, rowStates[idx].toList()))
        }
        if (guesses.isEmpty()) {
            Toast.makeText(this, R.string.no_guesses, Toast.LENGTH_SHORT).show()
            return
        }
        val candidates = SolverCore.solve(db.words(wordLength), guesses)
        renderResults(candidates)
    }

    private fun clearResults() {
        findViewById<LinearLayout>(R.id.manualResultsCard).visibility = View.GONE
        tvManualCandidatesTitle.visibility = View.GONE
        manualCandidatesContainer.removeAllViews()
        tvManualBest.visibility = View.GONE
    }

    private fun renderResults(candidates: List<String>) {
        clearResults()
        findViewById<LinearLayout>(R.id.manualResultsCard).visibility = View.VISIBLE
        tvManualCandidatesTitle.visibility = View.VISIBLE
        if (candidates.size == 1) {
            tvManualCandidatesTitle.text = getString(R.string.confirmed_answer)
        } else {
            tvManualCandidatesTitle.text = getString(R.string.possible_answers, candidates.size)
        }
        val shown = candidates.take(80)
        shown.forEachIndexed { i, w ->
            manualCandidatesContainer.addView(TileViews.candidateRow(this, i + 1, w, showDivider = i > 0))
        }
        if (candidates.size > shown.size) {
            manualCandidatesContainer.addView(TextView(this).apply {
                text = getString(R.string.showing_top, shown.size, candidates.size)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(8, 8, 8, 8)
            })
        }
        if (candidates.isEmpty()) {
            manualCandidatesContainer.addView(TextView(this).apply {
                text = getString(R.string.no_candidates)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
        } else {
            val (bg, reason) = SolverCore.bestNextGuess(candidates)
            tvManualBest.visibility = View.VISIBLE
            tvManualBest.text = getString(R.string.best_next_guess) + ": $bg\n$reason"
        }
    }
}

package com.taqijafri.wotdsolver.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.taqijafri.wotdsolver.ImageHolder
import com.taqijafri.wotdsolver.R
import com.taqijafri.wotdsolver.data.HistoryEntry
import com.taqijafri.wotdsolver.data.HistoryStore
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer

class HistoryActivity : BaseActivity() {

    private lateinit var store: HistoryStore
    private lateinit var adapter: HistoryAdapter
    private var entries: List<HistoryEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        store = HistoryStore(this)

        val listView = findViewById<ListView>(R.id.historyList)
        adapter = HistoryAdapter()
        listView.adapter = adapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, pos, _ ->
                val e = entries[pos]
                // Rebuild a view-model from the snapshot so Result/Edit screens
                // work exactly like a fresh analysis.
                ImageHolder.analysis = PuzzleAnalyzer.AnalysisResult(
                    wordLength = e.wordLength,
                    guesses = e.guesses,
                    warnings = emptyList(),
                    candidates = e.topCandidates,
                    bestGuessWord = e.bestGuessWord,
                    bestGuessReason = e.bestGuessReason
                )
                ImageHolder.historyId = e.id
                ImageHolder.historyCandidateCount = e.candidateCount
                startActivity(Intent(this, ResultActivity::class.java))
            }

        findViewById<Button>(R.id.btnClearAllHistory).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setMessage(R.string.confirm_clear_history)
                .setPositiveButton(R.string.yes) { _, _ ->
                    store.clear()
                    reload()
                    Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        entries = if (settings.historyEnabled) store.load() else emptyList()
        findViewById<TextView>(R.id.tvHistoryEmpty).apply {
            visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            text = getString(
                if (settings.historyEnabled) R.string.history_empty
                else R.string.history_disabled
            )
        }
        adapter.notifyDataSetChanged()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size
        override fun getItem(pos: Int): HistoryEntry = entries[pos]
        override fun getItemId(pos: Int): Long = entries[pos].id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val e = entries[pos]
            val row = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(R.drawable.card, theme)
                setPadding(
                    TileViews.dp(context, 14),
                    TileViews.dp(context, 12),
                    TileViews.dp(context, 14),
                    TileViews.dp(context, 12)
                )
            }
            val texts = LinearLayout(this@HistoryActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val title = TextView(this@HistoryActivity).apply {
                text = e.date
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
            }
            val sub = TextView(this@HistoryActivity).apply {
                text = getString(
                    R.string.history_item_sub,
                    e.wordLength, e.guesses.size, e.candidateCount
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            }
            val top = TextView(this@HistoryActivity).apply {
                text = e.topCandidates.take(5).joinToString(" ")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            texts.addView(title)
            texts.addView(sub)
            texts.addView(top)

            val del = Button(this@HistoryActivity).apply {
                text = getString(R.string.delete)
                background = TileViews.tileBackground(this@HistoryActivity, 0xFF6E2A33.toInt())
                setTextColor(0xFFFFFFFF.toInt())
            }
            del.setOnClickListener {
                store.delete(e.id)
                reload()
            }
            row.addView(texts)
            row.addView(del)
            return row
        }
    }
}

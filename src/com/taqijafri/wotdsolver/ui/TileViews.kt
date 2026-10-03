package com.taqijafri.wotdsolver.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.taqijafri.wotdsolver.vision.VisionCore

/** Small builders for the Wordle-style tile UI used across screens. */
object TileViews {

    fun dp(context: Context, v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    fun stateColor(state: Int): Int = when (state) {
        VisionCore.GREEN -> 0xFF2EBD85.toInt()
        VisionCore.YELLOW -> 0xFFF0B90B.toInt()
        else -> 0xFF474D57.toInt()
    }

    fun cardColor(dark: Boolean): Int =
        if (dark) 0xFF1E2329.toInt() else 0xFFF1F3F5.toInt()

    fun rounded(color: Int, context: Context, cornerDp: Int = 12): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(color)
        d.cornerRadius = dp(context, cornerDp).toFloat()
        return d
    }

    fun tileBackground(context: Context, color: Int): GradientDrawable =
        rounded(color, context, 8)

    /**
     * A centered row of letter tiles. Letters flagged [uncertain] get a "?"
     * suffix so low-confidence OCR is visible at a glance.
     */
    fun makeTileRow(
        context: Context,
        word: String,
        states: List<Int>,
        uncertain: List<Boolean>? = null,
        tileDp: Int = 46,
        textSp: Int = 20
    ): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val size = dp(context, tileDp)
        val margin = dp(context, 3)
        word.forEachIndexed { i, ch ->
            val tv = TextView(context)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, margin, margin, margin)
            tv.layoutParams = lp
            tv.gravity = Gravity.CENTER
            val isUncertain = uncertain?.getOrNull(i) == true
            tv.text = when {
                ch == '?' -> "?"
                isUncertain -> "$ch?"
                else -> ch.toString()
            }
            tv.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                if (isUncertain) (textSp - 6).toFloat() else textSp.toFloat()
            )
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.background = tileBackground(context, stateColor(states.getOrElse(i) { VisionCore.GRAY }))
            row.addView(tv)
        }
        return row
    }

    /** Section label used for candidate lists etc. */
    fun label(context: Context, text: String, sizeSp: Int = 18): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
            setPadding(0, dp(context, 10), 0, dp(context, 2))
        }

    /** Thin 1dp divider for lists. */
    fun divider(context: Context): View {
        val v = View(context)
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
        )
        v.setBackgroundColor(0x1A888888)
        return v
    }

    /**
     * One candidate row: gold rank number + word in letterspaced caps.
     * Set [showDivider] to add a divider above the row (skip for the first).
     */
    fun candidateRow(
        context: Context, rank: Int, word: String, showDivider: Boolean
    ): LinearLayout {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (showDivider) outer.addView(divider(context))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 9), dp(context, 4), dp(context, 9))
        }
        val rankTv = TextView(context).apply {
            text = rank.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFF0B90B.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(context, 36), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val wordTv = TextView(context).apply {
            text = word
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
        }
        row.addView(rankTv)
        row.addView(wordTv)
        outer.addView(row)
        return outer
    }

    /** Resolve a theme color attribute (e.g. R.attr.cardBackground) to a color int. */
    fun resolveAttrColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}

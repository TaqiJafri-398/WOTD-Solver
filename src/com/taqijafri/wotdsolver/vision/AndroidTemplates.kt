package com.taqijafri.wotdsolver.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders A–Z letter templates with Android's built-in fonts and normalizes
 * them with [VisionCore.extractInk48], exactly like tiles are normalized, so
 * template matching compares like with like. Two weights (bold + regular)
 * cover the font-weight range seen in WOTD screenshots.
 */
object AndroidTemplates {

    @Volatile
    private var cached: Map<Char, List<FloatArray>>? = null

    fun get(): Map<Char, List<FloatArray>> {
        cached?.let { return it }
        val map = LinkedHashMap<Char, List<FloatArray>>()
        for (c in 'A'..'Z') {
            val variants = ArrayList<FloatArray>()
            for (tf in arrayOf(Typeface.DEFAULT_BOLD, Typeface.DEFAULT)) {
                val gray = renderGlyph(c, tf)
                VisionCore.extractInk48(gray, 128, 128, 0.02f)?.let { variants.add(it) }
            }
            if (variants.isNotEmpty()) map[c] = variants
        }
        cached = map
        return map
    }

    private fun renderGlyph(c: Char, tf: Typeface): FloatArray {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.typeface = tf
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val x = size / 2f
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(c.toString(), x, y, paint)
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        bmp.recycle()
        return VisionCore.luminance(px)
    }
}

package com.taqijafri.wotdsolver

import android.graphics.Bitmap
import com.taqijafri.wotdsolver.data.WordDatabase
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer
import com.taqijafri.wotdsolver.vision.AndroidTemplates

/**
 * Android entry point for screenshot analysis: decodes a [Bitmap] to raw
 * pixels and delegates to the platform-agnostic [PuzzleAnalyzer].
 * Everything runs on-device; nothing is uploaded anywhere.
 */
object ScreenshotAnalyzer {

    /** Analysis input is downscaled to this max dimension for speed. */
    const val MAX_DIM = 1100

    fun analyze(bitmap: Bitmap, db: WordDatabase): PuzzleAnalyzer.AnalysisResult {
        val scaled = scaleDown(bitmap, MAX_DIM)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        return PuzzleAnalyzer.analyze(pixels, w, h, AndroidTemplates.get()) { n -> db.words(n) }
    }

    fun scaleDown(src: Bitmap, maxDim: Int): Bitmap {
        val m = maxOf(src.width, src.height)
        if (m <= maxDim) return src
        val s = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true)
    }
}

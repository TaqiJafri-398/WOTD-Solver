package com.taqijafri.wotdsolver

import android.graphics.Bitmap
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer

/**
 * In-process holder for the currently selected screenshot and its analysis,
 * shared between activities without serializing large bitmaps.
 */
object ImageHolder {
    var bitmap: Bitmap? = null
    var analysis: PuzzleAnalyzer.AnalysisResult? = null
    var historyId: Long? = null
    /** Total candidate count for history entries (only the top 100 are stored). */
    var historyCandidateCount: Int = 0

    fun clearImage() {
        try {
            bitmap?.recycle()
        } catch (_: Exception) {
        }
        bitmap = null
    }
}

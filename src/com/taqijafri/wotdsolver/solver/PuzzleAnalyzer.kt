package com.taqijafri.wotdsolver.solver

import com.taqijafri.wotdsolver.vision.VisionCore

/**
 * Platform-agnostic orchestration: screenshot pixels -> detected guesses ->
 * solved candidates. The platform layer only supplies raw pixels, rendered
 * letter templates and the word list for the detected length.
 */
object PuzzleAnalyzer {

    /** One detected guess row. [uncertain] flags letters the OCR was unsure about. */
    data class DetectedGuess(
        val word: String,
        val states: List<Int>,
        val uncertain: List<Boolean>
    )

    data class AnalysisResult(
        val wordLength: Int,
        val guesses: List<DetectedGuess>,
        val warnings: List<String>,
        val candidates: List<String>,
        val bestGuessWord: String,
        val bestGuessReason: String
    )

    /** Letters with a match margin below this are flagged for manual review. */
    const val UNCERTAIN_MARGIN = 0.025
    const val UNCERTAIN_SCORE = 0.65

    fun analyze(
        pixels: IntArray,
        w: Int,
        h: Int,
        templates: Map<Char, List<FloatArray>>,
        dictForLength: (Int) -> List<String>
    ): AnalysisResult {
        val warnings = ArrayList<String>()
        val grid = VisionCore.detectGrid(pixels, w, h)
            ?: return AnalysisResult(
                0, emptyList(),
                listOf("Unable to confidently detect the puzzle. Please crop the puzzle area or use Manual Mode."),
                emptyList(), "", ""
            )
        val wordLength = grid.wordLength

        val guesses = ArrayList<DetectedGuess>()
        grid.rows.forEachIndexed { ri, row ->
            val letters = CharArray(row.size) { ' ' }
            val states = IntArray(row.size)
            val uncertain = BooleanArray(row.size)
            var filled = 0
            row.forEachIndexed { ci, tile ->
                states[ci] = VisionCore.tileState(pixels, w, tile)
                val ink = VisionCore.tileLetterImage(pixels, w, tile)
                if (ink != null) {
                    val m = VisionCore.matchLetter(ink, templates)
                    if (m != null) {
                        letters[ci] = m.ch
                        filled++
                        uncertain[ci] = m.margin < UNCERTAIN_MARGIN || m.score < UNCERTAIN_SCORE
                    } else {
                        letters[ci] = '?'
                        filled++
                        uncertain[ci] = true
                    }
                }
            }
            when {
                filled == row.size -> guesses.add(
                    DetectedGuess(
                        String(letters),
                        states.toList(),
                        uncertain.toList()
                    )
                )
                filled > 0 -> warnings.add(
                    "Row ${ri + 1} looks partially filled and was skipped. " +
                            "If it is a real guess, enter it via Manual Mode."
                )
                // fully empty rows are simply not guesses; no warning needed
            }
        }

        if (guesses.isEmpty()) {
            warnings.add(
                "No completed guesses found in the puzzle. " +
                        "If the screenshot is correct, try Manual Mode."
            )
        }
        if (grid.rowCount == 1 && guesses.isNotEmpty()) {
            warnings.add(
                "Only one tile row was detected. If the puzzle uses more letters, " +
                        "please use Manual Mode."
            )
        }

        val dict = if (wordLength in 3..8) dictForLength(wordLength) else emptyList()
        if (wordLength !in 3..8) {
            warnings.add(
                "Detected word length ($wordLength) is outside the supported 3–8 range. " +
                        "The grid may be misdetected — try cropping the puzzle area or use Manual Mode."
            )
        }
        val solverGuesses = guesses.mapNotNull { g ->
            if (g.word.all { it in 'A'..'Z' }) SolverCore.Guess(g.word, g.states) else null
        }
        if (solverGuesses.size != guesses.size) {
            warnings.add("Some letters could not be recognized confidently — review them before trusting the results.")
        }
        val candidates = SolverCore.solve(dict, solverGuesses)
        if (guesses.isNotEmpty() && candidates.isEmpty()) {
            warnings.add(
                "No dictionary words match all guesses. A letter or tile color " +
                        "may be misdetected — use Edit Detection to correct it."
            )
        }
        val (bgWord, bgReason) = SolverCore.bestNextGuess(candidates)

        return AnalysisResult(
            wordLength, guesses, warnings, candidates, bgWord, bgReason
        )
    }
}

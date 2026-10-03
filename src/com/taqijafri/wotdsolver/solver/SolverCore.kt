package com.taqijafri.wotdsolver.solver

/**
 * Wordle-style constraint engine for the Binance Word of the Day puzzle.
 *
 * Correctness strategy: instead of extracting fragile per-letter min/max
 * rules (easy to get wrong with duplicate letters), every candidate answer is
 * checked by *simulating* the true coloring function [patternFor] for each
 * guess and comparing it to the observed pattern. This is exactly correct for
 * all duplicate-letter cases by construction, and plenty fast for the
 * dictionary sizes involved (tens of thousands of words x a few guesses).
 */
object SolverCore {

    const val GRAY = 0
    const val YELLOW = 1
    const val GREEN = 2

    /** One submitted guess: uppercase word + per-position states. */
    data class Guess(val word: String, val states: List<Int>) {
        init {
            require(word.length == states.size) { "word and states length mismatch" }
            require(word.all { it in 'A'..'Z' }) { "word must be uppercase A-Z" }
        }
    }

    /**
     * The true Wordle coloring of [guess] against [answer]: green = right
     * letter right spot; yellow = letter present but misplaced (respecting
     * multiplicities); gray otherwise. Standard two-pass algorithm.
     */
    fun patternFor(answer: String, guess: String): List<Int> {
        val n = answer.length
        require(guess.length == n)
        val res = IntArray(n)
        val remaining = IntArray(26)
        for (i in 0 until n) {
            if (guess[i] == answer[i]) {
                res[i] = GREEN
            } else {
                remaining[answer[i] - 'A']++
            }
        }
        for (i in 0 until n) {
            if (res[i] == GREEN) continue
            val c = guess[i] - 'A'
            if (c in 0..25 && remaining[c] > 0) {
                res[i] = YELLOW
                remaining[c]--
            } else {
                res[i] = GRAY
            }
        }
        return res.toList()
    }

    /**
     * Filter [dict] to words consistent with every guess. The dictionary is
     * expected to be pre-ordered by desirability (crypto terms, then frequency),
     * so the returned list is already ranked.
     */
    fun solve(dict: List<String>, guesses: List<Guess>): List<String> {
        if (guesses.isEmpty()) return dict
        val len = guesses[0].word.length
        require(guesses.all { it.word.length == len }) { "all guesses must share one word length" }
        return dict.filter { w ->
            w.length == len && guesses.all { g -> patternFor(w, g.word) == g.states }
        }
    }

    /**
     * Suggest a useful next guess from the remaining candidates: the word
     * covering the most frequent still-possible letters per position
     * (duplicate letters counted once). Returns the word plus a short
     * human-readable reason.
     */
    fun bestNextGuess(candidates: List<String>): Pair<String, String> {
        if (candidates.isEmpty()) return "" to "No candidates remain."
        if (candidates.size == 1) {
            return candidates[0] to "Only one possible answer remains."
        }
        val n = candidates[0].length
        val posFreq = Array(n) { IntArray(26) }
        for (w in candidates) {
            if (w.length != n) continue
            for (i in 0 until n) {
                val c = w[i] - 'A'
                if (c in 0..25) posFreq[i][c]++
            }
        }
        var best = candidates[0]
        var bestScore = -1
        for (w in candidates) {
            if (w.length != n) continue
            val seen = BooleanArray(26)
            var s = 0
            for (i in 0 until n) {
                val c = w[i] - 'A'
                if (c in 0..25 && !seen[c]) {
                    seen[c] = true
                    s += posFreq[i][c]
                }
            }
            if (s > bestScore) {
                bestScore = s
                best = w
            }
        }
        val distinct = best.toSet().size
        return best to "Tests the most common remaining letters " +
                "($distinct distinct letters across positions)."
    }

    /** Render states as emoji squares, e.g. "⬜🟨🟩⬜🟩". */
    fun patternEmoji(states: List<Int>): String =
        states.joinToString("") {
            when (it) {
                GREEN -> "\uD83D\uDFE9"
                YELLOW -> "\uD83D\uDFE8"
                else -> "⬜"
            }
        }

    /** Parse a compact pattern like "GYBBG" (B = gray/black) into states. */
    fun parsePattern(s: String): List<Int> = s.map {
        when (it.uppercaseChar()) {
            'G' -> GREEN
            'Y' -> YELLOW
            else -> GRAY
        }
    }
}

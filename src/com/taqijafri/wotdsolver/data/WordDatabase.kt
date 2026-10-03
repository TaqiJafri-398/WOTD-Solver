package com.taqijafri.wotdsolver.data

import android.content.res.Resources

/**
 * Local offline word database.
 *
 * Words live in res/raw/words_3.txt … words_8.txt (one lowercase word per
 * line). The file order IS the ranking: crypto/blockchain/trading terms come
 * first, then common English ordered by frequency.
 *
 * To expand the database later, append lowercase words to the file matching
 * their length — no code changes needed.
 */
class WordDatabase(private val resources: Resources, private val pkg: String) {

    private val cache = HashMap<Int, List<String>>()

    /** Uppercase words of [length], best-first. Empty list for unsupported lengths. */
    fun words(length: Int): List<String> = cache.getOrPut(length) {
        if (length !in 3..8) return@getOrPut emptyList()
        val id = resources.getIdentifier("words_$length", "raw", pkg)
        if (id == 0) return@getOrPut emptyList()
        val out = ArrayList<String>()
        try {
            resources.openRawResource(id).bufferedReader().forEachLine { line ->
                val w = line.trim()
                if (w.length == length && w.all { it in 'a'..'z' }) {
                    out.add(w.uppercase())
                }
            }
        } catch (e: Exception) {
            // corrupted resource: treat as empty rather than crashing
        }
        out
    }
}

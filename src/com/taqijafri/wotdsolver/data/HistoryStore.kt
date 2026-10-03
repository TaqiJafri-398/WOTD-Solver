package com.taqijafri.wotdsolver.data

import android.content.Context
import com.taqijafri.wotdsolver.solver.PuzzleAnalyzer.DetectedGuess
import com.taqijafri.wotdsolver.vision.VisionCore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val id: Long,
    val date: String,
    val wordLength: Int,
    val guesses: List<DetectedGuess>,
    val candidateCount: Int,
    val topCandidates: List<String>,
    val bestGuessWord: String,
    val bestGuessReason: String
)

/**
 * Local puzzle history, stored as JSON in the app's private files directory.
 * Nothing ever leaves the device.
 */
class HistoryStore(private val context: Context) {

    private fun file(): File = File(context.filesDir, "wotd_history.json")

    fun load(): List<HistoryEntry> {
        val f = file()
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(entry: HistoryEntry) {
        val list = load().toMutableList()
        list.add(0, entry)
        while (list.size > 50) list.removeAt(list.size - 1)
        writeAll(list)
    }

    fun delete(id: Long) {
        writeAll(load().filter { it.id != id })
    }

    fun clear() {
        writeAll(emptyList())
    }

    private fun writeAll(list: List<HistoryEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(toJson(it)) }
            file().writeText(arr.toString())
        } catch (e: Exception) {
            // history is best-effort; never crash the app over it
        }
    }

    private fun toJson(e: HistoryEntry): JSONObject {
        val guesses = JSONArray()
        e.guesses.forEach { g ->
            guesses.put(
                JSONObject()
                    .put("word", g.word)
                    .put("states", g.states.joinToString("") { s ->
                        when (s) {
                            VisionCore.GREEN -> "G"
                            VisionCore.YELLOW -> "Y"
                            else -> "B"
                        }
                    })
            )
        }
        return JSONObject()
            .put("id", e.id)
            .put("date", e.date)
            .put("wordLength", e.wordLength)
            .put("guesses", guesses)
            .put("candidateCount", e.candidateCount)
            .put("topCandidates", JSONArray(e.topCandidates))
            .put("bestGuessWord", e.bestGuessWord)
            .put("bestGuessReason", e.bestGuessReason)
    }

    private fun fromJson(o: JSONObject): HistoryEntry {
        val guesses = ArrayList<DetectedGuess>()
        val ga = o.optJSONArray("guesses") ?: JSONArray()
        for (i in 0 until ga.length()) {
            val go = ga.getJSONObject(i)
            val word = go.optString("word", "")
            val states = go.optString("states", "").map { c ->
                when (c) {
                    'G' -> VisionCore.GREEN
                    'Y' -> VisionCore.YELLOW
                    else -> VisionCore.GRAY
                }
            }
            if (word.isNotEmpty() && word.length == states.size) {
                guesses.add(DetectedGuess(word, states, List(word.length) { false }))
            }
        }
        val top = ArrayList<String>()
        val ta = o.optJSONArray("topCandidates") ?: JSONArray()
        for (i in 0 until ta.length()) top.add(ta.optString(i))
        return HistoryEntry(
            o.optLong("id", System.currentTimeMillis()),
            o.optString("date", ""),
            o.optInt("wordLength", 0),
            guesses,
            o.optInt("candidateCount", top.size),
            top,
            o.optString("bestGuessWord", ""),
            o.optString("bestGuessReason", "")
        )
    }

    companion object {
        fun nowLabel(): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
}

package com.standoutbylogic.sudoku.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化：种子码与对局记录。全部保存失败时按原 Web 策略降级（先淘汰一半，再清空）。
 */
class PrefsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSeeds(): MutableMap<String, SavedGame> {
        val output = LinkedHashMap<String, SavedGame>()
        val raw = prefs.getString(KEY_SEEDS, null) ?: return output
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val seed = keys.next()
                val item = root.optJSONObject(seed) ?: continue
                val puzzle = parseIntArray(item.optString("puzzle")) ?: continue
                val solution = parseIntArray(item.optString("solution")) ?: continue
                if (puzzle.size != 81 || solution.size != 81) {
                    continue
                }
                output[seed] = SavedGame(
                    puzzle = puzzle,
                    solution = solution,
                    difficulty = Difficulty.fromName(item.optString("difficulty")),
                    savedAt = item.optLong("savedAt"),
                    lastUsedAt = item.optLong("lastUsedAt")
                )
            }
        } catch (_: Exception) {
            output.clear()
        }
        return output
    }

    fun saveSeeds(seeds: Map<String, SavedGame>): Boolean {
        return try {
            val root = JSONObject()
            for ((seed, game) in seeds) {
                val item = JSONObject()
                item.put("puzzle", game.puzzle.joinToString(""))
                item.put("solution", game.solution.joinToString(""))
                item.put("difficulty", game.difficulty.name)
                item.put("savedAt", game.savedAt)
                item.put("lastUsedAt", game.lastUsedAt)
                root.put(seed, item)
            }
            prefs.edit().putString(KEY_SEEDS, root.toString()).commit()
        } catch (_: Exception) {
            false
        }
    }

    /** 振动开关，默认开启。 */
    fun loadVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, true)

    fun saveVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun loadRecords(): List<Record> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val output = ArrayList<Record>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val seed = item.optString("seed")
                if (seed.isEmpty()) {
                    continue
                }
                output.add(
                    Record(
                        result = item.optString("result"),
                        seconds = item.optLong("seconds"),
                        seed = seed,
                        difficulty = Difficulty.fromName(item.optString("difficulty")),
                        atMillis = item.optLong("at")
                    )
                )
            }
            output
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecords(records: List<Record>) {
        try {
            val array = JSONArray()
            for (record in records) {
                val item = JSONObject()
                item.put("result", record.result)
                item.put("seconds", record.seconds)
                item.put("seed", record.seed)
                item.put("difficulty", record.difficulty.name)
                item.put("at", record.atMillis)
                array.put(item)
            }
            prefs.edit().putString(KEY_RECORDS, array.toString()).apply()
        } catch (_: Exception) {
            // 忽略写入失败，与 Web 的记录保存行为一致（不阻断游戏）
        }
    }

    private fun parseIntArray(raw: String): IntArray? {
        if (raw.length != 81) {
            return null
        }
        val output = IntArray(81)
        for (index in 0 until 81) {
            val digit = raw[index].digitToIntOrNull() ?: return null
            output[index] = digit
        }
        return output
    }

    private companion object {
        const val PREFS_NAME = "stand_out_by_logic"
        const val KEY_SEEDS = "stand-out-by-logic.seeds"
        const val KEY_RECORDS = "stand-out-by-logic.records"
        const val KEY_VIBRATION = "stand-out-by-logic.vibration"
    }
}

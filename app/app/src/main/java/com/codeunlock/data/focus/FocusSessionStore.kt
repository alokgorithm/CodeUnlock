package com.codeunlock.data.focus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FocusSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val mode: String,
    val subjectTag: String,
    val durationSeconds: Long,
    val completedAtMillis: Long = System.currentTimeMillis(),
    val problemsSolved: Int = 0
)

object FocusSessionStore {

    private const val PREFS_NAME = "codeunlock_focus_sessions"
    private const val KEY_SESSIONS = "saved_sessions_json"

    fun recordSession(context: Context, session: FocusSessionRecord) {
        val sessions = getAllSessions(context).toMutableList()
        sessions.add(0, session) // Most recent first

        // Keep last 100 sessions
        val trimmed = if (sessions.size > 100) sessions.take(100) else sessions

        val jsonArray = JSONArray()
        trimmed.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("mode", s.mode)
                put("subjectTag", s.subjectTag)
                put("durationSeconds", s.durationSeconds)
                put("completedAtMillis", s.completedAtMillis)
                put("problemsSolved", s.problemsSolved)
            }
            jsonArray.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, jsonArray.toString())
            .apply()
    }

    fun getAllSessions(context: Context): List<FocusSessionRecord> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, null) ?: return emptyList()

        return runCatching {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<FocusSessionRecord>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    FocusSessionRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        mode = obj.getString("mode"),
                        subjectTag = obj.getString("subjectTag"),
                        durationSeconds = obj.getLong("durationSeconds"),
                        completedAtMillis = obj.getLong("completedAtMillis"),
                        problemsSolved = obj.optInt("problemsSolved", 0)
                    )
                )
            }
            list
        }.getOrElse { emptyList() }
    }

    fun getTodayFocusMinutes(context: Context): Int {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val seconds = getAllSessions(context)
            .filter { it.completedAtMillis >= startOfDay }
            .sumOf { it.durationSeconds }
        return (seconds / 60L).toInt()
    }

    fun getTodaySessions(context: Context): List<FocusSessionRecord> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        return getAllSessions(context).filter { it.completedAtMillis >= startOfDay }
    }
}

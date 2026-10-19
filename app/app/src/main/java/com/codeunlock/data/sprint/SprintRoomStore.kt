package com.codeunlock.data.sprint

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * SprintRoomStore — multiplayer coding sprint rooms inspired by Regain's group accountability
 * and Codeforces/LeetCode co-working sprints.
 *
 * Supports public community sprints, private 6-char room code joins, and offline co-working.
 */
object SprintRoomStore {

    private val defaultRooms = listOf(
        SprintRoom(
            id = "room_daily_grind",
            name = "⚡ LeetCode Daily Rapid Sprint",
            code = "DAILY-25",
            topic = "Top 200 Track",
            durationMinutes = 25,
            problemId = 1,
            problemTitle = "Two Sum",
            participants = listOf(
                SprintParticipant(username = "You", avatarEmoji = "🧑‍💻", currentProblemTitle = "Two Sum", status = "Coding 🧠", streakDays = 7, isHost = false),
                SprintParticipant(username = "Aryan", avatarEmoji = "🦁", currentProblemTitle = "Two Sum", status = "Coding 🧠", streakDays = 14, isHost = true),
                SprintParticipant(username = "Priya", avatarEmoji = "🌸", currentProblemTitle = "Two Sum", status = "Solved ✅", streakDays = 21, solvedCountToday = 2),
                SprintParticipant(username = "DevGuru", avatarEmoji = "🧙‍♂️", currentProblemTitle = "Two Sum", status = "Coding 🧠", streakDays = 45, solvedCountToday = 1),
                SprintParticipant(username = "Alex_C", avatarEmoji = "🚀", currentProblemTitle = "Two Sum", status = "Break ☕", streakDays = 3)
            )
        ),
        SprintRoom(
            id = "room_blind75",
            name = "🧠 Blind 75 & Top 200 Co-Working",
            code = "BLIND-45",
            topic = "Dynamic Programming & Trees",
            durationMinutes = 45,
            problemId = 2,
            problemTitle = "Valid Parentheses",
            participants = listOf(
                SprintParticipant(username = "You", avatarEmoji = "🧑‍💻", currentProblemTitle = "Valid Parentheses", status = "Coding 🧠", streakDays = 7),
                SprintParticipant(username = "CodeNinja", avatarEmoji = "🥷", currentProblemTitle = "Valid Parentheses", status = "Coding 🧠", streakDays = 30, isHost = true),
                SprintParticipant(username = "Sneha_K", avatarEmoji = "⭐", currentProblemTitle = "Valid Parentheses", status = "Coding 🧠", streakDays = 12),
                SprintParticipant(username = "Marcus", avatarEmoji = "🐺", currentProblemTitle = "Valid Parentheses", status = "Solved ✅", streakDays = 8, solvedCountToday = 3)
            )
        ),
        SprintRoom(
            id = "room_cf_div2",
            name = "🏆 Codeforces Contest Simulation",
            code = "DIV2-60",
            topic = "Competitive Programming",
            durationMinutes = 60,
            problemId = 3,
            problemTitle = "Merge Two Sorted Lists",
            participants = listOf(
                SprintParticipant(username = "You", avatarEmoji = "🧑‍💻", currentProblemTitle = "Merge Two Sorted Lists", status = "Coding 🧠", streakDays = 7),
                SprintParticipant(username = "Grandmaster_V", avatarEmoji = "👑", currentProblemTitle = "Merge Two Sorted Lists", status = "Coding 🧠", streakDays = 92, isHost = true),
                SprintParticipant(username = "Rohan_IIT", avatarEmoji = "⚡", currentProblemTitle = "Merge Two Sorted Lists", status = "Coding 🧠", streakDays = 28)
            )
        )
    )

    private val _rooms = MutableStateFlow(defaultRooms)
    val rooms: StateFlow<List<SprintRoom>> = _rooms.asStateFlow()

    private val _activeRoom = MutableStateFlow<SprintRoom?>(null)
    val activeRoom: StateFlow<SprintRoom?> = _activeRoom.asStateFlow()

    fun selectRoom(roomId: String) {
        val found = _rooms.value.find { it.id == roomId }
        _activeRoom.value = found
    }

    fun leaveRoom() {
        _activeRoom.value = null
    }

    fun createRoom(
        name: String,
        topic: String,
        durationMinutes: Int,
        problemTitle: String = "Selected DSA Track Problem"
    ): SprintRoom {
        val cleanCode = "SPRINT-${(1000..9999).random()}"
        val newRoom = SprintRoom(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Custom Coding Sprint" },
            code = cleanCode,
            topic = topic.ifBlank { "Algorithms" },
            durationMinutes = durationMinutes,
            problemTitle = problemTitle,
            participants = listOf(
                SprintParticipant(
                    username = "You (Host)",
                    avatarEmoji = "👑",
                    currentProblemTitle = problemTitle,
                    status = "Coding 🧠",
                    isHost = true
                )
            ),
            isPublic = false
        )

        _rooms.value = listOf(newRoom) + _rooms.value
        _activeRoom.value = newRoom
        return newRoom
    }

    fun joinByCode(code: String): Boolean {
        val clean = code.trim().uppercase()
        val found = _rooms.value.find { it.code.uppercase() == clean } ?: return false
        _activeRoom.value = found
        return true
    }
}

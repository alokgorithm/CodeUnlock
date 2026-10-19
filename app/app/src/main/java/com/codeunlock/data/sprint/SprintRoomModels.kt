package com.codeunlock.data.sprint

import java.util.UUID

data class SprintParticipant(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val avatarEmoji: String,
    val currentProblemTitle: String,
    val status: String, // "Coding 🧠", "Solved ✅", "Break ☕"
    val streakDays: Int = 1,
    val isHost: Boolean = false,
    val solvedCountToday: Int = 0
)

data class SprintRoom(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val code: String,
    val topic: String,
    val durationMinutes: Int = 25,
    val problemId: Int = 1,
    val problemTitle: String = "Two Sum",
    val participants: List<SprintParticipant> = emptyList(),
    val isPublic: Boolean = true,
    val isSprintActive: Boolean = true
)

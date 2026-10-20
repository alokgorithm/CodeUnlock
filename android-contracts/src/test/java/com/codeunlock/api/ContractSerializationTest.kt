package com.codeunlock.api

import com.codeunlock.api.dto.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ContractSerializationTest {

    private val gson = Gson()

    @Test
    fun testVerifySolveResponseSerialization() {
        val json = """
            {
                "verified": true,
                "unlock_granted_minutes": 30,
                "daily_progress": {
                    "problems_solved": 1,
                    "daily_target": 3,
                    "target_met": false,
                    "unlock_minutes_earned": 30,
                    "unlock_minutes_used": 0
                },
                "unlock_session": {
                    "id": "session-123",
                    "app_package": null,
                    "unlock_type": "coding",
                    "duration_minutes": 30,
                    "granted_at": "2026-10-20T04:00:00Z",
                    "expires_at": "2026-10-20T04:30:00Z",
                    "is_active": true
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, VerifySolveResponse::class.java)

        assertTrue(response.verified)
        assertEquals(30, response.unlockGrantedMinutes)
        assertEquals(1, response.dailyProgress.problemsSolved)
        assertEquals(3, response.dailyProgress.dailyTarget)
        assertFalse(response.dailyProgress.targetMet)
        assertNotNull(response.unlockSession)
        assertEquals("session-123", response.unlockSession?.id)
        assertEquals(30, response.unlockSession?.durationMinutes)
        assertEquals(true, response.unlockSession?.isActive)
    }

    @Test
    fun testUserSettingsDtoSerialization() {
        val json = """
            {
                "usage_mode": "strict",
                "daily_solve_target": 3,
                "restricted_apps": ["com.google.android.youtube", "com.zhiliaoapp.musically"],
                "per_app_limits": {"com.google.android.youtube": 15},
                "emergency_unlock_enabled": true,
                "free_unlock_enabled": false,
                "is_first_setup_complete": true,
                "pending_changes": null
            }
        """.trimIndent()

        val settings = gson.fromJson(json, UserSettingsDto::class.java)

        assertEquals("strict", settings.usageMode)
        assertEquals(3, settings.dailySolveTarget)
        assertEquals(2, settings.restrictedApps.size)
        assertTrue(settings.restrictedApps.contains("com.google.android.youtube"))
        assertEquals(15, settings.perAppLimits["com.google.android.youtube"])
        assertTrue(settings.emergencyUnlockEnabled)
        assertFalse(settings.freeUnlockEnabled)
        assertTrue(settings.isFirstSetupComplete)
    }

    @Test
    fun testProfileDtoSerialization() {
        val json = """
            {
                "id": "user-42",
                "username": "CodeUnlockDev",
                "email": "dev@codeunlock.app",
                "avatar_emoji": "⚡",
                "college": "Autonomous Engineering",
                "leetcode_username": "cu_dev",
                "codeforces_handle": "cu_cf",
                "last_sync_at": "2026-10-20T00:00:00Z",
                "ranking": {
                    "global_rank": 5,
                    "college_rank": 1,
                    "ranking_score": 1500.0,
                    "total_verified_solves": 12,
                    "consistency_streak_days": 4
                },
                "badges": [
                    {
                        "badge_id": 1,
                        "name": "First Solve",
                        "category": "discipline",
                        "icon_emoji": "🎯",
                        "earned_at": "2026-10-18T10:00:00Z",
                        "progress": 1.0
                    }
                ],
                "stats_summary": {
                    "total_verified_solves": 12,
                    "current_streak": 4,
                    "wellbeing_score_30d_avg": 92.5,
                    "screen_time_reduction_pct": 40
                },
                "classrooms": [
                    {
                        "id": "c-1",
                        "name": "Alpha Class",
                        "code": "ALPHA"
                    }
                ]
            }
        """.trimIndent()

        val profile = gson.fromJson(json, ProfileDto::class.java)

        assertEquals("user-42", profile.id)
        assertEquals("CodeUnlockDev", profile.username)
        assertEquals("⚡", profile.avatarEmoji)
        assertEquals(5, profile.ranking?.globalRank)
        assertEquals(1, profile.badges.size)
        assertEquals("First Solve", profile.badges[0].name)
        assertEquals(12, profile.statsSummary.totalVerifiedSolves)
        assertEquals(40, profile.statsSummary.screenTimeReductionPct)
        assertEquals(1, profile.classrooms.size)
    }

    @Test
    fun testProblemDtoSerialization() {
        val json = """
            {
                "id": 1,
                "title": "Two Sum",
                "slug": "two-sum",
                "difficulty": "Easy",
                "platform": "leetcode",
                "topic": "Array",
                "subtopics": ["Array", "Hash Table"],
                "pattern": "Two Pointers",
                "platform_problem_id": 1,
                "platform_slug": "two-sum",
                "track_position": 1,
                "rating": 9.8,
                "companies": ["Amazon", "Google"],
                "expected_solve_time_seconds": 1200,
                "url": "https://leetcode.com/problems/two-sum/",
                "user_status": "available",
                "verified": false
            }
        """.trimIndent()

        val problem = gson.fromJson(json, ProblemDto::class.java)

        assertEquals(1, problem.id)
        assertEquals("Two Sum", problem.title)
        assertEquals("Easy", problem.difficulty)
        assertEquals("leetcode", problem.platform)
        assertEquals(1, problem.trackPosition)
        assertEquals(2, problem.companies?.size)
        assertEquals("https://leetcode.com/problems/two-sum/", problem.url)
    }
}

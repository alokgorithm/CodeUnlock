package com.codeunlock.api.service

import com.codeunlock.api.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * CodeUnlock API v2.0 — Retrofit Interface
 *
 * Base URL: https://codeunlock-api.arka-cmd.workers.dev
 * Auth: Bearer JWT (Supabase access token) on all endpoints except /api/health
 *
 * All responses are wrapped in ApiResponse<T>:
 *   { "success": true, "data": T }  or
 *   { "success": false, "error": { "code": "...", "message": "..." } }
 */
interface CodeUnlockApiService {

    // ============================================================
    // Health Check
    // ============================================================

    @GET("api/health")
    suspend fun health(): Response<ApiResponse<HealthResponse>>

    // ============================================================
    // Problems
    // ============================================================

    /** Get the next unsolved problem in the 200-problem track. */
    @GET("api/next-problem")
    suspend fun getNextProblem(): Response<ApiResponse<NextProblemResponse>>

    /** List all problems with optional filters. */
    @GET("api/problems")
    suspend fun listProblems(
        @Query("topic") topic: String? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("platform") platform: String? = null
    ): Response<ApiResponse<ProblemListResponse>>

    // ============================================================
    // Verification (30s cooldown enforced server-side)
    // ============================================================

    /**
     * Verify a solve on LeetCode/Codeforces.
     *
     * On success: grants 30min unlock + updates daily progress.
     * Cooldown: 30 seconds between calls.
     *
     * Error codes:
     *  - ALREADY_VERIFIED (409)
     *  - SUBMISSION_PREDATES_ASSIGNMENT (422)
     *  - LEETCODE_USERNAME_NOT_SET / CODEFORCES_HANDLE_NOT_SET (400)
     *  - NO_ACCEPTED_SUBMISSION (400)
     *  - PLATFORM_UNAVAILABLE (503)
     *  - RATE_LIMITED (429)
     */
    @POST("api/verify-solve")
    suspend fun verifySolve(
        @Body request: VerifySolveRequest
    ): Response<ApiResponse<VerifySolveResponse>>

    // ============================================================
    // Unlock Management
    // ============================================================

    /** Get all currently active unlock sessions (not expired). */
    @GET("api/unlock/active")
    suspend fun getActiveUnlocks(): Response<ApiResponse<ActiveUnlocksResponse>>

    /**
     * Request emergency unlock (30 min, once per day).
     * Returns 429 EMERGENCY_ALREADY_USED if already used today.
     */
    @POST("api/unlock/emergency")
    suspend fun emergencyUnlock(
        @Body request: EmergencyUnlockRequest = EmergencyUnlockRequest()
    ): Response<ApiResponse<EmergencyUnlockResponse>>

    /**
     * Request free unlock (1 min, once per day).
     * Returns 429 FREE_UNLOCK_ALREADY_USED if already used today.
     */
    @POST("api/unlock/free")
    suspend fun freeUnlock(
        @Body request: FreeUnlockRequest
    ): Response<ApiResponse<FreeUnlockResponse>>

    // ============================================================
    // Stats
    // ============================================================

    /** Get cached LeetCode stats (from last sync). */
    @GET("api/stats/leetcode")
    suspend fun getLeetCodeStats(): Response<ApiResponse<LeetCodeStatsDto>>

    /** Get cached Codeforces stats (from last sync). */
    @GET("api/stats/codeforces")
    suspend fun getCodeforcesStats(): Response<ApiResponse<CodeforcesStatsDto>>

    /** Get user's 200-track progress (solved, streak, breakdowns). */
    @GET("api/stats/progress")
    suspend fun getTrackProgress(): Response<ApiResponse<TrackProgressDto>>

    /** Get wellbeing stats (today + 7-day trend). */
    @GET("api/stats/wellbeing")
    suspend fun getWellbeingStats(): Response<ApiResponse<WellbeingStatsDto>>

    // ============================================================
    // Settings
    // ============================================================

    /** Get current user settings (mode, target, apps, pending changes). */
    @GET("api/settings")
    suspend fun getSettings(): Response<ApiResponse<UserSettingsDto>>

    /**
     * Update settings.
     * - First setup: applies immediately.
     * - Subsequent: mode/target changes apply next day; app list changes apply immediately.
     */
    @PUT("api/settings")
    suspend fun updateSettings(
        @Body request: UpdateSettingsRequest
    ): Response<ApiResponse<UpdateSettingsResponse>>

    // ============================================================
    // Sync
    // ============================================================

    /** Push device wellbeing data (screen time, per-app usage). */
    @POST("api/wellbeing/sync")
    suspend fun syncWellbeing(
        @Body request: WellbeingSyncRequest
    ): Response<ApiResponse<WellbeingSyncResponse>>

    /** Trigger manual platform stats sync (LeetCode + Codeforces). */
    @POST("api/sync/trigger")
    suspend fun triggerSync(): Response<ApiResponse<SyncTriggerResponse>>

    // ============================================================
    // Rankings / Leaderboards
    // ============================================================

    /** Get global leaderboard (paginated). */
    @GET("api/rankings/global")
    suspend fun getGlobalRankings(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<ApiResponse<GlobalRankingsResponse>>

    /** Get college leaderboard (based on user's college_name). */
    @GET("api/rankings/college")
    suspend fun getCollegeRankings(): Response<ApiResponse<CollegeRankingsResponse>>

    /** Get classroom leaderboard. */
    @GET("api/rankings/classroom/{id}")
    suspend fun getClassroomRankings(
        @Path("id") classroomId: String
    ): Response<ApiResponse<ClassroomRankingsResponse>>

    // ============================================================
    // Social — Classrooms
    // ============================================================

    /** Create a new classroom (auto-generates 6-char join code). */
    @POST("api/classrooms")
    suspend fun createClassroom(
        @Body request: CreateClassroomRequest
    ): Response<ApiResponse<ClassroomDto>>

    /** Join a classroom by its 6-char code. */
    @POST("api/classrooms/join")
    suspend fun joinClassroom(
        @Body request: JoinClassroomRequest
    ): Response<ApiResponse<JoinClassroomResponse>>

    // ============================================================
    // Social — Friends
    // ============================================================

    /** List all accepted friends. */
    @GET("api/friends")
    suspend fun listFriends(): Response<ApiResponse<FriendsListResponse>>

    /** Send a friend request by username. */
    @POST("api/friends/request")
    suspend fun sendFriendRequest(
        @Body request: FriendRequestDto
    ): Response<ApiResponse<MessageResponse>>

    /** Accept a pending friend request. */
    @POST("api/friends/accept")
    suspend fun acceptFriendRequest(
        @Body request: AcceptFriendRequest
    ): Response<ApiResponse<MessageResponse>>

    // ============================================================
    // Search
    // ============================================================

    /** Search users by username, leetcode_username, or codeforces_handle. */
    @GET("api/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<ApiResponse<SearchResponse>>
}

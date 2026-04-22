package com.codeunlock.api.repository

import com.codeunlock.api.dto.*
import com.codeunlock.api.service.CodeUnlockApiService
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer wrapping the CodeUnlock API.
 * Provides clean Result<T> return types for ViewModel consumption.
 *
 * All methods return Result<T>:
 *   - Result.success(data)    on success
 *   - Result.failure(exception) on error (with error code + message)
 */
@Singleton
class CodeUnlockRepository @Inject constructor(
    private val api: CodeUnlockApiService
) {

    // ============================================================
    // Problems
    // ============================================================

    suspend fun getNextProblem(): Result<NextProblemResponse> =
        safeApiCall { api.getNextProblem() }

    suspend fun listProblems(
        topic: String? = null,
        difficulty: String? = null,
        platform: String? = null
    ): Result<ProblemListResponse> =
        safeApiCall { api.listProblems(topic, difficulty, platform) }

    // ============================================================
    // Verification
    // ============================================================

    suspend fun verifySolve(problemId: Int): Result<VerifySolveResponse> =
        safeApiCall { api.verifySolve(VerifySolveRequest(problemId)) }

    // ============================================================
    // Unlock
    // ============================================================

    suspend fun getActiveUnlocks(): Result<ActiveUnlocksResponse> =
        safeApiCall { api.getActiveUnlocks() }

    suspend fun emergencyUnlock(appPackage: String? = null): Result<EmergencyUnlockResponse> =
        safeApiCall { api.emergencyUnlock(EmergencyUnlockRequest(appPackage)) }

    suspend fun freeUnlock(appPackage: String): Result<FreeUnlockResponse> =
        safeApiCall { api.freeUnlock(FreeUnlockRequest(appPackage)) }

    // ============================================================
    // Stats
    // ============================================================

    suspend fun getLeetCodeStats(): Result<LeetCodeStatsDto> =
        safeApiCall { api.getLeetCodeStats() }

    suspend fun getCodeforcesStats(): Result<CodeforcesStatsDto> =
        safeApiCall { api.getCodeforcesStats() }

    suspend fun getTrackProgress(): Result<TrackProgressDto> =
        safeApiCall { api.getTrackProgress() }

    suspend fun getWellbeingStats(): Result<WellbeingStatsDto> =
        safeApiCall { api.getWellbeingStats() }

    // ============================================================
    // Settings
    // ============================================================

    suspend fun getSettings(): Result<UserSettingsDto> =
        safeApiCall { api.getSettings() }

    suspend fun updateSettings(request: UpdateSettingsRequest): Result<UpdateSettingsResponse> =
        safeApiCall { api.updateSettings(request) }

    // ============================================================
    // Sync
    // ============================================================

    suspend fun syncWellbeing(request: WellbeingSyncRequest): Result<WellbeingSyncResponse> =
        safeApiCall { api.syncWellbeing(request) }

    suspend fun triggerSync(): Result<SyncTriggerResponse> =
        safeApiCall { api.triggerSync() }

    // ============================================================
    // Rankings
    // ============================================================

    suspend fun getGlobalRankings(limit: Int = 50, offset: Int = 0): Result<GlobalRankingsResponse> =
        safeApiCall { api.getGlobalRankings(limit, offset) }

    suspend fun getCollegeRankings(): Result<CollegeRankingsResponse> =
        safeApiCall { api.getCollegeRankings() }

    suspend fun getClassroomRankings(classroomId: String): Result<ClassroomRankingsResponse> =
        safeApiCall { api.getClassroomRankings(classroomId) }

    // ============================================================
    // Social
    // ============================================================

    suspend fun createClassroom(name: String): Result<ClassroomDto> =
        safeApiCall { api.createClassroom(CreateClassroomRequest(name)) }

    suspend fun joinClassroom(code: String): Result<JoinClassroomResponse> =
        safeApiCall { api.joinClassroom(JoinClassroomRequest(code)) }

    suspend fun listFriends(): Result<FriendsListResponse> =
        safeApiCall { api.listFriends() }

    suspend fun sendFriendRequest(username: String): Result<MessageResponse> =
        safeApiCall { api.sendFriendRequest(FriendRequestDto(username)) }

    suspend fun acceptFriendRequest(requesterId: String): Result<MessageResponse> =
        safeApiCall { api.acceptFriendRequest(AcceptFriendRequest(requesterId)) }

    suspend fun searchUsers(query: String): Result<SearchResponse> =
        safeApiCall { api.searchUsers(query) }

    // ============================================================
    // Internal: Safe API Call Wrapper
    // ============================================================

    private suspend fun <T> safeApiCall(
        call: suspend () -> Response<ApiResponse<T>>
    ): Result<T> {
        return try {
            val response = call()
            val body = response.body()

            if (response.isSuccessful && body?.success == true && body.data != null) {
                Result.success(body.data)
            } else {
                val errorCode = body?.error?.code ?: "HTTP_${response.code()}"
                val errorMsg = body?.error?.message ?: response.message() ?: "Unknown error"
                Result.failure(ApiException(errorCode, errorMsg, response.code()))
            }
        } catch (e: Exception) {
            Result.failure(ApiException("NETWORK_ERROR", e.message ?: "Network error", 0, e))
        }
    }
}

/**
 * Custom exception carrying API error codes for UI handling.
 *
 * Usage in ViewModel:
 *   result.onFailure { error ->
 *       if (error is ApiException) {
 *           when (error.code) {
 *               "RATE_LIMITED" -> showCooldownUI(error.message)
 *               "ALREADY_VERIFIED" -> showAlreadyVerifiedUI()
 *               "UNAUTHORIZED" -> redirectToLogin()
 *           }
 *       }
 *   }
 */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
    cause: Throwable? = null
) : Exception(message, cause) {

    val isRateLimited: Boolean get() = code == "RATE_LIMITED"
    val isUnauthorized: Boolean get() = code == "UNAUTHORIZED"
    val isNotFound: Boolean get() = code == "NOT_FOUND"
    val isPlatformUnavailable: Boolean get() = code.startsWith("PLATFORM_UNAVAILABLE")
    val isAlreadyVerified: Boolean get() = code == "ALREADY_VERIFIED"

    override fun toString(): String = "ApiException(code=$code, status=$httpStatus, message=$message)"
}

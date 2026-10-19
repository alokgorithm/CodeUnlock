package com.codeunlock.api.dto

import com.google.gson.annotations.SerializedName

// ============================================================
// API Response Envelope
// ============================================================

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: ApiError? = null
)

data class ApiError(
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String,
    @SerializedName("details") val details: Any? = null
)

// ============================================================
// Problem DTOs
// ============================================================

data class ProblemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("difficulty") val difficulty: String,
    @SerializedName("platform") val platform: String,
    @SerializedName("topic") val topic: String,
    @SerializedName("topics") val topics: List<String>,
    @SerializedName("pattern") val pattern: String,
    @SerializedName("platform_id") val platformId: Int?,
    @SerializedName("platform_slug") val platformSlug: String,
    @SerializedName("track_position") val trackPosition: Int,
    @SerializedName("frequency_score") val frequencyScore: Double,
    @SerializedName("companies") val companies: List<String>,
    @SerializedName("elo_rating") val eloRating: Int,
    @SerializedName("url") val url: String? = null,
    @SerializedName("user_status") val userStatus: String? = null,
    @SerializedName("verified") val verified: Boolean? = null
)

data class NextProblemResponse(
    @SerializedName("problem") val problem: ProblemDto?,
    @SerializedName("progress") val progress: TrackProgressSummary,
    @SerializedName("message") val message: String? = null
)

data class TrackProgressSummary(
    @SerializedName("solved_count") val solvedCount: Int,
    @SerializedName("total_count") val totalCount: Int,
    @SerializedName("current_position") val currentPosition: Int
)

data class ProblemListResponse(
    @SerializedName("problems") val problems: List<ProblemDto>,
    @SerializedName("total") val total: Int
)

// ============================================================
// Verification DTOs
// ============================================================

data class VerifySolveRequest(
    @SerializedName("problem_id") val problemId: Int,
    @SerializedName("platform") val platform: String? = null
)

data class VerifySolveResponse(
    @SerializedName("verified") val verified: Boolean,
    @SerializedName("unlock_granted_minutes") val unlockGrantedMinutes: Int,
    @SerializedName("daily_progress") val dailyProgress: DailyProgressDto,
    @SerializedName("unlock_session") val unlockSession: UnlockSessionDto?
)

data class DailyProgressDto(
    @SerializedName("problems_solved") val problemsSolved: Int,
    @SerializedName("daily_target") val dailyTarget: Int,
    @SerializedName("target_met") val targetMet: Boolean,
    @SerializedName("unlock_minutes_earned") val unlockMinutesEarned: Int,
    @SerializedName("unlock_minutes_used") val unlockMinutesUsed: Int
)

// ============================================================
// Unlock DTOs
// ============================================================

data class UnlockSessionDto(
    @SerializedName("id") val id: String,
    @SerializedName("app_package") val appPackage: String? = null,
    @SerializedName("unlock_type") val unlockType: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("granted_at") val grantedAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("is_active") val isActive: Boolean? = null
)

data class ActiveUnlocksResponse(
    @SerializedName("active_unlocks") val activeUnlocks: List<UnlockSessionDto>
)

data class EmergencyUnlockRequest(
    @SerializedName("app_package") val appPackage: String? = null
)

data class EmergencyUnlockResponse(
    @SerializedName("unlock_session") val unlockSession: UnlockSessionDto,
    @SerializedName("message") val message: String
)

data class FreeUnlockRequest(
    @SerializedName("app_package") val appPackage: String
)

data class FreeUnlockResponse(
    @SerializedName("unlock_session") val unlockSession: UnlockSessionDto,
    @SerializedName("message") val message: String
)

// ============================================================
// Settings DTOs
// ============================================================

data class UserSettingsDto(
    @SerializedName("usage_mode") val usageMode: String,
    @SerializedName("daily_solve_target") val dailySolveTarget: Int,
    @SerializedName("restricted_apps") val restrictedApps: List<String>,
    @SerializedName("per_app_limits") val perAppLimits: Map<String, Int>,
    @SerializedName("emergency_unlock_enabled") val emergencyUnlockEnabled: Boolean,
    @SerializedName("free_unlock_enabled") val freeUnlockEnabled: Boolean,
    @SerializedName("is_first_setup_complete") val isFirstSetupComplete: Boolean,
    @SerializedName("pending_changes") val pendingChanges: PendingChangesDto?
)

data class PendingChangesDto(
    @SerializedName("usage_mode") val usageMode: String?,
    @SerializedName("daily_solve_target") val dailySolveTarget: Int?,
    @SerializedName("apply_epoch_day") val applyEpochDay: Long?
)

data class UpdateSettingsRequest(
    @SerializedName("usage_mode") val usageMode: String? = null,
    @SerializedName("daily_solve_target") val dailySolveTarget: Int? = null,
    @SerializedName("restricted_apps") val restrictedApps: List<String>? = null,
    @SerializedName("per_app_limits") val perAppLimits: Map<String, Int>? = null,
    @SerializedName("emergency_unlock_enabled") val emergencyUnlockEnabled: Boolean? = null,
    @SerializedName("free_unlock_enabled") val freeUnlockEnabled: Boolean? = null
)

data class UpdateSettingsResponse(
    @SerializedName("applied") val applied: Boolean,
    @SerializedName("pending_apply_date") val pendingApplyDate: String? = null,
    @SerializedName("message") val message: String
)

// ============================================================
// Stats DTOs
// ============================================================

data class LeetCodeStatsDto(
    @SerializedName("total_solved") val totalSolved: Int,
    @SerializedName("easy_solved") val easySolved: Int,
    @SerializedName("medium_solved") val mediumSolved: Int,
    @SerializedName("hard_solved") val hardSolved: Int,
    @SerializedName("contest_rating") val contestRating: Double,
    @SerializedName("global_ranking") val globalRanking: Int,
    @SerializedName("problems_solved_today") val problemsSolvedToday: Int,
    @SerializedName("as_of") val asOf: String
)

data class CodeforcesStatsDto(
    @SerializedName("current_rating") val currentRating: Int,
    @SerializedName("max_rating") val maxRating: Int,
    @SerializedName("rank") val rank: String,
    @SerializedName("problems_solved") val problemsSolved: Int,
    @SerializedName("problems_solved_today") val problemsSolvedToday: Int,
    @SerializedName("contest_count") val contestCount: Int,
    @SerializedName("as_of") val asOf: String
)

data class TrackProgressDto(
    @SerializedName("total_problems") val totalProblems: Int,
    @SerializedName("solved") val solved: Int,
    @SerializedName("current_streak") val currentStreak: Int,
    @SerializedName("difficulty_breakdown") val difficultyBreakdown: Map<String, Int>,
    @SerializedName("topic_breakdown") val topicBreakdown: Map<String, Int>,
    @SerializedName("next_problem") val nextProblem: NextProblemSummary?
)

data class NextProblemSummary(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("slug") val slug: String
)

data class WellbeingStatsDto(
    @SerializedName("today") val today: WellbeingTodayDto?,
    @SerializedName("weekly_trend") val weeklyTrend: List<WellbeingTrendEntry>
)

data class WellbeingTodayDto(
    @SerializedName("total_screen_time_minutes") val totalScreenTimeMinutes: Int,
    @SerializedName("restricted_app_time_minutes") val restrictedAppTimeMinutes: Int,
    @SerializedName("pickup_count") val pickupCount: Int,
    @SerializedName("wellbeing_score") val wellbeingScore: Double
)

data class WellbeingTrendEntry(
    @SerializedName("date") val date: String,
    @SerializedName("score") val score: Double,
    @SerializedName("screen_time") val screenTime: Int
)

// ============================================================
// Wellbeing Sync DTOs
// ============================================================

data class WellbeingSyncRequest(
    @SerializedName("date") val date: String,
    @SerializedName("apps") val apps: List<AppUsageEntry>,
    @SerializedName("total_screen_time_minutes") val totalScreenTimeMinutes: Int,
    @SerializedName("pickup_count") val pickupCount: Int,
    @SerializedName("blocked_attempt_count") val blockedAttemptCount: Int = 0,
    @SerializedName("unlock_count") val unlockCount: Int = 0,
    @SerializedName("longest_session_app") val longestSessionApp: String? = null
)

data class AppUsageEntry(
    @SerializedName("app_package") val appPackage: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("pickup_count") val pickupCount: Int,
    @SerializedName("category") val category: String // "social" | "entertainment" | "productivity" | "other"
)

data class WellbeingSyncResponse(
    @SerializedName("wellbeing_score") val wellbeingScore: Double
)

// ============================================================
// Sync DTOs
// ============================================================

data class SyncTriggerResponse(
    @SerializedName("synced") val synced: List<String>
)

// ============================================================
// Rankings DTOs
// ============================================================

data class RankingEntry(
    @SerializedName("rank") val rank: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String,
    @SerializedName("ranking_score") val rankingScore: Double,
    @SerializedName("total_solves") val totalSolves: Int,
    @SerializedName("streak_days") val streakDays: Int,
    @SerializedName("weekly_change") val weeklyChange: Int? = null
)

data class MyRankDto(
    @SerializedName("rank") val rank: Int,
    @SerializedName("ranking_score") val rankingScore: Double
)

data class GlobalRankingsResponse(
    @SerializedName("rankings") val rankings: List<RankingEntry>,
    @SerializedName("my_rank") val myRank: MyRankDto?,
    @SerializedName("total_users") val totalUsers: Int
)

data class CollegeRankingsResponse(
    @SerializedName("college") val college: String,
    @SerializedName("rankings") val rankings: List<RankingEntry>
)

data class ClassroomRankingsResponse(
    @SerializedName("classroom_name") val classroomName: String,
    @SerializedName("rankings") val rankings: List<RankingEntry>
)

// ============================================================
// Social DTOs
// ============================================================

data class CreateClassroomRequest(
    @SerializedName("name") val name: String
)

data class ClassroomDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String,
    @SerializedName("creator_id") val creatorId: String? = null
)

data class JoinClassroomRequest(
    @SerializedName("code") val code: String
)

data class JoinClassroomResponse(
    @SerializedName("classroom_id") val classroomId: String,
    @SerializedName("name") val name: String
)

data class FriendProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String
)

data class FriendsListResponse(
    @SerializedName("friends") val friends: List<FriendProfileDto>
)

data class FriendRequestDto(
    @SerializedName("username") val username: String
)

data class AcceptFriendRequest(
    @SerializedName("requester_id") val requesterId: String
)

data class MessageResponse(
    @SerializedName("message") val message: String
)

data class SearchResultDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String,
    @SerializedName("leetcode_username") val leetcodeUsername: String?,
    @SerializedName("codeforces_handle") val codeforcesHandle: String?
)

data class SearchResponse(
    @SerializedName("results") val results: List<SearchResultDto>
)

// ============================================================
// Profile / Onboarding / Daily Progress DTOs
// ============================================================

data class DailyProgressFullDto(
    @SerializedName("date") val date: String,
    @SerializedName("problems_solved") val problemsSolved: Int,
    @SerializedName("daily_target") val dailyTarget: Int,
    @SerializedName("target_met") val targetMet: Boolean,
    @SerializedName("unlock_minutes_earned") val unlockMinutesEarned: Int,
    @SerializedName("unlock_minutes_used") val unlockMinutesUsed: Int,
    @SerializedName("unlock_minutes_remaining") val unlockMinutesRemaining: Int,
    @SerializedName("free_unlock_used") val freeUnlockUsed: Boolean,
    @SerializedName("emergency_unlock_used") val emergencyUnlockUsed: Boolean,
    @SerializedName("bonus_time_earned") val bonusTimeEarned: Boolean,
    @SerializedName("total_screen_time_minutes") val totalScreenTimeMinutes: Int,
    @SerializedName("restricted_app_time_minutes") val restrictedAppTimeMinutes: Int,
    @SerializedName("usage_mode") val usageMode: String,
    @SerializedName("active_unlock_sessions") val activeUnlockSessions: List<UnlockSessionDto>
)

data class ConsumeUnlockTimeRequest(
    @SerializedName("minutes_used") val minutesUsed: Int,
    @SerializedName("app_package") val appPackage: String? = null
)

data class ConsumeUnlockTimeResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("unlock_minutes_used") val unlockMinutesUsed: Int
)

data class ProfileRankingDto(
    @SerializedName("global_rank") val globalRank: Int,
    @SerializedName("college_rank") val collegeRank: Int?,
    @SerializedName("ranking_score") val rankingScore: Double,
    @SerializedName("total_verified_solves") val totalVerifiedSolves: Int,
    @SerializedName("consistency_streak_days") val consistencyStreakDays: Int
)

data class ProfileBadgeDto(
    @SerializedName("badge_id") val badgeId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("icon_emoji") val iconEmoji: String,
    @SerializedName("earned_at") val earnedAt: String?,
    @SerializedName("progress") val progress: Double
)

data class ProfileStatsSummaryDto(
    @SerializedName("total_verified_solves") val totalVerifiedSolves: Int,
    @SerializedName("current_streak") val currentStreak: Int,
    @SerializedName("wellbeing_score_30d_avg") val wellbeingScore30dAvg: Double?,
    @SerializedName("screen_time_reduction_pct") val screenTimeReductionPct: Int
)

data class ProfileClassroomDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String
)

data class ProfileDto(
    @SerializedName("id") val id: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String,
    @SerializedName("college") val college: String?,
    @SerializedName("leetcode_username") val leetcodeUsername: String?,
    @SerializedName("codeforces_handle") val codeforcesHandle: String?,
    @SerializedName("last_sync_at") val lastSyncAt: String?,
    @SerializedName("ranking") val ranking: ProfileRankingDto?,
    @SerializedName("badges") val badges: List<ProfileBadgeDto>,
    @SerializedName("stats_summary") val statsSummary: ProfileStatsSummaryDto,
    @SerializedName("classrooms") val classrooms: List<ProfileClassroomDto>
)

data class UpdateProfileRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_emoji") val avatarEmoji: String? = null,
    @SerializedName("college") val college: String? = null,
    @SerializedName("leetcode_username") val leetcodeUsername: String? = null,
    @SerializedName("codeforces_handle") val codeforcesHandle: String? = null
)

data class UpdateProfileResponse(
    @SerializedName("updated") val updated: Boolean,
    @SerializedName("message") val message: String
)

data class AuthSetupRequest(
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar_emoji") val avatarEmoji: String? = null
)

data class AuthSetupResponse(
    @SerializedName("setup_complete") val setupComplete: Boolean,
    @SerializedName("already_setup") val alreadySetup: Boolean? = null,
    @SerializedName("username") val username: String,
    @SerializedName("message") val message: String
)

data class BadgeProgressDto(
    @SerializedName("badge_id") val badgeId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("icon_emoji") val iconEmoji: String,
    @SerializedName("description") val description: String,
    @SerializedName("progress") val progress: Double,
    @SerializedName("earned_at") val earnedAt: String?
)

data class BadgesResponse(
    @SerializedName("badges") val badges: List<BadgeProgressDto>
)

data class ClassroomListItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("code") val code: String,
    @SerializedName("is_creator") val isCreator: Boolean,
    @SerializedName("member_count") val memberCount: Int,
    @SerializedName("joined_at") val joinedAt: String
)

data class ClassroomsListResponse(
    @SerializedName("classrooms") val classrooms: List<ClassroomListItem>
)

data class PendingFriendDto(
    @SerializedName("requester_id") val requesterId: String? = null,
    @SerializedName("addressee_id") val addresseeId: String? = null,
    @SerializedName("username") val username: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String? = null
)

data class PendingFriendsResponse(
    @SerializedName("incoming") val incoming: List<PendingFriendDto>,
    @SerializedName("outgoing") val outgoing: List<PendingFriendDto>
)

// ============================================================
// Health Check
// ============================================================

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String
)

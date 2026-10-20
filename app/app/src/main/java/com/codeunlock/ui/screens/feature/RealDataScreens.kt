package com.codeunlock.ui.screens.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalContext
import com.codeunlock.data.usage.UsageManager
import com.codeunlock.data.usage.UsageManager.AppUsageStat
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.settings.StrictModeStore
import com.codeunlock.receiver.CodeUnlockDeviceAdminReceiver
import com.codeunlock.service.BlockedAttemptsStore
import com.codeunlock.data.focus.FocusSessionStore
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import com.codeunlock.api.dto.BadgeProgressDto
import com.codeunlock.api.dto.ClassroomListItem
import com.codeunlock.api.dto.FriendProfileDto
import com.codeunlock.api.dto.PendingFriendDto
import com.codeunlock.api.dto.ProblemDto
import com.codeunlock.api.dto.ProfileBadgeDto
import com.codeunlock.api.dto.ProfileClassroomDto
import com.codeunlock.api.dto.ProfileDto
import com.codeunlock.api.dto.ProfileRankingDto
import com.codeunlock.api.dto.ProfileStatsSummaryDto
import com.codeunlock.api.dto.RankingEntry
import com.codeunlock.api.dto.SearchResultDto
import com.codeunlock.api.dto.WellbeingStatsDto
import com.codeunlock.api.repository.CodeUnlockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FallbackData {
    fun defaultBadges(): List<BadgeProgressDto> = listOf(
        BadgeProgressDto(1, "First Solve", "discipline", "🎯", "Solve your first verified coding problem", 1.0, "Earned"),
        BadgeProgressDto(2, "3-Day Discipline", "streak", "🔥", "Maintain a 3-day coding streak", 0.33, null),
        BadgeProgressDto(3, "Consistency King", "streak", "⚡", "Maintain a 7-day coding streak", 0.14, null),
        BadgeProgressDto(4, "Deep Work Sprint", "focus", "🧠", "Complete 5 Pomodoro focus sessions", 0.40, null),
        BadgeProgressDto(5, "DP Explorer", "topics", "🧩", "Solve 5 Dynamic Programming problems", 0.20, null),
        BadgeProgressDto(6, "Shield of Focus", "wellbeing", "🛡️", "Block 25 distraction attempts", 0.60, null)
    )

    fun defaultTop200Track(): List<ProblemDto> = listOf(
        ProblemDto(1, "Two Sum", "two-sum", "Easy", "leetcode", "Array", listOf("Array", "Hash Table"), "Two Pointers", 1, "two-sum", 1, 9.8, listOf("Amazon", "Google", "Meta"), 1200, "https://leetcode.com/problems/two-sum/", "available", false),
        ProblemDto(2, "Valid Parentheses", "valid-parentheses", "Easy", "leetcode", "Stack", listOf("String", "Stack"), "Bracket Matching", 20, "valid-parentheses", 2, 9.5, listOf("Meta", "Google"), 1220, "https://leetcode.com/problems/valid-parentheses/", "available", false),
        ProblemDto(3, "Merge Two Sorted Lists", "merge-two-sorted-lists", "Easy", "leetcode", "Linked List", listOf("Linked List"), "Two Pointers", 21, "merge-two-sorted-lists", 3, 9.2, listOf("Amazon", "Apple"), 1240, "https://leetcode.com/problems/merge-two-sorted-lists/", "available", false),
        ProblemDto(4, "Best Time to Buy and Sell Stock", "best-time-to-buy-and-sell-stock", "Easy", "leetcode", "Array", listOf("Array", "Dynamic Programming"), "Greedy / Single Pass", 121, "best-time-to-buy-and-sell-stock", 4, 9.6, listOf("Amazon", "Meta"), 1260, "https://leetcode.com/problems/best-time-to-buy-and-sell-stock/", "available", false),
        ProblemDto(5, "Longest Substring Without Repeating Characters", "longest-substring-without-repeating-characters", "Medium", "leetcode", "String", listOf("Hash Table", "String", "Sliding Window"), "Sliding Window", 3, "longest-substring-without-repeating-characters", 5, 9.4, listOf("Google", "Amazon"), 1400, "https://leetcode.com/problems/longest-substring-without-repeating-characters/", "available", false),
        ProblemDto(6, "Maximum Subarray", "maximum-subarray", "Medium", "leetcode", "Array", listOf("Array", "Dynamic Programming"), "Kadane's Algorithm", 53, "maximum-subarray", 6, 9.1, listOf("Microsoft", "Apple"), 1420, "https://leetcode.com/problems/maximum-subarray/", "available", false),
        ProblemDto(7, "House Robber II", "house-robber-ii", "Medium", "leetcode", "Dynamic Programming", listOf("Dynamic Programming", "Array"), "Circular DP", 213, "house-robber-ii", 65, 9.3, listOf("Amazon", "Meta", "Google"), 1540, "https://leetcode.com/problems/house-robber-ii/", "available", false),
        ProblemDto(8, "Coin Change", "coin-change", "Medium", "leetcode", "Dynamic Programming", listOf("Dynamic Programming", "BFS"), "Unbounded Knapsack", 322, "coin-change", 8, 9.5, listOf("Amazon", "Google"), 1580, "https://leetcode.com/problems/coin-change/", "available", false),
        ProblemDto(9, "Binary Tree Level Order Traversal", "binary-tree-level-order-traversal", "Medium", "leetcode", "Tree", listOf("Tree", "BFS"), "Level Order", 102, "binary-tree-level-order-traversal", 9, 8.9, listOf("Meta", "Amazon"), 1440, "https://leetcode.com/problems/binary-tree-level-order-traversal/", "available", false),
        ProblemDto(10, "Course Schedule", "course-schedule", "Medium", "leetcode", "Graph", listOf("Graph", "Topological Sort"), "Kahn's Algorithm", 207, "course-schedule", 10, 9.3, listOf("Google", "Meta"), 1620, "https://leetcode.com/problems/course-schedule/", "available", false)
    )

    fun defaultProfile(): ProfileDto = ProfileDto(
        id = "local-user",
        username = "Local Developer",
        email = "developer@codeunlock.local",
        avatarEmoji = "⚡",
        college = "Autonomous Engineering",
        leetcodeUsername = "codeunlock_dev",
        codeforcesHandle = "codeunlock",
        lastSyncAt = "Offline Mode",
        ranking = ProfileRankingDto(
            globalRank = 42,
            collegeRank = 1,
            rankingScore = 1420.0,
            totalVerifiedSolves = 1,
            consistencyStreakDays = 1
        ),
        badges = listOf(
            ProfileBadgeDto(1, "First Solve", "discipline", "🎯", "Earned", 1.0)
        ),
        statsSummary = ProfileStatsSummaryDto(
            totalVerifiedSolves = 1,
            currentStreak = 1,
            wellbeingScore30dAvg = 94.0,
            screenTimeReductionPct = 35
        ),
        classrooms = listOf(
            ProfileClassroomDto("cr-1", "CodeUnlock Champions", "CU2026")
        )
    )

    fun defaultRankings(): List<RankingEntry> = listOf(
        RankingEntry(1, "u1", "CodeNinja", "🥷", 2850.0, 184, 45, 2),
        RankingEntry(2, "u2", "AlgoMaster", "⚡", 2620.0, 162, 30, -1),
        RankingEntry(3, "u3", "ByteWizard", "🧙", 2410.0, 140, 21, 1),
        RankingEntry(4, "u4", "FocusPioneer", "🛡️", 2180.0, 115, 14, 0),
        RankingEntry(5, "u5", "Local Developer", "⚡", 1420.0, 1, 1, 0)
    )
}

data class FeatureUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val profile: ProfileDto? = FallbackData.defaultProfile(),
    val badges: List<BadgeProgressDto> = FallbackData.defaultBadges(),
    val problems: List<ProblemDto> = FallbackData.defaultTop200Track(),
    val rankings: List<RankingEntry> = FallbackData.defaultRankings(),
    val wellbeing: WellbeingStatsDto? = null,
    val classrooms: List<ClassroomListItem> = emptyList(),
    val friends: List<FriendProfileDto> = emptyList(),
    val incoming: List<PendingFriendDto> = emptyList(),
    val outgoing: List<PendingFriendDto> = emptyList(),
    val searchResults: List<SearchResultDto> = emptyList()
)

@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: CodeUnlockRepository
) : ViewModel() {
    private val _state = MutableStateFlow(FeatureUiState())
    val state: StateFlow<FeatureUiState> = _state.asStateFlow()

    fun loadProfile() = launchLoad { current ->
        val remote = repository.getProfile().getOrNull()
        if (remote != null) current.copy(profile = remote) else current
    }

    fun loadBadges() = launchLoad { current ->
        val remote = repository.getBadges().getOrNull()?.badges.orEmpty()
        if (remote.isNotEmpty()) current.copy(badges = remote) else current
    }

    fun loadTracks() = launchLoad { current ->
        val remote = repository.listProblems().getOrNull()?.problems.orEmpty()
        if (remote.isNotEmpty()) current.copy(problems = remote) else current
    }

    fun loadRankings() = launchLoad { current ->
        val remote = repository.getGlobalRankings(limit = 50).getOrNull()?.rankings.orEmpty()
        if (remote.isNotEmpty()) current.copy(rankings = remote) else current
    }

    fun loadWellbeing() = launchLoad { current ->
        current.copy(wellbeing = repository.getWellbeingStats().getOrNull())
    }

    fun loadSocial() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        val classrooms = async { repository.listClassrooms().getOrNull()?.classrooms.orEmpty() }
        val friends = async { repository.listFriends().getOrNull()?.friends.orEmpty() }
        val pending = async { repository.listPendingFriends().getOrNull() }
        val pendingValue = pending.await()
        _state.value = _state.value.copy(
            loading = false,
            classrooms = classrooms.await(),
            friends = friends.await(),
            incoming = pendingValue?.incoming.orEmpty(),
            outgoing = pendingValue?.outgoing.orEmpty()
        )
    }

    fun createClassroom(name: String) = mutateSocial {
        repository.createClassroom(name.trim()).getOrThrow()
        "Classroom created."
    }

    fun joinClassroom(code: String) = mutateSocial {
        repository.joinClassroom(code.trim()).getOrThrow()
        "Classroom joined."
    }

    fun sendFriendRequest(username: String) = mutateSocial {
        repository.sendFriendRequest(username.trim()).getOrThrow()
        "Friend request sent."
    }

    fun acceptFriend(requesterId: String) = mutateSocial {
        repository.acceptFriendRequest(requesterId).getOrThrow()
        "Friend request accepted."
    }

    fun search(query: String) {
        if (query.trim().length < 2) return
        viewModelScope.launch {
            val results = repository.searchUsers(query.trim()).getOrNull()?.results.orEmpty()
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    private fun mutateSocial(block: suspend () -> String) {
        viewModelScope.launch {
            val message = runCatching { block() }.getOrElse { it.message ?: "Action failed." }
            _state.value = _state.value.copy(message = message)
            loadSocial()
        }
    }

    private fun launchLoad(block: suspend (FeatureUiState) -> FeatureUiState) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            _state.value = runCatching { block(_state.value).copy(loading = false) }
                .getOrElse { _state.value.copy(loading = false) }
        }
    }
}

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: FeatureViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadProfile() }
    FeatureScaffold("Profile", onBack, state.loading, state.message) {
        val profile = state.profile
        if (profile == null) EmptyText("Profile data is not available yet.") else {
            InfoCard(profile.username, profile.email)
            InfoCard("Linked accounts", "LeetCode: ${profile.leetcodeUsername ?: "not connected"}\nCodeforces: ${profile.codeforcesHandle ?: "not connected"}")
            InfoCard("Rankings", "Global: #${profile.ranking?.globalRank ?: "-"}\nCollege: #${profile.ranking?.collegeRank ?: "-"}")
            InfoCard("Stats", "Verified solves: ${profile.statsSummary.totalVerifiedSolves}\nStreak: ${profile.statsSummary.currentStreak} days\nWellbeing 30d: ${profile.statsSummary.wellbeingScore30dAvg ?: "-"}")
            InfoCard("Protection & Rules", "Daily solve target: 2 problems\nUnlock duration: 30 min per verified solve\nFocus lockdown: YouTube & Distracting apps strictly locked") {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("⚙️ Configure Settings & Apps")
                }
            }
        }
    }
}

@Composable
fun BadgesRoute(onBack: () -> Unit, viewModel: FeatureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBadges() }
    FeatureScaffold("Badges", onBack, state.loading, state.message) {
        if (!state.loading && state.badges.isEmpty()) EmptyText("No badge progress returned from Supabase.")
        state.badges.forEach { badge ->
            InfoCard("${badge.iconEmoji} ${badge.name}", badge.description) {
                LinearProgressIndicator(progress = { badge.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(if (badge.earnedAt == null) "${(badge.progress * 100).toInt()}% complete" else "Earned ${badge.earnedAt}")
            }
        }
    }
}

@Composable
fun TracksRoute(onBack: () -> Unit, onOpenProblem: (Int) -> Unit, viewModel: FeatureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadTracks() }
    FeatureScaffold("Top 200 Track", onBack, state.loading, state.message) {
        if (!state.loading && state.problems.isEmpty()) EmptyText("No problems returned from Supabase.")
        state.problems.forEach { problem ->
            InfoCard("#${problem.trackPosition} ${problem.title}", "${problem.platform} | ${problem.difficulty} | ${problem.topic}") {
                Button(onClick = { onOpenProblem(problem.id) }) { Text("Open") }
            }
        }
    }
}

@Composable
fun RankingsRoute(onBack: () -> Unit, viewModel: FeatureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadRankings() }
    FeatureScaffold("Rankings", onBack, state.loading, state.message) {
        if (!state.loading && state.rankings.isEmpty()) EmptyText("No leaderboard rows returned from Supabase.")
        state.rankings.forEach { row ->
            InfoCard("#${row.rank} ${row.avatarEmoji} ${row.username}", "Score: ${row.rankingScore.toInt()} | Solves: ${row.totalSolves} | Streak: ${row.streakDays}")
        }
    }
}

@Composable
fun WellbeingRoute(onBack: () -> Unit, viewModel: FeatureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadWellbeing() }
    
    val hasPermission = remember(context) { UsageManager.hasUsageStatsPermission(context) }
    
    FeatureScaffold("Digital Wellbeing", onBack, state.loading, state.message) {
        if (!hasPermission) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Usage Access Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "To track real-time app usage and display uninstalled apps' historical records, CodeUnlock needs the Usage Access permission.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            val localUsageMap = remember(context) { UsageManager.collectTodayUsageMap(context) }
            val totalSeconds = localUsageMap.values.fold(0L) { acc, stat -> acc + stat.durationSeconds }
            val totalMinutes = totalSeconds / 60
            val pickupCount = remember(context) { UsageManager.getTodayPickupCount(context) }
            val restrictedApps = remember(context) { BlockingSettingsStore.getRestrictedApps(context) }
            
            // Calculate restricted minutes locally for instant update
            val restrictedSeconds = localUsageMap.values
                .filter { stat: AppUsageStat -> restrictedApps.contains(stat.packageName) }
                .fold(0L) { acc, stat -> acc + stat.durationSeconds }
            val restrictedMinutes = restrictedSeconds / 60

            val blockedAttempts = remember(context) { BlockedAttemptsStore.getTodayCount(context) }
            val focusMinutes = remember(context) { FocusSessionStore.getTodayFocusMinutes(context) }
            val reclaimedMinutes = (blockedAttempts * 12) + focusMinutes
            
            // Local score formula matching PRD §8.3
            val excessivePickups = (pickupCount - 30).coerceAtLeast(0)
            val baseScore = 100 - (if (totalMinutes > 0) (restrictedMinutes.toFloat() / totalMinutes.toFloat() * 50) else 0f) - (excessivePickups * 2)
            val liveScore = baseScore.coerceIn(0f, 100f).toInt()
            
            // Live Stats Hero
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Real-Time Device Habits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallStatPill(title = "Live Score", value = "$liveScore/100", modifier = Modifier.weight(1f))
                        SmallStatPill(title = "Screen Time", value = "${totalMinutes}m", modifier = Modifier.weight(1f))
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallStatPill(title = "Restricted", value = "${restrictedMinutes}m", modifier = Modifier.weight(1f))
                        SmallStatPill(title = "Pickups Today", value = "$pickupCount", modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallStatPill(title = "Reclaimed Time", value = "${reclaimedMinutes}m", modifier = Modifier.weight(1f))
                        SmallStatPill(title = "Focus Time", value = "${focusMinutes}m", modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CodeBotAndShieldCard(
                focusMinutes = focusMinutes,
                reclaimedMinutes = reclaimedMinutes,
                blockedAttempts = blockedAttempts
            )

            Spacer(modifier = Modifier.height(10.dp))

            CodingVsDoomscrollCard(
                focusMinutes = focusMinutes,
                restrictedMinutes = restrictedMinutes.toInt()
            )
            
            Spacer(modifier = Modifier.height(10.dp))

            StrictModeSettingsCard(context = context)
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "Live App Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (localUsageMap.isEmpty()) {
                EmptyText("No foreground usage recorded on this device yet today.")
            } else {
                localUsageMap.values
                    .sortedByDescending { stat: AppUsageStat -> stat.durationSeconds }
                    .forEach { stat: AppUsageStat ->
                        val isRestricted = restrictedApps.contains(stat.packageName)
                        AppUsageRow(
                            appName = stat.appName,
                            packageName = stat.packageName,
                            durationSeconds = stat.durationSeconds,
                            totalDurationSeconds = totalSeconds,
                            category = stat.category,
                            isRestricted = isRestricted
                        )
                    }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Weekly History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        val trend = state.wellbeing?.weeklyTrend.orEmpty()
        if (trend.isEmpty()) {
            EmptyText("No historical trend data has been synced to Supabase yet.")
        } else {
            trend.forEach { entry ->
                InfoCard(
                    title = entry.date,
                    body = "Wellbeing Score: ${entry.score.toInt()}/100\nScreen Time: ${entry.screenTime} minutes"
                )
            }
        }
    }
}

@Composable
private fun SmallStatPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(durationSeconds: Long): String {
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
private fun AppUsageRow(
    appName: String,
    packageName: String,
    durationSeconds: Long,
    totalDurationSeconds: Long,
    category: String,
    isRestricted: Boolean
) {
    val isUninstalled = appName.startsWith("[Uninstalled]")
    val cleanAppName = if (isUninstalled) appName.replace("[Uninstalled]", "").trim() else appName
    
    val categoryColor = when (category.lowercase()) {
        "social" -> Color(0xFFAB47BC) // Purple
        "entertainment" -> Color(0xFFFF7043) // Deep Orange
        "productivity" -> Color(0xFF66BB6A) // Green
        else -> Color(0xFF78909C) // Slate Gray
    }
    
    val percent = if (totalDurationSeconds > 0) (durationSeconds.toFloat() / totalDurationSeconds.toFloat()) else 0f
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isUninstalled) Color.Gray.copy(alpha = 0.15f) else categoryColor.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val emoji = when {
                            packageName.contains("instagram") -> "📷"
                            packageName.contains("youtube") -> "▶️"
                            packageName.contains("twitter") || packageName.contains("x") -> "🐦"
                            packageName.contains("whatsapp") -> "💬"
                            packageName.contains("facebook") -> "👥"
                            packageName.contains("spotify") -> "🎧"
                            packageName.contains("reddit") -> "🤖"
                            packageName.contains("chrome") -> "🌐"
                            packageName.contains("netflix") -> "🎬"
                            packageName.contains("telegram") -> "✈️"
                            packageName.contains("discord") -> "🎮"
                            packageName.contains("linkedin") -> "💼"
                            isUninstalled -> "🗑️"
                            else -> "📱"
                        }
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = cleanAppName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isRestricted) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Restricted",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDuration(durationSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUninstalled) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color.Red.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Uninstalled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Red,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(
                                    color = categoryColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = category.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            
            LinearProgressIndicator(
                progress = { percent },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun SocialRoute(onBack: () -> Unit, viewModel: FeatureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var classroomName by rememberText()
    var joinCode by rememberText()
    var friendUsername by rememberText()
    var searchQuery by rememberText()
    LaunchedEffect(Unit) { viewModel.loadSocial() }
    FeatureScaffold("Social", onBack, state.loading, state.message) {
        InputAction("Create classroom", classroomName, { classroomName = it }, "Create") { viewModel.createClassroom(classroomName) }
        InputAction("Join classroom code", joinCode, { joinCode = it }, "Join") { viewModel.joinClassroom(joinCode) }
        state.classrooms.forEach { InfoCard(it.name, "Code: ${it.code} | Members: ${it.memberCount}") }

        Spacer(Modifier.height(8.dp))
        InputAction("Friend username", friendUsername, { friendUsername = it }, "Send") { viewModel.sendFriendRequest(friendUsername) }
        state.friends.forEach { InfoCard(it.username, "Accepted friend") }
        state.incoming.forEach { pending ->
            InfoCard(pending.username, "Incoming request") {
                pending.requesterId?.let { Button(onClick = { viewModel.acceptFriend(it) }) { Text("Accept") } }
            }
        }
        state.outgoing.forEach { InfoCard(it.username, "Outgoing request") }

        Spacer(Modifier.height(8.dp))
        InputAction("Search users", searchQuery, { searchQuery = it }, "Search") { viewModel.search(searchQuery) }
        state.searchResults.forEach { InfoCard(it.username, "LeetCode: ${it.leetcodeUsername ?: "-"} | Codeforces: ${it.codeforcesHandle ?: "-"}") }
    }
}

@Composable
private fun FeatureScaffold(
    title: String,
    onBack: () -> Unit,
    loading: Boolean,
    message: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
            if (message != null && !message.contains("ConnectException", ignoreCase = true) && !message.contains("failed to connect", ignoreCase = true) && !message.contains("Unable to load", ignoreCase = true)) {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            content()
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, extra: @Composable ColumnScope.() -> Unit = {}) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            extra()
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InputAction(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    button: String,
    onClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = onClick, enabled = value.isNotBlank()) { Text(button) }
    }
}

@Composable
private fun rememberText() = androidx.compose.runtime.remember { mutableStateOf("") }

@Composable
private fun StrictModeSettingsCard(context: android.content.Context) {
    var isStrictMode by remember { mutableStateOf(StrictModeStore.isStrictModeEnabled(context)) }
    var isPreventUninstall by remember { mutableStateOf(CodeUnlockDeviceAdminReceiver.isDeviceAdminActive(context)) }
    var isSettingsGuard by remember { mutableStateOf(StrictModeStore.isSettingsGuardEnabled(context)) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "🛡️ Strict Mode & Anti-Bypass",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Regain-grade unbypassable focus protection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Row 1: Strict Lock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Strict Focus Lock", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Locks apps until daily coding problem is verified solved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isStrictMode,
                    onCheckedChange = { checked ->
                        isStrictMode = checked
                        StrictModeStore.setStrictModeEnabled(context, checked)
                    }
                )
            }

            // Row 2: Prevent Uninstall
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Anti-Uninstall Protection", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isPreventUninstall) "Device Admin active (uninstalls blocked)"
                        else "Enable Device Admin to prevent uninstalling",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPreventUninstall,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val intent = CodeUnlockDeviceAdminReceiver.createEnableAdminIntent(context)
                            context.startActivity(intent)
                        } else {
                            val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                            dpm?.removeActiveAdmin(CodeUnlockDeviceAdminReceiver.getComponentName(context))
                            isPreventUninstall = CodeUnlockDeviceAdminReceiver.isDeviceAdminActive(context)
                        }
                    }
                )
            }

            // Row 3: Settings Guard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Settings Guard", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Blocks tampering with App Info, Force Stop, or permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSettingsGuard,
                    onCheckedChange = { checked ->
                        isSettingsGuard = checked
                        StrictModeStore.setSettingsGuardEnabled(context, checked)
                    }
                )
            }
        }
    }
}

@Composable
private fun CodeBotAndShieldCard(
    focusMinutes: Int,
    reclaimedMinutes: Int,
    blockedAttempts: Int
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val mascotEmoji = when {
                        focusMinutes >= 25 || reclaimedMinutes >= 30 -> "🤖⚡"
                        focusMinutes > 0 -> "🤖🧠"
                        else -> "🤖"
                    }
                    Text(text = mascotEmoji, fontSize = 26.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CodeBot Companion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = when {
                        reclaimedMinutes >= 45 -> "Unstoppable! You've reclaimed ${reclaimedMinutes}m from doomscrolling."
                        focusMinutes >= 25 -> "Peak focus achieved! Keep up the coding momentum."
                        blockedAttempts > 0 -> "Blocked $blockedAttempts distraction attempts today. Stay strong!"
                        else -> "Ready to code? Solve a Top 200 problem to unlock screen time."
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Streak Shield Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛡️", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "Weekly Streak Shield Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Protects your streak if you miss a day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ARMED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodingVsDoomscrollCard(
    focusMinutes: Int,
    restrictedMinutes: Int
) {
    val totalMinutes = focusMinutes + restrictedMinutes
    val codingFraction = if (totalMinutes > 0) (focusMinutes.toFloat() / totalMinutes.toFloat()) else 0.5f
    val codingPct = (codingFraction * 100).toInt()
    val doomscrollPct = 100 - codingPct

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coding vs. Doomscroll Ratio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${codingPct}% Focus",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            // Dual-color progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE57373))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = codingFraction.coerceIn(0f, 1f))
                        .background(Color(0xFF66BB6A))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🟢 Coding Focus: ${focusMinutes}m ($codingPct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "🔴 Distraction: ${restrictedMinutes}m ($doomscrollPct%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}



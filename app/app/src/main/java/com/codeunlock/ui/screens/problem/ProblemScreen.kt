package com.codeunlock.ui.screens.problem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.codeunlock.api.dto.DailyProgressDto
import com.codeunlock.api.dto.NextProblemResponse
import com.codeunlock.api.dto.ProblemDto
import com.codeunlock.api.dto.TrackProgressSummary
import com.codeunlock.api.dto.UnlockSessionDto
import com.codeunlock.api.dto.VerifySolveResponse
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.session.SessionManager
import com.codeunlock.data.unlock.UnlockSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun ProblemRoute(
    onBack: () -> Unit,
    onVerified: () -> Unit,
    viewModel: ProblemViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(state.message) {
        state.message?.takeIf { it.isNotBlank() }?.let { snackbarHostState.showSnackbar(it) }
    }

    ProblemScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenProblem = {
            state.problemUrl?.let(uriHandler::openUri)
        },
        onVerify = viewModel::verifySolve
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemScreen(
    state: ProblemUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenProblem: () -> Unit,
    onVerify: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Solve to Unlock",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (state.problem != null) {
                            Text(
                                text = "Top 200 interview track",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                LoadingState(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                )
            }

            state.problem == null -> {
                EmptyState(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    message = state.message ?: "No problem available right now.",
                    onBack = onBack
                )
            }

            else -> {
                val problem = state.problem

                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProblemHeroCard(
                        title = problem.title,
                        difficulty = problem.difficulty,
                        platform = problem.platform,
                        trackPosition = problem.trackPosition,
                        progressText = state.progressText,
                        solvedFraction = state.solvedFraction
                    )

                    ProblemMetaCard(problem = problem)

                    DescriptionCard(description = state.problemDescription)

                    if (state.isVerified && state.verificationResult != null) {
                        VerifiedRewardCard(result = state.verificationResult)
                    } else {
                        VerificationFlowCard(
                            isVerifying = state.isVerifying,
                            verifyButtonEnabled = !state.isVerifying,
                            onVerify = onVerify
                        )
                    }

                    ActionButtons(
                        canOpenProblem = !state.problemUrl.isNullOrBlank(),
                        isVerified = state.isVerified,
                        onOpenProblem = onOpenProblem,
                        onVerify = onVerify
                    )

                    if (state.error != null) {
                        ErrorCard(message = state.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading your next problem...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    message: String,
    onBack: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Nothing to verify",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onBack) {
                    Text("Go back")
                }
            }
        }
    }
}

@Composable
private fun ProblemHeroCard(
    title: String,
    difficulty: String,
    platform: String,
    trackPosition: Int,
    progressText: String,
    solvedFraction: Float
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DifficultyBadge(difficulty = difficulty)
                        MetaBadge(text = platform.replaceFirstChar { it.uppercase() })
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "#$trackPosition",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                LinearProgressIndicator(
                    progress = { solvedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
                )
            }
        }
    }
}

@Composable
private fun ProblemMetaCard(problem: ProblemDto) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Problem details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPill(
                    modifier = Modifier.weight(1f),
                    label = "Pattern",
                    value = problem.pattern.ifBlank { "Interview" }
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    label = "Topic",
                    value = problem.topic.ifBlank { "General" }
                )
            }

            if (problem.topics.isNotEmpty()) {
                FlowingTags(tags = problem.topics)
            }

            if (problem.companies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Common at",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowingTags(tags = problem.companies.take(6))
                }
            }
        }
    }
}

@Composable
private fun DescriptionCard(description: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "How this works",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerificationFlowCard(
    isVerifying: Boolean,
    verifyButtonEnabled: Boolean,
    onVerify: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Verification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = if (isVerifying) Icons.Default.HourglassTop else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isVerifying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Submit on the platform, then verify here",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "CodeUnlock checks your recent accepted submissions through the backend before granting unlock time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedButton(
                onClick = onVerify,
                enabled = verifyButtonEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (isVerifying) "Verifying..." else "Verify solve")
            }
        }
    }
}

@Composable
private fun VerifiedRewardCard(result: VerifySolveResponse) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Column {
                    Text(
                        text = "Verified successfully",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your reward has been granted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RewardStatGrid(
                unlockMinutes = result.unlockGrantedMinutes,
                dailyProgress = result.dailyProgress,
                unlockSession = result.unlockSession
            )
        }
    }
}

@Composable
private fun RewardStatGrid(
    unlockMinutes: Int,
    dailyProgress: DailyProgressDto,
    unlockSession: UnlockSessionDto?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatPill(
                modifier = Modifier.weight(1f),
                label = "Unlock earned",
                value = "$unlockMinutes min"
            )
            StatPill(
                modifier = Modifier.weight(1f),
                label = "Solved today",
                value = "${dailyProgress.problemsSolved}/${dailyProgress.dailyTarget}"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatPill(
                modifier = Modifier.weight(1f),
                label = "Minutes used",
                value = "${dailyProgress.unlockMinutesUsed}"
            )
            StatPill(
                modifier = Modifier.weight(1f),
                label = "Expires",
                value = unlockSession?.expiresAt?.takeLast(8) ?: "Active"
            )
        }
    }
}

@Composable
private fun ActionButtons(
    canOpenProblem: Boolean,
    isVerified: Boolean,
    onOpenProblem: () -> Unit,
    onVerify: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onOpenProblem,
            modifier = Modifier.fillMaxWidth(),
            enabled = canOpenProblem
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Open problem")
        }

        OutlinedButton(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isVerified
        ) {
            Text(if (isVerified) "Already verified" else "Verify after solving")
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: String) {
    val background = when (difficulty.lowercase()) {
        "easy" -> Color(0xFF1E7F4F)
        "hard" -> Color(0xFFB3261E)
        else -> Color(0xFFF59E0B)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background.copy(alpha = 0.18f)
    ) {
        Text(
            text = difficulty,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = background
        )
    }
}

@Composable
private fun MetaBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FlowingTags(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var row = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        tags.forEachIndexed { index, tag ->
            row.add(tag)
            if (row.size == 2 || index == tags.lastIndex) {
                rows.add(row.toList())
                row = mutableListOf()
            }
        }

        rows.forEach { rowTags ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(tag) }
                    )
                }
            }
        }
    }
}

@Immutable
data class ProblemUiState(
    val isLoading: Boolean = true,
    val isVerifying: Boolean = false,
    val isVerified: Boolean = false,
    val problem: ProblemDto? = null,
    val progressText: String = "0 / 200 solved",
    val solvedFraction: Float = 0f,
    val problemUrl: String? = null,
    val problemDescription: String = "",
    val verificationResult: VerifySolveResponse? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProblemViewModel @Inject constructor(
    private val repository: CodeUnlockRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val problemIdArg: Int = savedStateHandle["problemId"] ?: 1

    private val _uiState = MutableStateFlow(
        ProblemUiState(
            isLoading = true
        )
    )
    val uiState: StateFlow<ProblemUiState> = _uiState.asStateFlow()

    init {
        loadProblem()
    }

    fun verifySolve() {
        val problem = _uiState.value.problem ?: return
        if (_uiState.value.isVerifying || _uiState.value.isVerified) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isVerifying = true,
                    error = null,
                    message = null
                )
            }

            repository.verifySolve(problem.id)
                .onSuccess { result ->
                    if (result.verified) {
                        UnlockSessionStore.cacheServerUnlock(context, result.unlockSession)
                    }

                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            isVerified = result.verified,
                            verificationResult = result,
                            message = if (result.verified) {
                                "Verified successfully. ${result.unlockGrantedMinutes} minutes granted."
                            } else {
                                "Verification finished."
                            },
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            error = throwable.message ?: "Verification failed.",
                            message = throwable.message ?: "Verification failed."
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadProblem() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    message = null
                )
            }

            if (problemIdArg > 1) {
                val listResult = repository.listProblems()
                val specificProblem = listResult.getOrNull()?.problems?.find { it.id == problemIdArg }
                if (specificProblem != null) {
                    val progressResult = repository.getTrackProgress().getOrNull()
                    val solved = progressResult?.solved ?: 0
                    val total = progressResult?.totalProblems ?: 200
                    val response = NextProblemResponse(
                        problem = specificProblem,
                        progress = TrackProgressSummary(
                            solvedCount = solved,
                            totalCount = total,
                            currentPosition = specificProblem.trackPosition
                        ),
                        message = null
                    )
                    applyProblemResponse(response)
                    return@launch
                }
            }

            repository.getNextProblem()
                .onSuccess { response ->
                    applyProblemResponse(response)
                }
                .onFailure { throwable ->
                    _uiState.value = ProblemUiState(
                        isLoading = false,
                        problem = null,
                        progressText = "Track progress unavailable",
                        solvedFraction = 0f,
                        problemUrl = null,
                        problemDescription = "",
                        message = null,
                        error = throwable.message ?: "Unable to load the next problem."
                    )
                }
        }
    }

    private fun applyProblemResponse(response: NextProblemResponse) {
        val problem = response.problem
        if (problem == null) {
            _uiState.value = ProblemUiState(
                isLoading = false,
                problem = null,
                progressText = "${response.progress.solvedCount} / ${response.progress.totalCount} solved",
                solvedFraction = response.progress.solvedCount.toFloat() / response.progress.totalCount.coerceAtLeast(1).toFloat(),
                problemUrl = null,
                problemDescription = "",
                message = response.message ?: "No more problems available in the current track."
            )
            return
        }
        val progress = response.progress
        val solved = progress.solvedCount.coerceAtLeast(0)
        val total = progress.totalCount.coerceAtLeast(1)

        _uiState.value = ProblemUiState(
            isLoading = false,
            problem = problem,
            progressText = "$solved / $total solved",
            solvedFraction = solved.toFloat() / total.toFloat(),
            problemUrl = problem.url ?: buildDefaultProblemUrl(problem),
            problemDescription = verificationDescription(problem),
            message = response.message
        )
    }

    private fun unusedFallbackProblem(problemId: Int): ProblemDto {
        return ProblemDto(
            id = problemId,
            title = "House Robber II",
            slug = "house-robber-ii",
            difficulty = "Medium",
            platform = "leetcode",
            topic = "Dynamic Programming",
            topics = listOf("Dynamic Programming", "Array"),
            pattern = "Circular DP",
            platformId = 213,
            platformSlug = "house-robber-ii",
            trackPosition = 65,
            frequencyScore = 9.3,
            companies = listOf("Amazon", "Meta", "Google"),
            eloRating = 1540,
            url = "https://leetcode.com/problems/house-robber-ii/",
            userStatus = "available",
            verified = false
        )
    }

    private fun verificationDescription(problem: ProblemDto): String {
        val source = problem.platform.replaceFirstChar { it.uppercase() }
        return buildString {
            append("Open the problem on ")
            append(source)
            append(", solve it externally, then come back here and tap verify. ")
            append("CodeUnlock checks your latest accepted submission through the backend and grants unlock time only after successful verification.")
        }
    }

    private fun buildDefaultProblemUrl(problem: ProblemDto): String {
        return when (problem.platform.lowercase()) {
            "leetcode" -> "https://leetcode.com/problems/${problem.platformSlug.ifBlank { problem.slug }}/"
            "codeforces" -> "https://codeforces.com/problemset"
            else -> "https://leetcode.com/problems/${problem.slug}/"
        }
    }

    private fun unusedFallbackVerifyResult(problemId: Int): VerifySolveResponse {
        return VerifySolveResponse(
            verified = true,
            unlockGrantedMinutes = 30,
            dailyProgress = DailyProgressDto(
                problemsSolved = 4,
                dailyTarget = 5,
                targetMet = false,
                unlockMinutesEarned = 120,
                unlockMinutesUsed = 45
            ),
            unlockSession = UnlockSessionDto(
                id = "local-unused-$problemId",
                appPackage = null,
                unlockType = "coding",
                durationMinutes = 30,
                grantedAt = "2026-04-22T10:00:00Z",
                expiresAt = "2026-04-22T10:30:00Z",
                isActive = true
            )
        )
    }
}

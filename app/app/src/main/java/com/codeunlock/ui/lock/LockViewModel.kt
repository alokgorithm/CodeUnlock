package com.codeunlock.ui.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.notification.NotificationHelper
import com.codeunlock.data.session.SessionManager
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.ui.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LockUiState(
    val isProcessing: Boolean = false,
    val emergencyGranted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val repository: CodeUnlockRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    // FIX: Expose the real next problem route so LockActivity button works.
    // Original hardcoded DEFAULT_PROBLEM_ID = 1 which always sent the user
    // to problem #1 regardless of their track position.
    private val _nextProblemRoute = MutableStateFlow<String?>(null)
    val nextProblemRoute: StateFlow<String?> = _nextProblemRoute.asStateFlow()

    /** Pre-fetch the user's actual next problem. */
    fun loadNextProblem() {
        viewModelScope.launch {
            repository.getNextProblem()
                .onSuccess { response ->
                    val problemId = response.problem?.id
                    _nextProblemRoute.value = if (problemId != null) {
                        AppRoute.Problem.createRoute(problemId)
                    } else {
                        AppRoute.Dashboard.route
                    }
                }
                .onFailure {
                    _nextProblemRoute.value = AppRoute.Dashboard.route
                }
        }
    }

    /** Emergency unlock requires a backend-issued session. */
    fun requestEmergencyUnlock(appPackage: String?, onResult: (Boolean) -> Unit) {
        if (_uiState.value.isProcessing) return

        val session = sessionManager.currentState()

        if (!session.hasRealSession) {
            _uiState.value = LockUiState(
                isProcessing = false,
                emergencyGranted = false,
                errorMessage = "Sign in is required before emergency unlock.",
            )
            onResult(false)
            return
        }

        // Live mode — call backend
        viewModelScope.launch {
            _uiState.value = LockUiState(isProcessing = true)

            repository.emergencyUnlock(appPackage)
                .onSuccess { response ->
                    UnlockSessionStore.cacheServerUnlock(context, response.unlockSession)
                    // PRD §6.3: notification on unlock granted
                    NotificationHelper.showUnlockGranted(
                        context = context,
                        minutes = response.unlockSession.durationMinutes,
                    )
                    _uiState.value = LockUiState(isProcessing = false, emergencyGranted = true)
                    onResult(true)
                }
                .onFailure { throwable ->
                    _uiState.value = LockUiState(
                        isProcessing  = false,
                        emergencyGranted = false,
                        errorMessage  = throwable.message ?: "Emergency unlock failed.",
                    )
                    onResult(false)
                }
        }
    }
}

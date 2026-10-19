package com.codeunlock.ui.screens.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeunlock.api.dto.UpdateSettingsRequest
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionSetupUiState(
    val isSaving: Boolean = false,
    val isSyncedRemotely: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class OnboardingSettingsInput(
    val restrictedApps: Set<String>,
    val usageMode: String,
    val dailySolveTarget: Int,
    val emergencyUnlockEnabled: Boolean,
    val freeUnlockEnabled: Boolean
)

sealed interface PermissionSetupSaveResult {
    data class Success(
        val syncedRemotely: Boolean,
        val message: String
    ) : PermissionSetupSaveResult

    data class Failure(
        val message: String
    ) : PermissionSetupSaveResult
}

@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    private val repository: CodeUnlockRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionSetupUiState())
    val uiState: StateFlow<PermissionSetupUiState> = _uiState.asStateFlow()

    fun syncOnboardingSettings(
        input: OnboardingSettingsInput,
        onResult: (PermissionSetupSaveResult) -> Unit = {}
    ) {
        if (_uiState.value.isSaving) return

        val validationError = validate(input)
        if (validationError != null) {
            val result = PermissionSetupSaveResult.Failure(validationError)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSyncedRemotely = false,
                    statusMessage = null,
                    errorMessage = validationError
                )
            }
            onResult(result)
            return
        }

        val session = sessionManager.currentState()

        if (!session.hasRealSession) {
            val message = "Saved locally. Backend sync requires an authenticated session."

            val result = PermissionSetupSaveResult.Success(
                syncedRemotely = false,
                message = message
            )

            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSyncedRemotely = false,
                    statusMessage = message,
                    errorMessage = null
                )
            }
            onResult(result)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    statusMessage = null,
                    errorMessage = null
                )
            }

            val request = buildRequest(input)
            val result = repository.updateSettings(request)

            result
                .onSuccess { response ->
                    val message = response.message.ifBlank {
                        if (response.applied) {
                            "Settings synced successfully."
                        } else {
                            "Settings saved. Some changes will apply later."
                        }
                    }

                    val success = PermissionSetupSaveResult.Success(
                        syncedRemotely = true,
                        message = message
                    )

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSyncedRemotely = true,
                            statusMessage = message,
                            errorMessage = null
                        )
                    }
                    onResult(success)
                }
                .onFailure { throwable ->
                    val message = buildRemoteFailureMessage(throwable.message)
                    val failure = PermissionSetupSaveResult.Failure(message)

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSyncedRemotely = false,
                            statusMessage = message,
                            errorMessage = null
                        )
                    }
                    onResult(failure)
                }
        }
    }

    suspend fun savePlatformLinks(leetcode: String, codeforces: String) {
        if (leetcode.isBlank() && codeforces.isBlank()) return
        val request = com.codeunlock.api.dto.UpdateProfileRequest(
            leetcodeUsername = leetcode.takeIf { it.isNotBlank() },
            codeforcesHandle = codeforces.takeIf { it.isNotBlank() }
        )
        // Fire and forget — never crash onboarding
        runCatching { repository.updateProfile(request) }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    private fun validate(input: OnboardingSettingsInput): String? {
        return when {
            input.restrictedApps.isEmpty() ->
                "Select at least one app to restrict."

            input.dailySolveTarget < MIN_DAILY_SOLVE_TARGET ->
                "Daily solve target must be at least $MIN_DAILY_SOLVE_TARGET."

            input.usageMode.isBlank() ->
                "Select a valid usage mode."

            else -> null
        }
    }

    private fun buildRequest(input: OnboardingSettingsInput): UpdateSettingsRequest {
        val perAppLimitMinutes = resolvePerAppLimitMinutes(input.usageMode)
        val perAppLimits = input.restrictedApps.associateWith { perAppLimitMinutes }

        return UpdateSettingsRequest(
            usageMode = input.usageMode,
            dailySolveTarget = input.dailySolveTarget,
            restrictedApps = input.restrictedApps.toList(),
            perAppLimits = perAppLimits,
            emergencyUnlockEnabled = input.emergencyUnlockEnabled,
            freeUnlockEnabled = input.freeUnlockEnabled
        )
    }

    private fun resolvePerAppLimitMinutes(usageMode: String): Int {
        return when (usageMode.lowercase()) {
            "relaxed" -> 420
            "focused" -> 240
            "hardcore" -> 120
            "custom" -> 60
            else -> 240
        }
    }

    private fun buildRemoteFailureMessage(rawMessage: String?): String {
        val message = rawMessage.orEmpty()

        return when {
            message.contains("Settings not found", ignoreCase = true) ->
                "Saved locally. Remote profile settings are not ready yet."

            message.contains("UNAUTHORIZED", ignoreCase = true) ->
                "Saved locally. Your session needs to be refreshed before backend sync."

            message.isNotBlank() ->
                "Saved locally. Remote sync pending: $message"

            else ->
                "Saved locally. Remote sync could not be completed right now."
        }
    }

    companion object {
        private const val MIN_DAILY_SOLVE_TARGET = 2
    }
}

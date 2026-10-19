package com.codeunlock.ui.navigation

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeunlock.BuildConfig
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.session.SessionManager
import com.codeunlock.data.session.SessionState
import com.codeunlock.data.settings.BlockingSettingsStore
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class OnboardingState(
    val isResolved: Boolean = false,
    val isComplete: Boolean = false
)

data class AuthUiState(
    val isSigningIn: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class CodeUnlockNavViewModel @Inject constructor(
    val sessionManager: SessionManager,
    private val repository: CodeUnlockRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = sessionManager.sessionState

    private val _onboardingState = MutableStateFlow(
        OnboardingState(
            isResolved = true,
            isComplete = readOnboardingComplete()
        )
    )
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    /**
     * Offline-first start destination:
     * - First launch → Permissions (no login required)
     * - Onboarding complete → Dashboard
     * Login is optional and accessible from Dashboard.
     */
    fun resolveStartDestination(initialRoute: String?): String {
        if (!initialRoute.isNullOrBlank()) return initialRoute

        val onboarding = onboardingState.value

        return when {
            onboarding.isResolved && onboarding.isComplete -> AppRoute.Dashboard.route
            else -> AppRoute.Permissions.route
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_authUiState.value.isSigningIn) return
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _authUiState.value = AuthUiState(
                message = "Google OAuth client ID is not configured for this build."
            )
            return
        }

        viewModelScope.launch {
            _authUiState.value = AuthUiState(isSigningIn = true, message = "Opening Google sign-in…")

            val idToken = try {
                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setNonce(UUID.randomUUID().toString())
                    .build()

                val credential = credentialManager.getCredential(
                    context = activity,
                    request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                ).credential

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    _authUiState.value = AuthUiState(message = "Google credential was not returned.")
                    return@launch
                }

                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (e: Exception) {
                _authUiState.value = AuthUiState(
                    message = e.message ?: "Google sign-in was cancelled."
                )
                return@launch
            }

            // Step 2: Exchange with Supabase
            _authUiState.value = AuthUiState(isSigningIn = true, message = "Connecting to server…")

            val session = try {
                exchangeGoogleTokenWithSupabase(idToken)
            } catch (e: Exception) {
                _authUiState.value = AuthUiState(
                    message = e.message ?: "Could not connect to server. Try again."
                )
                return@launch
            }

            sessionManager.saveSession(
                accessToken = session.accessToken,
                email = session.email
            )

            // Setup auth in background — don't block login if worker API is slow
            _authUiState.value = AuthUiState(isSigningIn = true, message = "Setting up profile…")
            runCatching { repository.setupAuth() }

            refreshOnboardingState()
            _authUiState.value = AuthUiState()
        }
    }

    fun markOnboardingComplete() {
        refreshOnboardingState(isCompleteOverride = true)
    }

    fun logout() {
        sessionManager.clearSession()
        refreshOnboardingState()
    }

    fun refreshOnboardingState() {
        refreshOnboardingState(isCompleteOverride = null)
    }

    private fun refreshOnboardingState(isCompleteOverride: Boolean?) {
        _onboardingState.value = OnboardingState(
            isResolved = true,
            isComplete = isCompleteOverride ?: readOnboardingComplete()
        )
    }

    private fun readOnboardingComplete(): Boolean {
        return BlockingSettingsStore.isOnboardingComplete(context)
    }

    private suspend fun exchangeGoogleTokenWithSupabase(idToken: String): SupabaseSession {
        return withContext(Dispatchers.IO) {
            val googleDns = object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return try {
                        java.net.InetAddress.getAllByName(hostname).toList()
                    } catch (_: Exception) {
                        try {
                            java.net.InetAddress.getAllByName(hostname).toList()
                        } catch (_: Exception) {
                            okhttp3.Dns.SYSTEM.lookup(hostname)
                        }
                    }
                }
            }

            val client = OkHttpClient.Builder()
                .dns(googleDns)
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val body = JSONObject()
                .put("provider", "google")
                .put("id_token", idToken)
                .toString()
                .toRequestBody("application/json".toMediaType())

            val maxRetries = 2
            var lastError: String = "Connection timed out"

            for (attempt in 0..maxRetries) {
                if (attempt > 0) {
                    withContext(Dispatchers.Main) {
                        _authUiState.value = AuthUiState(
                            isSigningIn = true,
                            message = "Retrying… (attempt ${attempt + 1}/${maxRetries + 1})"
                        )
                    }
                    kotlinx.coroutines.delay((attempt * 3000).toLong())
                }

                try {
                    val request = okhttp3.Request.Builder()
                        .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=id_token")
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()

                        if (!response.isSuccessful) {
                            val json = runCatching { JSONObject(raw) }.getOrNull()
                            val errorMsg = json?.optString("msg")?.takeIf { it.isNotBlank() }
                                ?: json?.optString("error_description")?.takeIf { it.isNotBlank() }
                                ?: json?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: "Auth failed (HTTP ${response.code})"

                            if (response.code in 400..499) {
                                error(errorMsg)
                            }
                            lastError = errorMsg
                            return@use
                        }

                        val json = JSONObject(raw)
                        val accessToken = json.optString("access_token")
                        if (accessToken.isBlank()) error("Server did not return a session token.")

                        val user = json.optJSONObject("user")
                        return@withContext SupabaseSession(
                            accessToken = accessToken,
                            email = user?.optString("email")?.takeIf { it.isNotBlank() }
                        )
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    lastError = "Connection timed out. Check your internet."
                } catch (e: java.net.UnknownHostException) {
                    lastError = "Cannot reach server. Check your WiFi/data."
                } catch (e: javax.net.ssl.SSLException) {
                    lastError = "Secure connection failed. Try a different network."
                } catch (e: java.io.IOException) {
                    lastError = "Network error: ${e.message ?: "connection lost"}"
                } catch (e: IllegalStateException) {
                    throw e
                }
            }

            error(lastError)
        }
    }

    private data class SupabaseSession(
        val accessToken: String,
        val email: String?
    )
}

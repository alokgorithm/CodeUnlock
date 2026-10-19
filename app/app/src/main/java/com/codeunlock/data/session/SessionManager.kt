package com.codeunlock.data.session

import android.content.Context
import android.content.SharedPreferences
import com.codeunlock.api.auth.TokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) : TokenProvider {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedAccessToken: String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    private val _sessionState = MutableStateFlow(
        SessionState(
            accessToken = cachedAccessToken,
            email = prefs.getString(KEY_EMAIL, null)
        )
    )

    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override fun getAccessToken(): String? = cachedAccessToken

    fun currentState(): SessionState = _sessionState.value

    fun saveSession(
        accessToken: String,
        email: String? = null
    ) {
        cachedAccessToken = accessToken

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_EMAIL, email)
            .apply()

        _sessionState.value = SessionState(
            accessToken = accessToken,
            email = email
        )
    }

    fun clearSession() {
        cachedAccessToken = null

        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_EMAIL)
            .apply()

        _sessionState.value = SessionState()
    }

    companion object {
        private const val PREFS_NAME = "codeunlock_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EMAIL = "email"
    }
}

package com.codeunlock.data.session

import com.codeunlock.api.auth.AuthFailureHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears local session state when the API layer detects an auth failure.
 */
@Singleton
class SessionAuthFailureHandler @Inject constructor(
    private val sessionManager: SessionManager
) : AuthFailureHandler {

    override fun onAuthFailure() {
        sessionManager.clearSession()
    }
}

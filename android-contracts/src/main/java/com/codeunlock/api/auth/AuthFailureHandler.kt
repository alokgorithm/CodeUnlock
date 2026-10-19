package com.codeunlock.api.auth

/**
 * Callback invoked when the API layer detects an authentication failure
 * (for example, an expired or invalid access token).
 *
 * The app module can implement this to clear local session state and
 * redirect the user back to the login flow.
 */
fun interface AuthFailureHandler {
    fun onAuthFailure()
}

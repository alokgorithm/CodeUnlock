package com.codeunlock.data.session

/**
 * Represents the current authentication/session state for the app.
 *
 * The app requires a real JWT-backed session for product flows.
 */
data class SessionState(
    val accessToken: String? = null,
    val email: String? = null
) {
    val isAuthenticated: Boolean
        get() = !accessToken.isNullOrBlank()

    val hasRealSession: Boolean
        get() = !accessToken.isNullOrBlank()

    companion object {
        val LoggedOut = SessionState()
    }
}

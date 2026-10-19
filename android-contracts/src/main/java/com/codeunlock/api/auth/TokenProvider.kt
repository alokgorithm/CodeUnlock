package com.codeunlock.api.auth

/**
 * Provides the latest access token for authenticated API requests.
 *
 * Implementations should return a synchronously accessible cached token,
 * because networking interceptors cannot suspend while building requests.
 */
interface TokenProvider {
    fun getAccessToken(): String?
}

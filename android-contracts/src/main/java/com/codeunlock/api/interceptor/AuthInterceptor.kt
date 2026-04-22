package com.codeunlock.api.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Injects the Supabase JWT Bearer token into every request.
 *
 * Usage: Provide a lambda that returns the current access token
 * from your auth state (e.g., Supabase session, DataStore, etc.)
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()
        } else {
            chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .build()
        }
        return chain.proceed(request)
    }
}

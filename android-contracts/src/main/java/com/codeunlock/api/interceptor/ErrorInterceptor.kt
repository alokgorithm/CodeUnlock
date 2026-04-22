package com.codeunlock.api.interceptor

import android.util.Log
import com.codeunlock.api.dto.ApiError
import com.codeunlock.api.dto.ApiResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Intercepts error responses and logs them.
 * Also handles token refresh scenarios (401 → trigger re-auth).
 *
 * Attach a listener to react to auth failures (e.g., redirect to login).
 */
class ErrorInterceptor(
    private val onAuthFailure: (() -> Unit)? = null
) : Interceptor {

    companion object {
        private const val TAG = "CodeUnlockAPI"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        when (response.code) {
            401 -> {
                Log.w(TAG, "401 Unauthorized: ${request.url}")
                onAuthFailure?.invoke()
            }
            429 -> {
                // Rate limited — extract retry-after info from body
                val body = response.peekBody(1024).string()
                Log.w(TAG, "429 Rate Limited: ${request.url} — $body")
            }
            in 500..599 -> {
                Log.e(TAG, "Server error ${response.code}: ${request.url}")
            }
        }

        return response
    }
}

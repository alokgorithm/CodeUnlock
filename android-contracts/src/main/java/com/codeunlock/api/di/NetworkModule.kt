package com.codeunlock.api.di

import com.codeunlock.api.auth.AuthFailureHandler
import com.codeunlock.api.auth.TokenProvider
import com.codeunlock.api.interceptor.AuthInterceptor
import com.codeunlock.api.interceptor.ErrorInterceptor
import com.codeunlock.api.service.CodeUnlockApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt DI Module for CodeUnlock API.
 *
 * Provides:
 *   - OkHttpClient (with auth + error interceptors)
 *   - Retrofit instance (base URL: codeunlock-api.arka-cmd.workers.dev)
 *   - CodeUnlockApiService (Retrofit interface)
 *
 * The app module supplies:
 *   - [TokenProvider] for the current access token
 *   - [AuthFailureHandler] for unauthorized/session-expired handling
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://codeunlock-api.arka-cmd.workers.dev/"
    private const val CONNECT_TIMEOUT = 8L
    private const val READ_TIMEOUT = 8L
    private const val WRITE_TIMEOUT = 8L

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenProvider: TokenProvider
    ): AuthInterceptor {
        return AuthInterceptor {
            tokenProvider.getAccessToken()
        }
    }

    @Provides
    @Singleton
    fun provideErrorInterceptor(
        authFailureHandler: AuthFailureHandler
    ): ErrorInterceptor {
        return ErrorInterceptor(
            onAuthFailure = authFailureHandler::onAuthFailure
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        errorInterceptor: ErrorInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideCodeUnlockApiService(retrofit: Retrofit): CodeUnlockApiService {
        return retrofit.create(CodeUnlockApiService::class.java)
    }
}

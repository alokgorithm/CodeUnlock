package com.codeunlock.di

import com.codeunlock.api.auth.AuthFailureHandler
import com.codeunlock.api.auth.TokenProvider
import com.codeunlock.data.session.SessionAuthFailureHandler
import com.codeunlock.data.session.SessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTokenProvider(
        sessionManager: SessionManager
    ): TokenProvider

    @Binds
    @Singleton
    abstract fun bindAuthFailureHandler(
        authFailureHandler: SessionAuthFailureHandler
    ): AuthFailureHandler
}

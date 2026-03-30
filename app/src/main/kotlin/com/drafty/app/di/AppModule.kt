package com.drafty.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-wide Hilt module.
 * Provides application-scoped dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // App-level bindings will be added here as needed
}

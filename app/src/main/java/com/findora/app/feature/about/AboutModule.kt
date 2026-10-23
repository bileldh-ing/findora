package com.findora.app.feature.about

import com.findora.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AboutModule {
    /** Injected rather than read from BuildConfig in the ViewModel, so tests pass their own. */
    @Provides
    @Named(AboutViewModel.VERSION_NAME)
    fun provideVersionName(): String = BuildConfig.VERSION_NAME
}

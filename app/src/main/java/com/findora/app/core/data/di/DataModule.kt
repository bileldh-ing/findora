package com.findora.app.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.findora.app.core.common.AppClock
import com.findora.app.core.common.SystemAppClock
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.dao.FavoriteDao
import com.findora.app.core.data.local.dao.InteractionEventDao
import com.findora.app.core.data.local.dao.MessageDao
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.dao.RecentSearchDao
import com.findora.app.core.data.local.dao.SavedSearchDao
import com.findora.app.core.data.local.dao.UserDao
import com.findora.app.core.data.local.dao.ViewingDao
import com.findora.app.core.data.match.DataStoreMatchProfileRepository
import com.findora.app.core.data.remote.FakeRemoteDataSource
import com.findora.app.core.data.remote.RemoteDataSource
import com.findora.app.core.data.repository.CachePolicy
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.OfflineFirstPropertyRepository
import com.findora.app.core.data.repository.RoomFavoritesRepository
import com.findora.app.core.data.repository.RoomInteractionRepository
import com.findora.app.core.data.repository.RoomMessageRepository
import com.findora.app.core.data.repository.RoomSearchHistoryRepository
import com.findora.app.core.data.repository.RoomViewingRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.data.seed.AssetSeedReader
import com.findora.app.core.data.seed.SeedReader
import com.findora.app.core.data.settings.DataStoreSettingsRepository
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.MessageRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.RegionRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.reminder.AndroidViewingReminderNotifier
import com.findora.app.core.reminder.ViewingReminderNotifier
import com.findora.app.core.reminder.WorkManagerViewingReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.ZoneId
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    abstract fun bindSeedReader(impl: AssetSeedReader): SeedReader

    @Binds
    abstract fun bindRemoteDataSource(impl: FakeRemoteDataSource): RemoteDataSource

    @Binds
    abstract fun bindPropertyRepository(impl: OfflineFirstPropertyRepository): PropertyRepository

    @Binds
    abstract fun bindFavoritesRepository(impl: RoomFavoritesRepository): FavoritesRepository

    @Binds
    abstract fun bindSearchHistoryRepository(impl: RoomSearchHistoryRepository): SearchHistoryRepository

    @Binds
    abstract fun bindInteractionRepository(impl: RoomInteractionRepository): InteractionRepository

    @Binds
    abstract fun bindRegionRepository(impl: SeedRegionRepository): RegionRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindHomeFilterRepository(impl: InMemoryHomeFilterRepository): HomeFilterRepository

    @Binds
    abstract fun bindMessageRepository(impl: RoomMessageRepository): MessageRepository

    @Binds
    abstract fun bindMatchProfileRepository(impl: DataStoreMatchProfileRepository): MatchProfileRepository
}

/** Viewing bookings and their WorkManager reminders. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ViewingBindingsModule {
    @Binds
    abstract fun bindViewingRepository(impl: RoomViewingRepository): ViewingRepository

    @Binds
    abstract fun bindViewingReminderScheduler(impl: WorkManagerViewingReminderScheduler): ViewingReminderScheduler

    @Binds
    abstract fun bindViewingReminderNotifier(impl: AndroidViewingReminderNotifier): ViewingReminderNotifier
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val SETTINGS_FILE = "settings"

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    fun provideClock(): AppClock = SystemAppClock

    /** Read on every injection so a time-zone change on the device is picked up by new screens and workers. */
    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    @Provides
    fun provideCachePolicy(): CachePolicy = CachePolicy()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A corrupted file resets settings to defaults instead of crashing on start.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
        )
}

/** Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): FindoraDatabase =
        Room
            .databaseBuilder(context, FindoraDatabase::class.java, FindoraDatabase.NAME)
            // Every schema change ships a migration (auto or explicit) tested against the exported schemas.
            .build()

    @Provides
    fun providePropertyDao(db: FindoraDatabase): PropertyDao = db.propertyDao()

    @Provides
    fun provideFavoriteDao(db: FindoraDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideUserDao(db: FindoraDatabase): UserDao = db.userDao()

    @Provides
    fun provideMessageDao(db: FindoraDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideSavedSearchDao(db: FindoraDatabase): SavedSearchDao = db.savedSearchDao()

    @Provides
    fun provideViewingDao(db: FindoraDatabase): ViewingDao = db.viewingDao()

    @Provides
    fun provideRecentSearchDao(db: FindoraDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun provideInteractionEventDao(db: FindoraDatabase): InteractionEventDao = db.interactionEventDao()
}

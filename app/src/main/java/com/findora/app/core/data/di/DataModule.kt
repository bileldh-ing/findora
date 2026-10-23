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
import com.findora.app.core.data.chat.DemoAgentResponder
import com.findora.app.core.data.chat.ResourceAgentReplyFormatter
import com.findora.app.core.data.export.ContentResolverDocumentWriter
import com.findora.app.core.data.export.JsonUserDataExporter
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.dao.ChatDao
import com.findora.app.core.data.local.dao.FavoriteDao
import com.findora.app.core.data.local.dao.InteractionEventDao
import com.findora.app.core.data.local.dao.NotificationDao
import com.findora.app.core.data.local.dao.OwnerListingDao
import com.findora.app.core.data.local.dao.PriceWatchDao
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.dao.RecentSearchDao
import com.findora.app.core.data.local.dao.SavedSearchDao
import com.findora.app.core.data.local.dao.UserDao
import com.findora.app.core.data.local.dao.ViewingDao
import com.findora.app.core.data.location.LastKnownLocationSource
import com.findora.app.core.data.match.DataStoreMatchProfileRepository
import com.findora.app.core.data.owner.AndroidPhotoCodec
import com.findora.app.core.data.owner.DataStoreOwnerModeRepository
import com.findora.app.core.data.owner.FileListingPhotoStore
import com.findora.app.core.data.owner.ListingPhotosDir
import com.findora.app.core.data.owner.OwnerListingIds
import com.findora.app.core.data.owner.PhotoCodec
import com.findora.app.core.data.owner.RoomOwnerListingRepository
import com.findora.app.core.data.reference.AssetReferenceDataRepository
import com.findora.app.core.data.remote.FakeRemoteDataSource
import com.findora.app.core.data.remote.RemoteDataSource
import com.findora.app.core.data.repository.CachePolicy
import com.findora.app.core.data.repository.InMemoryCompareRepository
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.OfflineFirstPropertyRepository
import com.findora.app.core.data.repository.RoomChatRepository
import com.findora.app.core.data.repository.RoomFavoritesRepository
import com.findora.app.core.data.repository.RoomInteractionRepository
import com.findora.app.core.data.repository.RoomNotificationRepository
import com.findora.app.core.data.repository.RoomSearchHistoryRepository
import com.findora.app.core.data.repository.RoomViewingRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.data.seed.AssetSeedReader
import com.findora.app.core.data.seed.SeedReader
import com.findora.app.core.data.settings.DataStoreSettingsRepository
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.chat.AgentReplyFormatter
import com.findora.app.core.domain.chat.AgentResponder
import com.findora.app.core.domain.location.DeviceLocationSource
import com.findora.app.core.domain.owner.ListingPhotoStore
import com.findora.app.core.domain.owner.OwnerListingRepository
import com.findora.app.core.domain.owner.OwnerModeRepository
import com.findora.app.core.domain.repository.CacheCleaner
import com.findora.app.core.domain.repository.ChatRepository
import com.findora.app.core.domain.repository.CompareRepository
import com.findora.app.core.domain.repository.DocumentWriter
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.HomeFilterRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.LanguageController
import com.findora.app.core.domain.repository.MatchProfileRepository
import com.findora.app.core.domain.repository.NotificationRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.ReferenceDataRepository
import com.findora.app.core.domain.repository.RegionRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.domain.repository.SettingsRepository
import com.findora.app.core.domain.repository.UserDataExporter
import com.findora.app.core.domain.repository.ViewingReminderScheduler
import com.findora.app.core.domain.repository.ViewingRepository
import com.findora.app.core.image.AndroidCacheCleaner
import com.findora.app.core.locale.AppCompatLanguageController
import com.findora.app.core.notification.AndroidChatNotifier
import com.findora.app.core.notification.ChatNotifier
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
import java.io.File
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
}

/** Settings support: bundled reference data, per-app language and the privacy & data controls. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsBindingsModule {
    @Binds
    abstract fun bindReferenceDataRepository(impl: AssetReferenceDataRepository): ReferenceDataRepository

    @Binds
    abstract fun bindLanguageController(impl: AppCompatLanguageController): LanguageController

    @Binds
    abstract fun bindCacheCleaner(impl: AndroidCacheCleaner): CacheCleaner

    @Binds
    abstract fun bindUserDataExporter(impl: JsonUserDataExporter): UserDataExporter

    @Binds
    abstract fun bindDocumentWriter(impl: ContentResolverDocumentWriter): DocumentWriter
}

/** Chat with the demo agent and the in-app notifications center. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatBindingsModule {
    @Binds
    abstract fun bindChatRepository(impl: RoomChatRepository): ChatRepository

    @Binds
    abstract fun bindNotificationRepository(impl: RoomNotificationRepository): NotificationRepository

    @Binds
    abstract fun bindAgentResponder(impl: DemoAgentResponder): AgentResponder

    @Binds
    abstract fun bindAgentReplyFormatter(impl: ResourceAgentReplyFormatter): AgentReplyFormatter

    @Binds
    abstract fun bindChatNotifier(impl: AndroidChatNotifier): ChatNotifier
}

/** The Best Match engine's preference profiles. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MatchBindingsModule {
    @Binds
    abstract fun bindMatchProfileRepository(impl: DataStoreMatchProfileRepository): MatchProfileRepository

    @Binds
    abstract fun bindCompareRepository(impl: InMemoryCompareRepository): CompareRepository
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

    @Binds
    abstract fun bindDeviceLocationSource(impl: LastKnownLocationSource): DeviceLocationSource
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
@Suppress("TooManyFunctions") // One provider per DAO.
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
    fun provideChatDao(db: FindoraDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideNotificationDao(db: FindoraDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideSavedSearchDao(db: FindoraDatabase): SavedSearchDao = db.savedSearchDao()

    @Provides
    fun providePriceWatchDao(db: FindoraDatabase): PriceWatchDao = db.priceWatchDao()

    @Provides
    fun provideViewingDao(db: FindoraDatabase): ViewingDao = db.viewingDao()

    @Provides
    fun provideRecentSearchDao(db: FindoraDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun provideInteractionEventDao(db: FindoraDatabase): InteractionEventDao = db.interactionEventDao()

    @Provides
    fun provideOwnerListingDao(db: FindoraDatabase): OwnerListingDao = db.ownerListingDao()
}

/** Owner mode: listings created on the device, their photos and the owner-mode switch. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OwnerBindingsModule {
    @Binds
    abstract fun bindOwnerListingRepository(impl: RoomOwnerListingRepository): OwnerListingRepository

    @Binds
    abstract fun bindListingPhotoStore(impl: FileListingPhotoStore): ListingPhotoStore

    @Binds
    abstract fun bindPhotoCodec(impl: AndroidPhotoCodec): PhotoCodec

    @Binds
    abstract fun bindOwnerModeRepository(impl: DataStoreOwnerModeRepository): OwnerModeRepository

    companion object {
        private const val PHOTOS_DIR = "listing_photos"

        @Provides
        @ListingPhotosDir
        fun provideListingPhotosDir(
            @ApplicationContext context: Context,
        ): File = File(context.filesDir, PHOTOS_DIR)

        @Provides
        fun provideOwnerListingIds(): OwnerListingIds = OwnerListingIds.Random
    }
}

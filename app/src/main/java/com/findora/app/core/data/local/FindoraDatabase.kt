package com.findora.app.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.findora.app.core.data.local.dao.AccountDao
import com.findora.app.core.data.local.dao.ChatDao
import com.findora.app.core.data.local.dao.FavoriteDao
import com.findora.app.core.data.local.dao.InteractionEventDao
import com.findora.app.core.data.local.dao.NotificationDao
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.dao.PropertySearchDao
import com.findora.app.core.data.local.dao.RecentSearchDao
import com.findora.app.core.data.local.dao.SavedSearchDao
import com.findora.app.core.data.local.dao.UserDao
import com.findora.app.core.data.local.dao.ViewingDao
import com.findora.app.core.data.local.entity.ConversationEntity
import com.findora.app.core.data.local.entity.FavoriteEntity
import com.findora.app.core.data.local.entity.InteractionEventEntity
import com.findora.app.core.data.local.entity.LoginAttemptEntity
import com.findora.app.core.data.local.entity.MessageEntity
import com.findora.app.core.data.local.entity.NotificationEntity
import com.findora.app.core.data.local.entity.PropertyEntity
import com.findora.app.core.data.local.entity.RecentSearchEntity
import com.findora.app.core.data.local.entity.SavedSearchEntity
import com.findora.app.core.data.local.entity.UserEntity
import com.findora.app.core.data.local.entity.ViewingEntity

/** Local single source of truth. Schemas are exported to app/schemas and committed. */
@Database(
    entities = [
        PropertyEntity::class,
        FavoriteEntity::class,
        UserEntity::class,
        MessageEntity::class,
        SavedSearchEntity::class,
        ViewingEntity::class,
        RecentSearchEntity::class,
        InteractionEventEntity::class,
        LoginAttemptEntity::class,
        ConversationEntity::class,
        NotificationEntity::class,
    ],
    version = FindoraDatabase.VERSION,
    exportSchema = true,
    // v2 adds login_attempts (sign-in throttling); v3 adds conversations, notifications and messages.status
    // (with a default). Both are pure additions, so Room generates the migrations; DatabaseSchemaTest runs them.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class FindoraDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun userDao(): UserDao

    abstract fun chatDao(): ChatDao

    abstract fun notificationDao(): NotificationDao

    abstract fun savedSearchDao(): SavedSearchDao

    abstract fun viewingDao(): ViewingDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun interactionEventDao(): InteractionEventDao

    abstract fun accountDao(): AccountDao

    abstract fun propertySearchDao(): PropertySearchDao

    companion object {
        const val VERSION = 3
        const val NAME = "findora.db"
    }
}

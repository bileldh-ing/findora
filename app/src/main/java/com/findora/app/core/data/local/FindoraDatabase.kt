package com.findora.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.findora.app.core.data.local.dao.FavoriteDao
import com.findora.app.core.data.local.dao.InteractionEventDao
import com.findora.app.core.data.local.dao.MessageDao
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.dao.RecentSearchDao
import com.findora.app.core.data.local.dao.SavedSearchDao
import com.findora.app.core.data.local.dao.UserDao
import com.findora.app.core.data.local.dao.ViewingDao
import com.findora.app.core.data.local.entity.FavoriteEntity
import com.findora.app.core.data.local.entity.InteractionEventEntity
import com.findora.app.core.data.local.entity.MessageEntity
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
    ],
    version = FindoraDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FindoraDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun userDao(): UserDao

    abstract fun messageDao(): MessageDao

    abstract fun savedSearchDao(): SavedSearchDao

    abstract fun viewingDao(): ViewingDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun interactionEventDao(): InteractionEventDao

    companion object {
        const val VERSION = 1
        const val NAME = "findora.db"
    }
}

package com.findora.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A listing created on this device by [ownerId]. [draftJson] is the wizard's working copy (versioned JSON, see
 * OwnerDraftJson); [publishedJson] is the snapshot made live at the last publish, null until the first one.
 */
@Entity(tableName = "owner_listings", indices = [Index(value = ["owner_id", "updated_at"])])
data class OwnerListingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val status: String,
    @ColumnInfo(name = "draft_json") val draftJson: String,
    @ColumnInfo(name = "published_json") val publishedJson: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAtMillis: Long,
    @ColumnInfo(name = "published_at") val publishedAtMillis: Long?,
)

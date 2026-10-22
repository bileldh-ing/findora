package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.findora.app.core.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /** Throws SQLiteConstraintException when the e-mail is already registered. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :normalizedEmail LIMIT 1")
    suspend fun findByEmail(normalizedEmail: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("UPDATE users SET password_hash = :hash, password_salt = :salt WHERE id = :id")
    suspend fun updatePassword(
        id: String,
        hash: String,
        salt: String,
    ): Int
}

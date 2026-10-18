package com.kiroku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE owner_type = :type AND owner_id = :ownerId ORDER BY created_at ASC")
    fun observeFor(type: String, ownerId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE owner_type = :type AND owner_id = :ownerId")
    suspend fun getFor(type: String, ownerId: String): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE owner_type = :type AND owner_id = :ownerId")
    suspend fun countFor(type: String, ownerId: String): Int

    @Insert
    suspend fun insert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachments WHERE owner_type = :type AND owner_id = :ownerId")
    suspend fun deleteFor(type: String, ownerId: String)
}

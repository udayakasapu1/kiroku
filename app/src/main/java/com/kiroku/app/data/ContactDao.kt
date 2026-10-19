package com.kiroku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE link_id = :linkId ORDER BY created_at ASC")
    fun observeFor(linkId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE link_id = :linkId ORDER BY created_at ASC")
    suspend fun getFor(linkId: String): List<ContactEntity>

    @Query("SELECT * FROM contacts ORDER BY created_at ASC")
    suspend fun getAll(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(contact: ContactEntity): Long

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts WHERE link_id = :linkId")
    suspend fun deleteForLink(linkId: String)
}

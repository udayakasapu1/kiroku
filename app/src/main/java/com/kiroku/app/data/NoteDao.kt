package com.kiroku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE status = 'saved' ORDER BY pinned DESC, updated_at DESC")
    fun observeSaved(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE status = 'archived' ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE status = 'saved'
          AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updated_at DESC
        """
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}

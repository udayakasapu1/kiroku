package com.kiroku.app.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(private val dao: NoteDao) {

    fun observeSaved(): Flow<List<NoteEntity>> = dao.observeSaved()
    fun observeArchived(): Flow<List<NoteEntity>> = dao.observeArchived()
    fun search(query: String): Flow<List<NoteEntity>> = dao.search(query)

    suspend fun getById(id: String): NoteEntity? = dao.getById(id)
    suspend fun allNotes(): List<NoteEntity> = dao.getAll()
    suspend fun upsert(note: NoteEntity) = dao.upsert(note)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun unarchive(id: String) = dao.setStatus(id, Status.SAVED, System.currentTimeMillis())

    suspend fun create(title: String, body: String): NoteEntity {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            body = body,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(note)
        return note
    }

    /** Used by backup import; returns false if a note with this id already exists. */
    suspend fun insertImported(note: NoteEntity): Boolean = try {
        dao.insert(note)
        true
    } catch (e: SQLiteConstraintException) {
        false
    }
}

package com.kiroku.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    /**
     * Saved links in one folder. category = null means Unsorted: no folder, or a folder
     * that no longer exists.
     */
    @Query(
        """
        SELECT * FROM knowledge_items
        WHERE status = 'saved'
          AND ((:category IS NULL AND (category IS NULL OR category NOT IN (SELECT id FROM categories)))
               OR category = :category)
        ORDER BY pinned DESC, created_at DESC
        """
    )
    fun observeFolder(category: String?): Flow<List<LinkEntity>>

    @Query("SELECT * FROM knowledge_items WHERE status = 'archived' ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<LinkEntity>>

    @Query(
        """
        SELECT * FROM knowledge_items
        WHERE status = 'saved'
          AND (title LIKE '%' || :query || '%'
               OR description LIKE '%' || :query || '%'
               OR url LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%'
               OR tags LIKE '%' || :query || '%'
               OR source_domain LIKE '%' || :query || '%'
               OR site_name LIKE '%' || :query || '%'
               OR id IN (SELECT link_id FROM contacts
                         WHERE name LIKE '%' || :query || '%'
                            OR email LIKE '%' || :query || '%'
                            OR phone LIKE '%' || :query || '%'
                            OR address LIKE '%' || :query || '%'))
        ORDER BY pinned DESC, created_at DESC
        """
    )
    fun search(query: String): Flow<List<LinkEntity>>

    @Query("SELECT category, COUNT(*) AS count FROM knowledge_items WHERE status = 'saved' GROUP BY category")
    fun observeCounts(): Flow<List<CategoryCount>>

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    fun observeById(id: String): Flow<LinkEntity?>

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    suspend fun getById(id: String): LinkEntity?

    @Query("SELECT * FROM knowledge_items WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): LinkEntity?

    @Query("SELECT * FROM knowledge_items ORDER BY created_at DESC")
    suspend fun getAll(): List<LinkEntity>

    @Query("SELECT id FROM knowledge_items WHERE fetch_state != 'DONE'")
    suspend fun unfinishedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: LinkEntity)

    @Update
    suspend fun update(item: LinkEntity)

    @Query("UPDATE knowledge_items SET title = :title, title_custom = :custom, updated_at = :now WHERE id = :id")
    suspend fun setTitle(id: String, title: String?, custom: Boolean, now: Long)

    @Query("UPDATE knowledge_items SET notes = :notes, updated_at = :now WHERE id = :id")
    suspend fun setNotes(id: String, notes: String, now: Long)

    @Query("UPDATE knowledge_items SET category = :category, updated_at = :now WHERE id = :id")
    suspend fun setCategory(id: String, category: String?, now: Long)

    @Query("UPDATE knowledge_items SET category = NULL, updated_at = :now WHERE category = :categoryId")
    suspend fun clearCategory(categoryId: String, now: Long)

    @Query("UPDATE knowledge_items SET category = :to, updated_at = :now WHERE category = :from")
    suspend fun moveCategory(from: String, to: String, now: Long)

    /** New address: old page details are cleared and fetched again. */
    @Query(
        """
        UPDATE knowledge_items
        SET url = :url, source_domain = :domain, description = NULL, preview_image_url = NULL,
            site_name = NULL, fetch_state = 'PENDING', fetch_error = NULL, updated_at = :now
        WHERE id = :id
        """
    )
    suspend fun setUrl(id: String, url: String, domain: String?, now: Long)

    @Query("UPDATE knowledge_items SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long)

    @Query("UPDATE knowledge_items SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    /** tagsJson must already be encoded with [Converters.tagsToJson]. */
    @Query("UPDATE knowledge_items SET tags = :tagsJson, updated_at = :now WHERE id = :id")
    suspend fun setTagsJson(id: String, tagsJson: String, now: Long)

    @Query("UPDATE knowledge_items SET fetch_state = :state, fetch_error = :error WHERE id = :id")
    suspend fun setFetchState(id: String, state: String, error: String?)

    @Query("DELETE FROM knowledge_items WHERE id = :id")
    suspend fun delete(id: String)
}

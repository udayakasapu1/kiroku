package com.kiroku.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kiroku.app.fetch.FetchWorker
import com.kiroku.app.fetch.MetadataFetcher
import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface UrlEditResult {
    data object Updated : UrlEditResult
    /** Same address as before; details are fetched again. */
    data object Unchanged : UrlEditResult
    data object Invalid : UrlEditResult
    data class Duplicate(val item: LinkEntity) : UrlEditResult
}

sealed interface SaveResult {
    data class Saved(val item: LinkEntity) : SaveResult
    data class AlreadySaved(val item: LinkEntity) : SaveResult
    data object InvalidUrl : SaveResult
}

class LinkRepository(
    private val context: Context,
    private val dao: LinkDao,
    private val fetcher: MetadataFetcher,
) {
    private val converters = Converters()
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun observeFolder(category: String?): Flow<List<LinkEntity>> = dao.observeFolder(category)
    fun observeArchived(): Flow<List<LinkEntity>> = dao.observeArchived()
    fun search(query: String): Flow<List<LinkEntity>> = dao.search(query)
    fun observeCounts(): Flow<List<CategoryCount>> = dao.observeCounts()
    fun observeItem(id: String): Flow<LinkEntity?> = dao.observeById(id)
    suspend fun getById(id: String): LinkEntity? = dao.getById(id)
    suspend fun allItems(): List<LinkEntity> = dao.getAll()
    suspend fun findByUrl(normalizedUrl: String): LinkEntity? = dao.getByUrl(normalizedUrl)

    /**
     * Saves the link locally right away (works offline), then queues a background fetch
     * of the page's title/description/image for when the phone is online.
     */
    suspend fun save(
        rawInput: String,
        note: String = "",
        titleHint: String? = null,
        category: String? = null,
    ): SaveResult {
        val candidate = UrlUtils.extractUrl(rawInput) ?: rawInput
        val url = UrlUtils.normalize(candidate) ?: return SaveResult.InvalidUrl
        dao.getByUrl(url)?.let { return SaveResult.AlreadySaved(it) }

        val now = System.currentTimeMillis()
        val item = LinkEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            title = titleHint?.trim()?.takeIf { it.isNotEmpty() },
            category = category,
            sourceDomain = UrlUtils.domainOf(url),
            notes = note.trim(),
            createdAt = now,
            updatedAt = now,
        )
        try {
            dao.insert(item)
        } catch (e: SQLiteConstraintException) {
            dao.getByUrl(url)?.let { return SaveResult.AlreadySaved(it) }
            throw e
        }
        enqueueFetch(item.id)
        return SaveResult.Saved(item)
    }

    /** Used by backup import; returns false if the URL or id already exists. */
    suspend fun insertImported(item: LinkEntity): Boolean {
        return try {
            dao.insert(item)
            if (item.fetchState != FetchState.DONE) enqueueFetch(item.id)
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    /** Called by [FetchWorker]. Throws on network/HTTP errors so the worker can retry. */
    suspend fun fetchAndApply(id: String) {
        val item = dao.getById(id) ?: return
        val meta = fetcher.fetch(item.url)
        val current = dao.getById(id) ?: return // deleted while fetching
        dao.update(
            current.copy(
                title = meta.title ?: current.title,
                description = meta.description ?: current.description,
                previewImageUrl = meta.imageUrl ?: current.previewImageUrl,
                siteName = meta.siteName ?: current.siteName,
                sourceDomain = meta.sourceDomain ?: current.sourceDomain,
                fetchState = FetchState.DONE,
                fetchError = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Changes a saved link's address and fetches its details again. */
    suspend fun updateUrl(id: String, rawInput: String): UrlEditResult {
        val candidate = UrlUtils.extractUrl(rawInput) ?: rawInput
        val url = UrlUtils.normalize(candidate) ?: return UrlEditResult.Invalid
        val current = dao.getById(id) ?: return UrlEditResult.Invalid
        if (url == current.url) {
            refetch(id)
            return UrlEditResult.Unchanged
        }
        dao.getByUrl(url)?.let { if (it.id != id) return UrlEditResult.Duplicate(it) }
        try {
            dao.setUrl(id, url, UrlUtils.domainOf(url), now())
        } catch (e: SQLiteConstraintException) {
            dao.getByUrl(url)?.let { return UrlEditResult.Duplicate(it) }
            throw e
        }
        enqueueFetch(id)
        return UrlEditResult.Updated
    }

    suspend fun markFetchFailed(id: String, message: String) =
        dao.setFetchState(id, FetchState.FAILED, message)

    suspend fun refetch(id: String) {
        dao.setFetchState(id, FetchState.PENDING, null)
        enqueueFetch(id)
    }

    /** Re-queues every link whose details haven't been fetched yet. Returns how many. */
    suspend fun retryUnfinished(): Int {
        val ids = dao.unfinishedIds()
        ids.forEach { refetch(it) }
        return ids.size
    }

    suspend fun updateTitle(id: String, title: String) =
        dao.setTitle(id, title.trim().takeIf { it.isNotEmpty() }, now())

    suspend fun updateNotes(id: String, notes: String) = dao.setNotes(id, notes, now())
    suspend fun setCategory(id: String, category: String?) = dao.setCategory(id, category, now())

    suspend fun archive(id: String) = dao.setStatus(id, Status.ARCHIVED, now())
    suspend fun unarchive(id: String) = dao.setStatus(id, Status.SAVED, now())
    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, now())
    suspend fun setTags(id: String, tags: List<String>) =
        dao.setTagsJson(id, converters.tagsToJson(tags), now())

    suspend fun delete(id: String) {
        workManager.cancelUniqueWork(workName(id))
        dao.delete(id)
    }

    private fun enqueueFetch(id: String) {
        val request = OneTimeWorkRequestBuilder<FetchWorker>()
            .setInputData(workDataOf(FetchWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(id: String) = "fetch-$id"
    private fun now() = System.currentTimeMillis()
}

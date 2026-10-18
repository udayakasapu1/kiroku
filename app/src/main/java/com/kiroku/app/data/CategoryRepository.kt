package com.kiroku.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * User folders. Folder names are unique (ignoring case and extra spaces): creating a folder
 * whose name already exists returns the existing one instead of making a duplicate.
 */
class CategoryRepository(
    private val db: KirokuDatabase,
    private val dao: CategoryDao,
    private val linkDao: LinkDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    fun observe(id: String): Flow<CategoryEntity?> = dao.observeById(id)
    suspend fun getAll(): List<CategoryEntity> = dao.getAll()
    suspend fun count(): Int = dao.count()

    suspend fun findByName(name: String): CategoryEntity? {
        val key = nameKey(name)
        return dao.getAll().firstOrNull { nameKey(it.name) == key }
    }

    /** Creates a folder, or returns the existing folder with the same name. */
    suspend fun create(name: String, icon: String): CategoryEntity = db.withTransaction {
        findByName(name) ?: CategoryEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName(name),
            icon = icon,
            position = dao.maxPosition() + 1,
            createdAt = System.currentTimeMillis(),
        ).also { dao.insert(it) }
    }

    suspend fun createAll(items: List<FolderSuggestion>) {
        db.withTransaction { items.forEach { create(it.name, it.icon) } }
    }

    /** Renames / re-icons a folder. Returns false if another folder already has that name. */
    suspend fun update(category: CategoryEntity, name: String, icon: String): Boolean = db.withTransaction {
        val clash = findByName(name)
        if (clash != null && clash.id != category.id) {
            false
        } else {
            dao.update(category.copy(name = cleanName(name), icon = icon))
            true
        }
    }

    /** Saves a new order (the list as it should appear). */
    suspend fun reorder(ordered: List<CategoryEntity>) =
        dao.updateAll(ordered.mapIndexed { index, c -> c.copy(position = index) })

    /** Deletes the folder; its links move to Unsorted. */
    suspend fun delete(id: String) {
        db.withTransaction {
            linkDao.clearCategory(id, System.currentTimeMillis())
            dao.delete(id)
        }
    }

    /**
     * Merges folders that share a name (left over from older versions): links move into the
     * first one, the others are removed. Returns how many duplicates were merged.
     */
    suspend fun mergeDuplicates(): Int = db.withTransaction {
        val keep = mutableMapOf<String, CategoryEntity>()
        var merged = 0
        for (category in dao.getAll()) {
            val key = nameKey(category.name)
            val first = keep[key]
            if (first == null) {
                keep[key] = category
            } else {
                linkDao.moveCategory(category.id, first.id, System.currentTimeMillis())
                dao.delete(category.id)
                merged++
            }
        }
        merged
    }

    /** Used by backup import; keeps ids so links still point at their folder. */
    suspend fun insertImported(category: CategoryEntity): Boolean = dao.insertIgnore(category) != -1L

    companion object {
        const val MAX_NAME = 40

        fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

        /** What counts as "the same name": case and spacing ignored. */
        fun nameKey(name: String): String = cleanName(name).lowercase()
    }
}

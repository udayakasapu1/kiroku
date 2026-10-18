package com.kiroku.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CategoryRepository(
    private val db: KirokuDatabase,
    private val dao: CategoryDao,
    private val linkDao: LinkDao,
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    fun observe(id: String): Flow<CategoryEntity?> = dao.observeById(id)
    suspend fun getAll(): List<CategoryEntity> = dao.getAll()
    suspend fun count(): Int = dao.count()

    suspend fun create(name: String, icon: String): CategoryEntity {
        val category = CategoryEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(MAX_NAME),
            icon = icon,
            position = dao.maxPosition() + 1,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(category)
        return category
    }

    suspend fun createAll(items: List<FolderSuggestion>) {
        db.withTransaction { items.forEach { create(it.name, it.icon) } }
    }

    suspend fun update(category: CategoryEntity, name: String, icon: String) =
        dao.update(category.copy(name = name.trim().take(MAX_NAME), icon = icon))

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

    /** Used by backup import; keeps ids so links still point at their folder. */
    suspend fun insertImported(category: CategoryEntity): Boolean = dao.insertIgnore(category) != -1L

    companion object {
        const val MAX_NAME = 40
    }
}

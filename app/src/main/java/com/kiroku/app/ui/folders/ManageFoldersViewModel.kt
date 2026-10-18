package com.kiroku.app.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.LinkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManagedFolder(val category: CategoryEntity, val links: Int)

class ManageFoldersViewModel(
    private val categories: CategoryRepository,
    links: LinkRepository,
) : ViewModel() {

    /** Null while loading. */
    val folders: StateFlow<List<ManagedFolder>?> =
        combine(categories.observeAll(), links.observeCounts()) { all, counts ->
            val byId = counts.associate { it.category to it.count }
            all.map { ManagedFolder(it, byId[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, icon: String) {
        viewModelScope.launch { categories.create(name, icon) }
    }

    fun update(category: CategoryEntity, name: String, icon: String) {
        viewModelScope.launch { categories.update(category, name, icon) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { categories.delete(category.id) }
    }

    /** Moves a folder one place up (-1) or down (+1). */
    fun move(category: CategoryEntity, direction: Int) {
        val current = folders.value?.map { it.category } ?: return
        val from = current.indexOfFirst { it.id == category.id }
        val to = from + direction
        if (from < 0 || to !in current.indices) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { categories.reorder(reordered) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ManageFoldersViewModel(container.categories, container.links) }
        }
    }
}

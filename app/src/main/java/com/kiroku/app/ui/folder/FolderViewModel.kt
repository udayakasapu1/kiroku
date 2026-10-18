package com.kiroku.app.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.FolderKeys
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.LinkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FolderUiState(
    val loading: Boolean = true,
    val links: List<LinkEntity> = emptyList(),
    /** The folder itself (null for Unsorted and the Archive, or if it was deleted). */
    val category: CategoryEntity? = null,
    /** All folders, for labelling links in the Archive. */
    val categories: List<CategoryEntity> = emptyList(),
)

/** The links in one folder (a user folder or Unsorted), or the links Archive. */
class FolderViewModel(
    handle: SavedStateHandle,
    private val links: LinkRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val folderKey: String = checkNotNull(handle.get<String>("key"))
    val isArchive: Boolean = folderKey == FolderKeys.ARCHIVED
    val isUnsorted: Boolean = folderKey == FolderKeys.UNSORTED

    /** Value for links added from inside this folder. */
    val categoryId: String? = FolderKeys.categoryOf(folderKey)

    private val linkFlow = if (isArchive) links.observeArchived() else links.observeFolder(categoryId)

    val state: StateFlow<FolderUiState> =
        combine(linkFlow, categories.observeAll()) { l, all ->
            FolderUiState(
                loading = false,
                links = l,
                category = categoryId?.let { id -> all.firstOrNull { it.id == id } },
                categories = all,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    suspend fun retryPending(): Int = links.retryUnfinished()

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { FolderViewModel(createSavedStateHandle(), container.links, container.categories) }
        }
    }
}

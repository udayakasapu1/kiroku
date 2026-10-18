package com.kiroku.app.ui.home

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.backup.BackupManager
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.FolderKeys
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.LinkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A folder tile: a real folder, or Unsorted (category = null). */
data class FolderTile(val key: String, val category: CategoryEntity?, val links: Int)

data class HomeState(val folders: List<FolderTile>, val totalLinks: Int, val categories: List<CategoryEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val links: LinkRepository,
    private val categories: CategoryRepository,
    private val backup: BackupManager,
) : ViewModel() {

    var searchText by mutableStateOf("")
        private set

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** Null while loading. */
    val state: StateFlow<HomeState?> =
        combine(categories.observeAll(), links.observeCounts()) { folders, counts ->
            val ids = folders.map { it.id }.toSet()
            val byCategory = mutableMapOf<String, Int>()
            var unsorted = 0
            counts.forEach { row ->
                val id = row.category
                if (id != null && id in ids) byCategory[id] = (byCategory[id] ?: 0) + row.count
                else unsorted += row.count
            }
            val tiles = folders.map { FolderTile(it.id, it, byCategory[it.id] ?: 0) } +
                listOfNotNull(FolderTile(FolderKeys.UNSORTED, null, unsorted).takeIf { unsorted > 0 })
            HomeState(tiles, counts.sumOf { it.count }, folders)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Search results across all folders; null when not searching. */
    val searchResults: StateFlow<List<LinkEntity>?> =
        snapshotFlow { searchText.trim() }
            .flatMapLatest<String, List<LinkEntity>?> { query ->
                if (query.isEmpty()) flowOf(null) else links.search(query)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        searchText = value
    }

    fun createFolder(name: String, icon: String) {
        viewModelScope.launch { categories.create(name, icon) }
    }

    fun retryPending() {
        viewModelScope.launch {
            val count = links.retryUnfinished()
            messageChannel.send(if (count == 0) "All link details are up to date" else "Retrying $count link(s)")
        }
    }

    fun exportTo(uri: Uri) = exportBackup(viewModelScope, backup, uri, messageChannel)
    fun importFrom(uri: Uri) = importBackup(viewModelScope, backup, uri, messageChannel)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container.links, container.categories, container.backup) }
        }
    }
}

// Shared by the Links and Notes home screens.

fun exportBackup(scope: kotlinx.coroutines.CoroutineScope, backup: BackupManager, uri: Uri, messages: Channel<String>) {
    scope.launch {
        val message = try {
            "Exported ${backup.export(uri)} items"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Export failed: ${e.message ?: e.javaClass.simpleName}"
        }
        messages.send(message)
    }
}

fun importBackup(scope: kotlinx.coroutines.CoroutineScope, backup: BackupManager, uri: Uri, messages: Channel<String>) {
    scope.launch {
        val message = try {
            val r = backup.import(uri)
            buildString {
                append("Imported ${r.links} links and ${r.notes} notes")
                if (r.folders > 0) append(", ${r.folders} folders")
                if (r.skipped > 0) append(" (${r.skipped} skipped as duplicates or invalid)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Import failed: ${e.message ?: e.javaClass.simpleName}"
        }
        messages.send(message)
    }
}

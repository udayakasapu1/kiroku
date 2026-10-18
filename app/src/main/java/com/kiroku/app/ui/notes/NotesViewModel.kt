package com.kiroku.app.ui.notes

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.backup.BackupManager
import com.kiroku.app.data.NoteEntity
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.ui.home.exportBackup
import com.kiroku.app.ui.home.importBackup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** The Notes home (Keep-style board), or the notes archive when `archived` is true. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    handle: SavedStateHandle,
    private val notes: NoteRepository,
    private val backup: BackupManager,
) : ViewModel() {

    val archived: Boolean = handle.get<Boolean>("archived") ?: false

    var searchText by mutableStateOf("")
        private set

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** Null while loading. */
    val items: StateFlow<List<NoteEntity>?> =
        snapshotFlow { searchText.trim() }
            .flatMapLatest { query ->
                when {
                    archived -> notes.observeArchived()
                    query.isEmpty() -> notes.observeSaved()
                    else -> notes.search(query)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        searchText = value
    }

    fun exportTo(uri: Uri) = exportBackup(viewModelScope, backup, uri, messageChannel)
    fun importFrom(uri: Uri) = importBackup(viewModelScope, backup, uri, messageChannel)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NotesViewModel(createSavedStateHandle(), container.notes, container.backup) }
        }
    }
}

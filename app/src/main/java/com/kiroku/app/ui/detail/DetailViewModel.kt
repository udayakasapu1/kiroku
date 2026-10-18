package com.kiroku.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kiroku.app.data.AttachmentEntity
import com.kiroku.app.data.AttachmentRepository
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.OwnerType
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.UrlEditResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DetailState(val loading: Boolean = true, val item: LinkEntity? = null)

class DetailViewModel(
    handle: SavedStateHandle,
    private val repository: LinkRepository,
    private val categories: CategoryRepository,
    private val files: AttachmentRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    /** True while picked files are being copied in. */
    var attaching by mutableStateOf(false)
        private set

    val folders: StateFlow<List<CategoryEntity>> =
        categories.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a folder and moves this link into it. */
    fun createFolder(name: String, icon: String) {
        appScope.launch {
            val created = categories.create(name, icon)
            writeLock.withLock { repository.setCategory(id, created.id) }
        }
    }

    val id: String = checkNotNull(handle.get<String>("id"))
    private val writeLock = Mutex()

    val state: StateFlow<DetailState> = repository.observeItem(id)
        .map { DetailState(loading = false, item = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    /**
     * Edits are written immediately (like Keep: no Save button). They run on the app scope so
     * the last keystrokes still save if the screen is closed, and in strict call order.
     */
    private fun write(block: suspend LinkRepository.() -> Unit) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeLock.withLock { repository.block() }
        }
    }

    /** Changes the link's address; [onResult] runs on the main thread. */
    fun editUrl(raw: String, onResult: (UrlEditResult) -> Unit) {
        viewModelScope.launch {
            val result = writeLock.withLock { repository.updateUrl(id, raw) }
            onResult(result)
        }
    }

    fun setTitle(title: String) = write { updateTitle(id, title) }
    fun setNotes(notes: String) = write { updateNotes(id, notes) }
    fun setCategory(category: String?) = write { setCategory(id, category) }
    fun archive() = write { archive(id) }
    fun unarchive() = write { unarchive(id) }
    fun togglePin() = write { getById(id)?.let { setPinned(id, !it.pinned) } }
    fun retryFetch() = write { refetch(id) }
    fun delete() = write {
        files.removeAllFor(OwnerType.LINK, id)
        delete(id)
    }

    val attachments: StateFlow<List<AttachmentEntity>> by lazy {
        files.observeFor(OwnerType.LINK, id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        attaching = true
        appScope.launch(Dispatchers.Main) {
            var failed = 0
            uris.forEach { uri -> runCatching { files.attach(OwnerType.LINK, id, uri) }.onFailure { failed++ } }
            attaching = false
            if (failed > 0) messageChannel.send("Couldn't attach $failed file(s)")
        }
    }

    fun removeAttachment(attachment: AttachmentEntity) {
        appScope.launch { files.remove(attachment) }
    }

    fun openIntent(attachment: AttachmentEntity): Intent? =
        if (files.fileOf(attachment).exists()) files.openIntent(attachment) else null

    fun addTag(raw: String) {
        val tag = raw.trim().removePrefix("#").trim()
        if (tag.isEmpty()) return
        write {
            val current = getById(id)?.tags.orEmpty()
            if (current.none { it.equals(tag, ignoreCase = true) }) setTags(id, current + tag)
        }
    }

    fun removeTag(tag: String) = write {
        val current = getById(id)?.tags.orEmpty()
        setTags(id, current.filterNot { it == tag })
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DetailViewModel(createSavedStateHandle(), container.links, container.categories, container.attachments, container.appScope) }
        }
    }
}

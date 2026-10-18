package com.kiroku.app.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import android.content.Intent
import android.net.Uri
import com.kiroku.app.data.AttachmentEntity
import com.kiroku.app.data.AttachmentRepository
import com.kiroku.app.data.NoteEntity
import com.kiroku.app.data.OwnerType
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Keep-style editor: no Save button. The note is created on the first character typed,
 * saved on every change, and removed again if it's left empty.
 */
class NoteEditorViewModel(
    handle: SavedStateHandle,
    private val repo: NoteRepository,
    private val files: AttachmentRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val argId: String = handle.get<String>("id") ?: NEW
    val isNew: Boolean = argId == NEW

    /** Stable id, kept across process death so a new note isn't created twice. */
    val noteId: String = handle.get<String>(KEY_NOTE_ID)
        ?: (if (isNew) UUID.randomUUID().toString() else argId).also { handle[KEY_NOTE_ID] = it }

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf("")
        private set
    var pinned by mutableStateOf(false)
        private set
    var archived by mutableStateOf(false)
        private set
    var updatedAt by mutableStateOf<Long?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var gone by mutableStateOf(false)
        private set

    private var createdAt = System.currentTimeMillis()

    @Volatile
    private var exists = false
    private val writeLock = Mutex()

    init {
        viewModelScope.launch {
            val existing = repo.getById(noteId)
            if (existing != null) {
                title = existing.title
                body = existing.body
                pinned = existing.pinned
                archived = existing.status == Status.ARCHIVED
                createdAt = existing.createdAt
                updatedAt = existing.updatedAt
                exists = true
            } else if (!isNew) {
                gone = true
            }
            loaded = true
        }
    }

    fun onTitle(value: String) {
        title = value
        save()
    }

    fun onBody(value: String) {
        body = value
        save()
    }

    fun togglePin() {
        pinned = !pinned
        save()
    }

    /** Returns false if there was nothing to archive (empty note). */
    fun archive(): Boolean {
        if (isBlank()) return false
        archived = true
        save()
        return true
    }

    fun unarchive() {
        archived = false
        save()
    }

    fun delete() {
        write {
            files.removeAllFor(OwnerType.NOTE, noteId)
            repo.delete(noteId)
            exists = false
        }
    }

    /** Nothing typed and nothing attached. */
    fun isBlank(): Boolean = title.isBlank() && body.isBlank() && attachments.value.isEmpty()

    // ---- Attachments ----

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    var attaching by mutableStateOf(false)
        private set

    val attachments: StateFlow<List<AttachmentEntity>> =
        files.observeFor(OwnerType.NOTE, noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun attach(uris: List<Uri>) {
        if (!loaded || gone || uris.isEmpty()) return
        attaching = true
        val snapshot = snapshot(System.currentTimeMillis())
        appScope.launch(Dispatchers.Main) {
            var failed = 0
            writeLock.withLock {
                // A note needs to exist before files can hang off it.
                if (!exists) {
                    repo.upsert(snapshot)
                    exists = true
                }
                uris.forEach { uri -> runCatching { files.attach(OwnerType.NOTE, noteId, uri) }.onFailure { failed++ } }
            }
            attaching = false
            if (failed > 0) messageChannel.send("Couldn't attach $failed file(s)")
        }
    }

    fun removeAttachment(attachment: AttachmentEntity) {
        appScope.launch { files.remove(attachment) }
    }

    fun openIntent(attachment: AttachmentEntity): Intent? =
        if (files.fileOf(attachment).exists()) files.openIntent(attachment) else null

    private fun snapshot(now: Long) = NoteEntity(
            id = noteId,
            title = title,
            body = body,
            category = null,
            pinned = pinned,
            status = if (archived) Status.ARCHIVED else Status.SAVED,
            createdAt = createdAt,
            updatedAt = now,
        )

    private fun save() {
        if (!loaded || gone) return
        val now = System.currentTimeMillis()
        val snapshot = snapshot(now)
        updatedAt = now
        val blank = snapshot.isBlank()
        write {
            if (exists || !blank) {
                repo.upsert(snapshot)
                exists = true
            }
        }
    }

    /** Writes run on the app scope (finish even after the screen closes), one at a time, in order. */
    private fun write(block: suspend () -> Unit) {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            writeLock.withLock { block() }
        }
    }

    override fun onCleared() {
        // Don't keep empty notes around.
        write {
            val note = repo.getById(noteId)
            if (note != null && note.isBlank() && files.countFor(OwnerType.NOTE, noteId) == 0) repo.delete(noteId)
        }
    }

    companion object {
        const val NEW = "new"
        private const val KEY_NOTE_ID = "note_id"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NoteEditorViewModel(createSavedStateHandle(), container.notes, container.attachments, container.appScope) }
        }
    }
}

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
import com.kiroku.app.data.NoteEntity
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
            repo.delete(noteId)
            exists = false
        }
    }

    fun isBlank(): Boolean = title.isBlank() && body.isBlank()

    private fun save() {
        if (!loaded || gone) return
        val now = System.currentTimeMillis()
        val snapshot = NoteEntity(
            id = noteId,
            title = title,
            body = body,
            category = null,
            pinned = pinned,
            status = if (archived) Status.ARCHIVED else Status.SAVED,
            createdAt = createdAt,
            updatedAt = now,
        )
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
            if (note != null && note.isBlank()) repo.delete(noteId)
        }
    }

    companion object {
        const val NEW = "new"
        private const val KEY_NOTE_ID = "note_id"

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { NoteEditorViewModel(createSavedStateHandle(), container.notes, container.appScope) }
        }
    }
}

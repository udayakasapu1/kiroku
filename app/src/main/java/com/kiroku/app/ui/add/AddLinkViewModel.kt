package com.kiroku.app.ui.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.IncomingShare
import com.kiroku.app.ShareInbox
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.FolderKeys
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.data.SaveResult
import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AddSheetState(
    val visible: Boolean = false,
    val url: String = "",
    val note: String = "",
    val category: String? = null,
    val titleHint: String? = null,
    val fromShare: Boolean = false,
    val sharedText: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val duplicateId: String? = null,
    /** True when what was entered/shared isn't a link: offer "Save as note". */
    val noLinkFound: Boolean = false,
)

sealed interface AddEvent {
    data class Saved(val folderKey: String, val folderLabel: String) : AddEvent
    data class OpenLink(val id: String) : AddEvent
    data class OpenNote(val id: String) : AddEvent
}

/** App-wide "save a link" sheet. Lives above all screens so a share can open it anywhere. */
class AddLinkViewModel(
    private val links: LinkRepository,
    private val notes: NoteRepository,
    private val categories: CategoryRepository,
    private val shares: ShareInbox,
) : ViewModel() {

    val folders: StateFlow<List<CategoryEntity>> =
        categories.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Creates a folder from the sheet and selects it. */
    fun createFolder(name: String, icon: String) {
        viewModelScope.launch {
            val created = categories.create(name, icon)
            sheet = sheet.copy(category = created.id)
        }
    }

    private fun folderLabel(id: String?): String =
        folders.value.firstOrNull { it.id == id }?.name ?: "Unsorted"

    var sheet by mutableStateOf(AddSheetState())
        private set

    private val eventChannel = Channel<AddEvent>(Channel.BUFFERED)
    val events: Flow<AddEvent> = eventChannel.receiveAsFlow()

    fun open(category: String?) {
        sheet = AddSheetState(visible = true, category = category)
    }

    fun dismiss() {
        sheet = AddSheetState()
    }

    fun onUrlChange(value: String) {
        sheet = sheet.copy(url = value, error = null, duplicateId = null, noLinkFound = false)
    }

    fun onNoteChange(value: String) {
        sheet = sheet.copy(note = value)
    }

    fun onCategoryChange(value: String?) {
        sheet = sheet.copy(category = value)
    }

    fun save() {
        val current = sheet
        if (current.saving || current.url.isBlank()) return
        sheet = current.copy(saving = true, error = null, duplicateId = null)
        viewModelScope.launch {
            val result = try {
                links.save(current.url, current.note, current.titleHint, current.category)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sheet = sheet.copy(saving = false, error = "Couldn't save: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            when (result) {
                is SaveResult.Saved -> {
                    sheet = AddSheetState()
                    val known = folders.value.any { it.id == current.category }
                    eventChannel.send(
                        AddEvent.Saved(
                            folderKey = if (known) current.category!! else FolderKeys.UNSORTED,
                            folderLabel = folderLabel(current.category),
                        )
                    )
                }
                is SaveResult.AlreadySaved -> sheet = sheet.copy(
                    saving = false,
                    error = "You've already saved this link (in ${folderLabel(result.item.category)}).",
                    duplicateId = result.item.id,
                )
                SaveResult.InvalidUrl -> sheet = sheet.copy(
                    saving = false,
                    error = "That doesn't look like a web link. It should look like https://example.com/page",
                    noLinkFound = true,
                )
            }
        }
    }

    /** Saves the shared/typed text as a note instead, then opens it. */
    fun saveAsNote() {
        val current = sheet
        if (current.saving) return
        val text = (current.sharedText ?: current.url).trim()
        val body = listOf(text, current.note.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
        if (body.isEmpty()) return
        sheet = current.copy(saving = true)
        viewModelScope.launch {
            val note = notes.create(title = current.titleHint.orEmpty(), body = body)
            sheet = AddSheetState()
            eventChannel.send(AddEvent.OpenNote(note.id))
        }
    }

    fun openExisting(id: String) {
        sheet = AddSheetState()
        viewModelScope.launch { eventChannel.send(AddEvent.OpenLink(id)) }
    }

    // ---- Share menu ----

    suspend fun listenForShares() {
        shares.pending.filterNotNull().collect {
            shares.consume()?.let(::openFromShare)
        }
    }

    private fun openFromShare(share: IncomingShare) {
        val url = UrlUtils.extractUrl(share.text) ?: UrlUtils.extractUrl(share.subject)
        if (url == null) {
            // Never silently drop a share: show what arrived, offer to keep it as a note.
            sheet = AddSheetState(
                visible = true,
                fromShare = true,
                url = share.text.trim().ifEmpty { share.subject.orEmpty() }.take(2000),
                sharedText = share.text,
                titleHint = share.subject?.trim()?.takeIf { it.isNotEmpty() },
                error = "No web link found in what was shared.",
                noLinkFound = true,
            )
            return
        }
        val titleHint = share.subject?.trim()?.takeIf { it.isNotEmpty() && UrlUtils.extractUrl(it) == null }
            ?: share.text.replace(url, " ")
                .trim()
                .trim('-', '|', ':', '—', '–', '"')
                .trim()
                .takeIf { it.length in 3..300 && UrlUtils.extractUrl(it) == null }
        sheet = AddSheetState(
            visible = true,
            fromShare = true,
            url = url,
            titleHint = titleHint,
            sharedText = share.text,
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { AddLinkViewModel(container.links, container.notes, container.categories, container.shares) }
        }
    }
}

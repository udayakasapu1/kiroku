package com.kiroku.app.ui.add

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.InputChip
import androidx.compose.ui.platform.LocalContext
import com.kiroku.app.ui.components.AttachmentRow
import com.kiroku.app.ui.components.ContactsSection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiroku.app.ui.components.CategoryPicker
import com.kiroku.app.ui.components.FolderDialog
import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLinkSheet(vm: AddLinkViewModel) {
    val state = vm.sheet
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val urlFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    val folders by vm.folders.collectAsStateWithLifecycle()
    var creatingFolder by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = vm::dismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (state.fromShare) "Save shared link" else "Save a link",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                label = { Text("Link") },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { text ->
                            vm.onUrlChange(UrlUtils.extractUrl(text) ?: text.trim())
                        }
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
                singleLine = true,
                isError = state.error != null,
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.save() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(urlFocus),
            )

            state.titleHint?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text("Folder", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            CategoryPicker(
                categories = folders,
                selected = state.category,
                onSelect = vm::onCategoryChange,
                enabled = !state.saving,
                onCreateNew = { creatingFolder = true },
            )
            if (state.category == null) {
                Text(
                    "No folder picked: it will go to Unsorted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNoteChange,
                label = { Text("Note (optional)") },
                enabled = !state.saving,
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Tags: type and press Enter or a comma.
            if (state.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTag(tag) },
                            enabled = !state.saving,
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.tagInput,
                onValueChange = vm::onTagInput,
                label = { Text("Tags (optional)") },
                placeholder = { Text("Type a tag, then Enter or comma") },
                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                singleLine = true,
                enabled = !state.saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.commitTagInput() }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            // Contacts to add once the link is saved.
            ContactsSection(
                contacts = state.contacts,
                onSave = vm::savePendingContact,
                onDelete = vm::deletePendingContact,
                enabled = !state.saving,
            )

            // Files to attach once the link is saved.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Attachments",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pickFiles.launch(arrayOf("*/*")) }, enabled = !state.saving) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Attach file")
                }
            }
            state.files.forEach { file ->
                AttachmentRow(
                    name = file.name,
                    mimeType = file.mimeType,
                    detail = file.size?.let { Formatter.formatShortFileSize(context, it) },
                    onClick = null,
                    onRemove = { vm.removeFile(file) },
                )
            }

            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
            state.duplicateId?.let { id ->
                TextButton(onClick = { vm.openExisting(id) }) { Text("Open the saved link") }
            }
            if (state.noLinkFound) {
                OutlinedButton(onClick = vm::saveAsNote, enabled = !state.saving) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Save as a note instead")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = vm::dismiss, enabled = !state.saving) { Text("Cancel") }
                Button(
                    onClick = vm::save,
                    enabled = !state.saving && state.url.isNotBlank(),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Saving…")
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (creatingFolder) {
        FolderDialog(
            title = "New folder",
            confirmLabel = "Create",
            existingNames = folders.map { it.name },
            onDismiss = { creatingFolder = false },
            onConfirm = { name, icon ->
                creatingFolder = false
                vm.createFolder(name, icon)
            },
        )
    }

    LaunchedEffect(Unit) {
        if (!state.fromShare) {
            delay(200)
            runCatching { urlFocus.requestFocus() }
        }
    }
}

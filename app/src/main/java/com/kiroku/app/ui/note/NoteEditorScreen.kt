package com.kiroku.app.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kiroku.app.ui.components.formatDateTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    vm: NoteEditorViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onArchived: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }

    LaunchedEffect(vm.gone) { if (vm.gone) onBack() }
    LaunchedEffect(vm.loaded) {
        if (vm.loaded && vm.isNew && vm.isBlank()) {
            delay(200)
            runCatching { bodyFocus.requestFocus() }
        }
    }

    val transparentField = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::togglePin) {
                        Icon(
                            if (vm.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (vm.pinned) "Unpin" else "Pin",
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (vm.archived) {
                                DropdownMenuItem(
                                    text = { Text("Unarchive") },
                                    leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                                    onClick = { menuOpen = false; vm.unarchive() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                    enabled = !vm.isBlank(),
                                    onClick = {
                                        menuOpen = false
                                        if (vm.archive()) {
                                            onArchived(vm.noteId)
                                            onBack()
                                        }
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.background,
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            if (vm.archived) {
                Surface(
                    color = colors.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("This note is archived", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::unarchive) { Text("Unarchive") }
                    }
                }
            }

            TextField(
                value = vm.title,
                onValueChange = vm::onTitle,
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onBackground),
                colors = transparentField,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { runCatching { bodyFocus.requestFocus() } }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextField(
                value = vm.body,
                onValueChange = vm::onBody,
                placeholder = { Text("Note") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
                colors = transparentField,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(bodyFocus),
            )

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Spacer(Modifier.height(16.dp))
                vm.updatedAt?.let {
                    Text(
                        "Edited ${formatDateTime(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("It will be removed from Kiroku. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                    onBack()
                }) {
                    Text("Delete", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

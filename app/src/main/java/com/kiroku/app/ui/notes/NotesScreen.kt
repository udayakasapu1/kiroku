package com.kiroku.app.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiroku.app.backup.BackupManager
import com.kiroku.app.ui.components.AddFab
import com.kiroku.app.ui.components.AppHeader
import com.kiroku.app.ui.components.EmptyMessage
import com.kiroku.app.ui.components.NoteGrid
import com.kiroku.app.ui.components.Space
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    snackbar: SnackbarHostState,
    onSwitchSpace: (Space) -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onOpenArchive: () -> Unit,
    onBack: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(vm) {
        vm.messages.collect { text -> launch { snackbar.showSnackbar(text) } }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFrom(uri) }

    Scaffold(
        topBar = {
            if (vm.archived) {
                TopAppBar(
                    title = { Text("Archived notes") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                )
            } else {
                AppHeader(
                    space = Space.NOTES,
                    onSwitchSpace = onSwitchSpace,
                    searchText = vm.searchText,
                    onQuery = vm::setQuery,
                    searchPlaceholder = "Search notes",
                    actions = {
                        IconButton(onClick = onOpenArchive) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Archived notes")
                        }
                    },
                    menuItems = { close ->
                        DropdownMenuItem(
                            text = { Text("Export backup") },
                            leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                            onClick = { close(); exportLauncher.launch(BackupManager.suggestedFileName()) },
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup") },
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                            onClick = {
                                close()
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            },
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (!vm.archived) AddFab("Note", Icons.Outlined.Description, onNewNote)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.background,
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val list = items
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() && vm.archived -> EmptyMessage(
                    icon = Icons.Outlined.Archive,
                    title = "No archived notes",
                    body = "Archive a note from its ⋮ menu to tuck it away here.",
                )
                list.isEmpty() && vm.searchText.isNotBlank() -> EmptyMessage(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    body = "None of your notes match \"${vm.searchText.trim()}\".",
                )
                list.isEmpty() -> EmptyMessage(
                    icon = Icons.Outlined.Description,
                    title = "Notes you add appear here",
                    body = "Tap Note to write one. It saves as you type.",
                )
                else -> NoteGrid(notes = list, onOpenNote = onOpenNote)
            }
        }
    }
}

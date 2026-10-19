package com.kiroku.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiroku.app.backup.BackupManager
import com.kiroku.app.data.FolderKeys
import com.kiroku.app.ui.components.AddFab
import com.kiroku.app.ui.components.AppHeader
import com.kiroku.app.ui.components.EmptyMessage
import com.kiroku.app.ui.components.FolderCard
import com.kiroku.app.ui.components.FolderDialog
import com.kiroku.app.ui.components.FolderIcons
import com.kiroku.app.ui.components.LinkGrid
import com.kiroku.app.ui.components.Space
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    snackbar: SnackbarHostState,
    onSwitchSpace: (Space) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onManageFolders: () -> Unit,
    onOpenWebInbox: () -> Unit,
    onAddLink: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching = vm.searchText.isNotBlank()
    var newFolder by rememberSaveable { mutableStateOf(false) }

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
            AppHeader(
                space = Space.LINKS,
                onSwitchSpace = onSwitchSpace,
                searchText = vm.searchText,
                onQuery = vm::setQuery,
                searchPlaceholder = "Search links",
                actions = {
                    IconButton(onClick = { onOpenFolder(FolderKeys.ARCHIVED) }) {
                        Icon(Icons.Outlined.Archive, contentDescription = "Archived links")
                    }
                },
                menuItems = { close ->
                    DropdownMenuItem(
                        text = { Text("Manage folders") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                        onClick = { close(); onManageFolders() },
                    )
                    DropdownMenuItem(
                        text = { Text("Web inbox") },
                        leadingIcon = { Icon(Icons.Outlined.Language, null) },
                        onClick = { close(); onOpenWebInbox() },
                    )
                    DropdownMenuItem(
                        text = { Text("Retry fetching details") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                        onClick = { close(); vm.retryPending() },
                    )
                    HorizontalDivider()
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
        },
        floatingActionButton = { AddFab("Link", Icons.Filled.Link, onAddLink) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val current = state
            val found = results
            when {
                searching && found == null -> Loading()
                searching && found.isNullOrEmpty() -> EmptyMessage(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    body = "None of your links match \"${vm.searchText.trim()}\".",
                )
                searching && found != null -> {
                    val byId = current?.categories.orEmpty().associateBy { it.id }
                    LinkGrid(
                        links = found,
                        gridLayout = true,
                        onOpenLink = onOpenLink,
                        folderOf = { link ->
                            val c = link.category?.let { byId[it] }
                            if (c == null) "Unsorted" to FolderIcons.unsorted else c.name to FolderIcons.of(c.icon)
                        },
                    )
                }
                current == null -> Loading()
                else -> FolderGrid(current, onOpenFolder, onNewFolder = { newFolder = true })
            }
        }
    }

    if (newFolder) {
        FolderDialog(
            title = "New folder",
            confirmLabel = "Create",
            existingNames = state?.categories.orEmpty().map { it.name },
            onDismiss = { newFolder = false },
            onConfirm = { name, icon ->
                newFolder = false
                vm.createFolder(name, icon)
            },
        )
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun FolderGrid(state: HomeState, onOpenFolder: (String) -> Unit, onNewFolder: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = when {
                    state.folders.isEmpty() -> "No folders yet. Create one to sort your links."
                    state.totalLinks == 0 -> "Tap Link, or share a page to Kiroku, to save your first link."
                    else -> "Folders · ${state.totalLinks} links"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(state.folders, key = { it.key }) { tile ->
            val category = tile.category
            FolderCard(
                name = category?.name ?: "Unsorted",
                icon = if (category == null) FolderIcons.unsorted else FolderIcons.of(category.icon),
                links = tile.links,
                onClick = { onOpenFolder(tile.key) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = onNewFolder) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Text("  New folder")
                }
            }
        }
    }
}

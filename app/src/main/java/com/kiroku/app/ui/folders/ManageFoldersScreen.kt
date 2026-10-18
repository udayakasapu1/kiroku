package com.kiroku.app.ui.folders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.ui.components.EmptyMessage
import com.kiroku.app.ui.components.FolderDialog
import com.kiroku.app.ui.components.FolderIcons
import com.kiroku.app.ui.components.linkCountLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFoldersScreen(vm: ManageFoldersViewModel, onBack: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<Pair<CategoryEntity, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New folder") },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            )
        },
        containerColor = colors.background,
    ) { padding ->
        val list = folders
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> EmptyMessage(
                    icon = Icons.Outlined.CreateNewFolder,
                    title = "No folders yet",
                    body = "Create folders to sort your links. Links without a folder go to Unsorted.",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 104.dp)) {
                    item {
                        Text(
                            "Tap a folder to rename it or change its icon. Deleting a folder moves its links to Unsorted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    itemsIndexed(list, key = { _, f -> f.category.id }) { index, folder ->
                        val category = folder.category
                        ListItem(
                            leadingContent = {
                                Icon(
                                    FolderIcons.of(category.icon).vector,
                                    contentDescription = null,
                                    tint = colors.primary,
                                )
                            },
                            headlineContent = { Text(category.name) },
                            supportingContent = { Text(linkCountLabel(folder.links)) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { vm.move(category, -1) }, enabled = index > 0) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${category.name} up")
                                    }
                                    IconButton(onClick = { vm.move(category, 1) }, enabled = index < list.lastIndex) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${category.name} down")
                                    }
                                    IconButton(onClick = { deleting = category to folder.links }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete ${category.name}")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = colors.background),
                            modifier = Modifier.clickable(onClickLabel = "Edit ${category.name}") { editing = category },
                        )
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
            }
        }
    }

    if (creating) {
        FolderDialog(
            title = "New folder",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name, icon ->
                creating = false
                vm.create(name, icon)
            },
        )
    }

    editing?.let { category ->
        FolderDialog(
            title = "Edit folder",
            initialName = category.name,
            initialIcon = category.icon,
            onDismiss = { editing = null },
            onConfirm = { name, icon ->
                editing = null
                vm.update(category, name, icon)
            },
        )
    }

    deleting?.let { (category, count) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = {
                Text(
                    if (count == 0) "The folder is empty."
                    else "Its ${linkCountLabel(count)} won't be deleted. They move to Unsorted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.delete(category)
                }) { Text("Delete folder", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

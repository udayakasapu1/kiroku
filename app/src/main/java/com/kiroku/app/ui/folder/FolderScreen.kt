package com.kiroku.app.ui.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiroku.app.ui.components.AddFab
import com.kiroku.app.ui.components.EmptyMessage
import com.kiroku.app.ui.components.FolderHeader
import com.kiroku.app.ui.components.FolderIcons
import com.kiroku.app.ui.components.LinkGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    vm: FolderViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onAddLink: (String?) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    var gridLayout by rememberSaveable { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val category = state.category
    val name = when {
        vm.isArchive -> "Archived links"
        category != null -> category.name
        else -> "Unsorted"
    }
    val icon = if (category != null) FolderIcons.of(category.icon) else FolderIcons.unsorted

    val layoutToggle: @Composable () -> Unit = {
        IconButton(onClick = { gridLayout = !gridLayout }) {
            Icon(
                if (gridLayout) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                contentDescription = if (gridLayout) "List view" else "Grid view",
            )
        }
    }

    Scaffold(
        topBar = {
            if (vm.isArchive) {
                TopAppBar(
                    title = { Text(name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = { layoutToggle() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                )
            } else {
                FolderHeader(
                    name = name,
                    icon = icon,
                    links = state.links.size,
                    onBack = onBack,
                    actions = { layoutToggle() },
                )
            }
        },
        floatingActionButton = {
            if (!vm.isArchive) AddFab("Link", Icons.Filled.Link) { onAddLink(vm.categoryId) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    val count = vm.retryPending()
                    delay(600)
                    refreshing = false
                    if (count > 0) snackbar.showSnackbar("Retrying details for $count link(s)")
                }
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.links.isEmpty() && vm.isArchive -> EmptyMessage(
                    icon = Icons.Outlined.Archive,
                    title = "No archived links",
                    body = "Archive a link from its ⋮ menu to tuck it away here.",
                )
                state.links.isEmpty() -> EmptyMessage(
                    icon = icon.vector,
                    title = "$name is empty",
                    body = "Tap Link, or share a page to Kiroku and pick $name.",
                )
                else -> {
                    val byId = state.categories.associateBy { it.id }
                    val labelFor: (com.kiroku.app.data.LinkEntity) -> Pair<String, com.kiroku.app.ui.components.FolderIcon> = { link ->
                        val c = link.category?.let { byId[it] }
                        if (c == null) "Unsorted" to FolderIcons.unsorted else c.name to FolderIcons.of(c.icon)
                    }
                    LinkGrid(
                        links = state.links,
                        gridLayout = gridLayout,
                        onOpenLink = onOpenLink,
                        modifier = Modifier.padding(top = 4.dp),
                        folderOf = if (vm.isArchive) labelFor else null,
                    )
                }
            }
        }
    }
}

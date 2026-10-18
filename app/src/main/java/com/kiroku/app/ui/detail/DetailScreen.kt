package com.kiroku.app.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kiroku.app.data.FetchState
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.Status
import com.kiroku.app.ui.components.CategoryPicker
import com.kiroku.app.ui.components.FolderDialog
import com.kiroku.app.ui.components.FolderIcons
import com.kiroku.app.ui.components.FolderLabel
import com.kiroku.app.ui.components.PreviewImage
import com.kiroku.app.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: DetailViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onArchived: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val item = state.item
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Item deleted (or id unknown): leave the screen once.
    val gone = !state.loading && item == null
    LaunchedEffect(gone) { if (gone) onBack() }

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
                    if (item != null) {
                        IconButton(onClick = vm::togglePin) {
                            Icon(
                                if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (item.pinned) "Unpin" else "Pin",
                            )
                        }
                        IconButton(onClick = { shareLink(context, item) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share link")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (item.status == Status.ARCHIVED) {
                                    DropdownMenuItem(
                                        text = { Text("Unarchive") },
                                        leadingIcon = { Icon(Icons.Outlined.Unarchive, null) },
                                        onClick = { menuOpen = false; vm.unarchive() },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Archive") },
                                        leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                        onClick = {
                                            menuOpen = false
                                            vm.archive()
                                            onArchived(vm.id)
                                            onBack()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Refresh details") },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                    onClick = { menuOpen = false; vm.retryFetch() },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            DetailContent(
                item = item,
                vm = vm,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this link?") },
            text = { Text("It will be removed from Kiroku. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(item: LinkEntity, vm: DetailViewModel, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folder = folders.firstOrNull { it.id == item.category }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }

    // Local editing state, initialised from the database once per item.
    var title by rememberSaveable(item.id) { mutableStateOf(item.title.orEmpty()) }
    var titleEdited by rememberSaveable(item.id) { mutableStateOf(false) }
    var notes by rememberSaveable(item.id) { mutableStateOf(item.notes) }
    var newTag by rememberSaveable(item.id) { mutableStateOf("") }

    // If details arrive from the network while the screen is open, show them
    // (unless the title has already been edited by hand).
    LaunchedEffect(item.title) {
        if (!titleEdited) title = item.title.orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (item.status == Status.ARCHIVED) {
            Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Archive, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("This link is archived", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::unarchive) { Text("Unarchive") }
                }
            }
        }

        item.previewImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            PreviewImage(
                url = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.91f)
                    .clip(MaterialTheme.shapes.large),
            )
        }

        FetchBanner(item, onRetry = vm::retryFetch)

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (folder != null) FolderLabel(folder.name, FolderIcons.of(folder.icon))
            else FolderLabel("Unsorted", FolderIcons.unsorted)
            Text("  ·  ", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Text(
                text = listOfNotNull(item.siteName, item.sourceDomain).distinct().joinToString(" · ")
                    .ifEmpty { item.url },
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextField(
            value = title,
            onValueChange = {
                title = it
                titleEdited = true
                vm.setTitle(it)
            },
            placeholder = { Text("Add a title", style = MaterialTheme.typography.headlineSmall) },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = colors.onBackground),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = colors.primary,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        item.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }

        Text(
            text = item.url,
            style = MaterialTheme.typography.bodySmall,
            color = colors.primary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { openUrl(context, item.url) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openUrl(context, item.url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open in browser")
            }
            OutlinedButton(onClick = { shareLink(context, item) }) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Share")
            }
        }

        SectionLabel("Folder")
        CategoryPicker(
            categories = folders,
            selected = folder?.id,
            onSelect = vm::setCategory,
            onCreateNew = { creatingFolder = true },
        )

        SectionLabel("Tags")
        if (item.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTag(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = "Remove tag $tag", modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it.replace("\n", "") },
            placeholder = { Text("Add a tag and press Enter") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                newTag.split(',').forEach { vm.addTag(it) }
                newTag = ""
            }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel("Notes")
        OutlinedTextField(
            value = notes,
            onValueChange = {
                notes = it
                vm.setNotes(it)
            },
            placeholder = { Text("Why is this worth keeping?") },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Saved ${formatDateTime(item.createdAt)} · Edited ${formatDateTime(item.updatedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (creatingFolder) {
        FolderDialog(
            title = "New folder",
            confirmLabel = "Create",
            onDismiss = { creatingFolder = false },
            onConfirm = { name, icon ->
                creatingFolder = false
                vm.createFolder(name, icon)
            },
        )
    }
}

@Composable
private fun FetchBanner(item: LinkEntity, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (item.fetchState) {
        FetchState.PENDING -> Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    "Fetching the page's title, description and image. This happens automatically when you're online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        FetchState.FAILED -> Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "Couldn't fetch details: ${item.fetchError ?: "unknown error"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun shareLink(context: Context, item: LinkEntity) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, item.url)
        item.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    context.startActivity(Intent.createChooser(send, null))
}

package com.kiroku.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kiroku.app.data.FetchState
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.NoteEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkCard(
    item: LinkEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in search results and the archive, where links from many folders mix. */
    folder: Pair<String, FolderIcon>? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        item.previewImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            PreviewImage(url, Modifier.fillMaxWidth().aspectRatio(1.91f))
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayDomain(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.pinned) PinMark()
            }
            Text(
                text = item.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (item.fetchState) {
                FetchState.PENDING -> FetchingLabel()
                FetchState.FAILED -> Text(
                    "Details unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error,
                )
            }
            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item.tags.take(4).forEach { TagPill(it) }
                }
            }
            CardFooter(
                folder = folder,
                time = item.createdAt,
            )
        }
    }
}

@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (note.title.isNotBlank() || note.pinned) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.pinned) PinMark()
                }
            }
            if (note.body.isNotBlank()) {
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CardFooter(
                folder = null,
                time = note.updatedAt,
            )
        }
    }
}

@Composable
private fun CardFooter(folder: Pair<String, FolderIcon>?, time: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (folder != null) {
            FolderLabel(folder.first, folder.second, Modifier.weight(1f, fill = false))
            Spacer(Modifier.size(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = relativeTime(time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinMark() {
    Icon(
        Icons.Filled.PushPin,
        contentDescription = "Pinned",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
fun PreviewImage(url: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun FetchingLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        Spacer(Modifier.size(6.dp))
        Text(
            "Fetching details…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagPill(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Text(
            "#$tag",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

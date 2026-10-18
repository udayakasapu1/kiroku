package com.kiroku.app.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiroku.app.data.AttachmentEntity

/** Picks an icon from the file's type or name. */
fun attachmentIcon(mimeType: String?, name: String): ImageVector {
    val mime = mimeType.orEmpty()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/pdf" || ext == "pdf" -> Icons.Outlined.PictureAsPdf
        mime.startsWith("image/") -> Icons.Outlined.Image
        mime.contains("spreadsheet") || mime.contains("excel") || ext in setOf("xls", "xlsx", "csv", "ods") ->
            Icons.Outlined.TableChart
        mime.startsWith("text/") || mime.contains("word") || ext in setOf("doc", "docx", "txt", "md", "odt", "rtf") ->
            Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}

/** "Attachments" heading, an "Attach file" button and the attached files. */
@Composable
fun AttachmentsSection(
    attachments: List<AttachmentEntity>,
    attaching: Boolean,
    onAdd: () -> Unit,
    onOpen: (AttachmentEntity) -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf<AttachmentEntity?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Attachments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (attaching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            TextButton(onClick = onAdd, enabled = !attaching) {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Attach file")
            }
        }
        attachments.forEach { attachment ->
            AttachmentRow(
                name = attachment.name,
                mimeType = attachment.mimeType,
                detail = Formatter.formatShortFileSize(context, attachment.size),
                onClick = { onOpen(attachment) },
                onRemove = { confirmRemove = attachment },
            )
        }
    }

    confirmRemove?.let { attachment ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove attachment?") },
            text = { Text("\"${attachment.name}\" will be removed from Kiroku. The original in your files isn't touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    onRemove(attachment)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

/** One file: icon, name, size; tap to open (when [onClick] is set), ✕ to remove. */
@Composable
fun AttachmentRow(
    name: String,
    mimeType: String?,
    detail: String?,
    onClick: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(attachmentIcon(mimeType, name), contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove $name", modifier = Modifier.size(18.dp))
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant),
            content = content,
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant),
            content = content,
        )
    }
}

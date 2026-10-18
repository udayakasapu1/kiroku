package com.kiroku.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kiroku.app.data.CategoryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AddFab(label: String, icon: ImageVector, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

/**
 * One chip per folder. Tap the selected chip again to clear (= Unsorted).
 * [onCreateNew], when given, adds a "New folder" chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<CategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCreateNew: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        categories.forEach { category ->
            val isSelected = selected == category.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category.id) },
                enabled = enabled,
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        FolderIcons.of(category.icon).vector,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    iconColor = colors.primary,
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    selectedLeadingIconColor = colors.onPrimary,
                ),
            )
        }
        if (onCreateNew != null) {
            AssistChip(
                onClick = onCreateNew,
                enabled = enabled,
                label = { Text("New folder") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
fun EmptyMessage(
    icon: ImageVector,
    title: String,
    body: String,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

private val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatDateTime(millis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

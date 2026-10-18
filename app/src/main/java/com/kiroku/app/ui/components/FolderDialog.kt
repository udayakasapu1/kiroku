package com.kiroku.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kiroku.app.data.CategoryRepository
import kotlinx.coroutines.delay

/** Create or edit a folder: a name and an icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderDialog(
    title: String,
    initialName: String = "",
    initialIcon: String = FolderIcons.all.first().key,
    confirmLabel: String = "Save",
    /** Names of the other folders; a matching name (ignoring case/spaces) is refused. */
    existingNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon) }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val taken = remember(existingNames) { existingNames.map { CategoryRepository.nameKey(it) }.toSet() }
    val duplicate = name.isNotBlank() && CategoryRepository.nameKey(name) in taken
    val canSave = name.isNotBlank() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(CategoryRepository.MAX_NAME) },
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("You already have a folder with this name") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name, icon) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Text("Icon", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FolderIcons.all.forEach { option ->
                        val selected = option.key == icon
                        Surface(
                            onClick = { icon = option.key },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) colors.primary else colors.surfaceContainerHigh,
                            contentColor = if (selected) colors.onPrimary else colors.primary,
                            border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = option.label + if (selected) ", selected" else ""
                                },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(option.vector, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon) }, enabled = canSave) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
}

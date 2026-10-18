package com.kiroku.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kiroku.app.R

enum class Space(val title: String) { LINKS("Links"), NOTES("Notes") }

/** The logo tile colour (same as the app icon background). */
private val KirokuInk = Color(0xFF1F2937)

/**
 * Top of the Links and Notes home screens. Tapping the logo opens a menu to switch space.
 * [menuItems] fills the ⋮ menu; call `close()` before acting.
 */
@Composable
fun AppHeader(
    space: Space,
    onSwitchSpace: (Space) -> Unit,
    searchText: String,
    onQuery: (String) -> Unit,
    searchPlaceholder: String,
    actions: @Composable () -> Unit = {},
    menuItems: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var switcherOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Surface(
                    onClick = { switcherOpen = true },
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = KirokuInk, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_kiroku_mark),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text("Kiroku", style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
                            Text(space.title, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Switch between Links and Notes",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                DropdownMenu(expanded = switcherOpen, onDismissRequest = { switcherOpen = false }) {
                    Space.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.title) },
                            leadingIcon = {
                                Icon(
                                    if (option == Space.LINKS) Icons.Filled.Link else Icons.Outlined.Description,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                if (option == space) Icon(Icons.Filled.Check, contentDescription = "Current")
                            },
                            onClick = {
                                switcherOpen = false
                                if (option != space) onSwitchSpace(option)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menuItems { menuOpen = false }
                }
            }
        }

        TextField(
            value = searchText,
            onValueChange = onQuery,
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

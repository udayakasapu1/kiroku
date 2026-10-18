package com.kiroku.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kiroku.app.R
import com.kiroku.app.ui.components.FolderDialog
import com.kiroku.app.ui.components.FolderIcons
import com.kiroku.app.ui.components.LogoTile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var adding by rememberSaveable { mutableStateOf(false) }

    Surface(color = colors.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LogoTile(size = 64.dp)
                Text("Set up your folders", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
                Text(
                    "Kiroku sorts your saved links into folders. Pick a few to start with, or add your own. " +
                        "You can rename, add or remove folders any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    vm.options.forEach { option ->
                        val isSelected = option in vm.selected
                        FilterChip(
                            selected = isSelected,
                            onClick = { vm.toggle(option) },
                            label = { Text(option.name) },
                            leadingIcon = {
                                Icon(FolderIcons.of(option.icon).vector, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                iconColor = colors.primary,
                                selectedContainerColor = colors.primary,
                                selectedLabelColor = colors.onPrimary,
                                selectedLeadingIconColor = colors.onPrimary,
                            ),
                        )
                    }
                    AssistChip(
                        onClick = { adding = true },
                        label = { Text("Add your own") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.selected.isNotEmpty()) {
                    Text(
                        "${vm.selected.size} selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { vm.selected.clear(); vm.finish(onDone) }, enabled = !vm.saving) {
                    Text("Skip")
                }
                Button(onClick = { vm.finish(onDone) }, enabled = !vm.saving && vm.selected.isNotEmpty()) {
                    Text("Continue")
                }
            }
        }
    }

    if (adding) {
        FolderDialog(
            title = "Add a folder",
            confirmLabel = "Add",
            onDismiss = { adding = false },
            onConfirm = { name, icon ->
                adding = false
                vm.addCustom(name, icon)
            },
        )
    }
}

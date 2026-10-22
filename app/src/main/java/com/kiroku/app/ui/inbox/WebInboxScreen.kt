package com.kiroku.app.ui.inbox

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kiroku.app.inbox.WebInbox
import com.kiroku.app.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebInboxScreen(vm: WebInboxViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Web inbox") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "See and save your links from a laptop's browser. Links saved there wait in your Supabase " +
                    "project until Kiroku picks them up: when you open or leave the app, and about every 15 minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            if (!vm.configured) {
                Step("1", "Run the setup script in Supabase")
                Text(
                    "In your Supabase project: SQL Editor → New query → paste the script → Run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                OutlinedButton(onClick = { open(context, WebInbox.SETUP_SCRIPT) }) { Text("Open the setup script") }

                Step("2", "Enter your project details")
                Text(
                    "Supabase → Project Settings → API: copy the Project URL and the anon public key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = vm.projectUrl,
                    onValueChange = { vm.projectUrl = it },
                    label = { Text("Project URL") },
                    placeholder = { Text("https://abcd1234.supabase.co") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.anonKey,
                    onValueChange = { vm.anonKey = it.trim() },
                    label = { Text("anon public key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = vm::connect,
                    enabled = !vm.busy && vm.projectUrl.isNotBlank() && vm.anonKey.isNotBlank(),
                ) {
                    if (vm.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text("Connect")
                }
                if (vm.lastError.isNotBlank()) ErrorLine(vm.lastError)
            } else {
                Status(vm.lastCheck, vm.lastError)

                Step("→", "Connect a browser")
                Text(
                    "Send this link to your laptop (email, chat, notes…) and open it there once. " +
                        "Treat it like a password: anyone with it can add links to your inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { share(context, vm.setupLink()) }) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Share link")
                    }
                    OutlinedButton(onClick = { copy(context, vm.setupLink()) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Copy")
                    }
                }
                Text(
                    "On the laptop the page also gives you a \"Save to Kiroku\" button for the bookmarks bar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show all my links on the web page", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Keeps a copy of your links (titles, folders, tags, notes, contacts) in your Supabase " +
                                    "project so the web page can show and edit them. Attached files stay on the phone. " +
                                    "Turning this off removes the copy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Switch(checked = vm.mirror, onCheckedChange = vm::changeMirror, enabled = !vm.busy)
                    }
                }

                OutlinedButton(onClick = vm::checkNow, enabled = !vm.busy) {
                    if (vm.busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text("Sync now")
                }

                HorizontalDivider()
                Text("Project: ${vm.projectUrl}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = { confirmReset = true }) { Text("Disconnect all browsers (new setup link)") }
                TextButton(onClick = { confirmOff = true }) { Text("Turn off web inbox", color = colors.error) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Disconnect all browsers?") },
            text = { Text("Browsers you connected stop working until you send them a new setup link. Links still waiting in the old inbox are dropped.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetKey() }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn off web inbox?") },
            text = { Text("Kiroku stops checking for links from the browser. Your saved links stay on this phone.") },
            confirmButton = { TextButton(onClick = { confirmOff = false; vm.turnOff() }) { Text("Turn off", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Step(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small, modifier = Modifier.size(24.dp)) {
            Box24 { Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
        }
        Spacer(Modifier.size(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Box24(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Status(lastCheck: Long, error: String) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (error.isBlank()) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (error.isBlank()) colors.primary else colors.error,
            )
            Spacer(Modifier.size(10.dp))
            Column {
                Text(if (error.isBlank()) "Connected" else "Last check failed", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        error.isNotBlank() -> error
                        lastCheck > 0 -> "Last checked ${formatDateTime(lastCheck)}"
                        else -> "Not checked yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private fun share(context: Context, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Kiroku web inbox setup link")
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(send, "Send the setup link to your laptop"))
}

private fun copy(context: Context, link: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kiroku setup link", link))
    Toast.makeText(context, "Setup link copied", Toast.LENGTH_SHORT).show()
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

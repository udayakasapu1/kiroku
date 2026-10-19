package com.kiroku.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiroku.app.data.ContactEntity
import com.kiroku.app.data.ContactRepository

/**
 * "Contacts" heading with an "Add contact" button and one card per contact.
 * Tap a card to edit it; the icons email, call or open the address in Maps.
 */
@Composable
fun ContactsSection(
    contacts: List<ContactEntity>,
    onSave: (ContactEntity) -> Unit,
    onDelete: (ContactEntity) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var editing by remember { mutableStateOf<ContactEntity?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Contacts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { editing = ContactRepository.draft() }, enabled = enabled) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add contact")
            }
        }
        contacts.forEach { contact ->
            ContactCard(contact = contact, onClick = { if (enabled) editing = contact })
        }
    }

    editing?.let { contact ->
        ContactDialog(
            initial = contact,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                onSave(updated)
            },
            onDelete = if (contact.id.isEmpty()) null else {
                {
                    editing = null
                    onDelete(contact)
                }
            },
        )
    }
}

@Composable
private fun ContactCard(contact: ContactEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    contact.name.ifBlank { "Unnamed contact" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (contact.name.isBlank()) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                listOf(contact.email, contact.phone, contact.address).filter { it.isNotBlank() }.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (contact.email.isNotBlank()) {
                IconButton(onClick = { startOrToast(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + contact.email))) }) {
                    Icon(Icons.Outlined.Email, contentDescription = "Email ${contact.name}")
                }
            }
            if (contact.phone.isNotBlank()) {
                IconButton(onClick = { startOrToast(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + contact.phone.filter { it.isDigit() || it == '+' }))) }) {
                    Icon(Icons.Outlined.Phone, contentDescription = "Call ${contact.name}")
                }
            }
            if (contact.address.isNotBlank()) {
                IconButton(onClick = { startOrToast(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(contact.address)))) }) {
                    Icon(Icons.Outlined.Factory, contentDescription = "Show facility on a map")
                }
            }
        }
    }
}

/** Add or edit a contact. [onDelete] is null for a new contact. */
@Composable
fun ContactDialog(
    initial: ContactEntity,
    onDismiss: () -> Unit,
    onSave: (ContactEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var address by rememberSaveable { mutableStateOf(initial.address) }
    var confirmDelete by remember { mutableStateOf(false) }

    val emailInvalid = email.isNotBlank() && !email.trim().matches(Regex("""[^\s@]+@[^\s@]+\.[^\s@]+"""))
    val empty = name.isBlank() && email.isBlank() && phone.isBlank() && address.isBlank()
    val canSave = !empty && !emailInvalid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Add contact" else "Edit contact") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact name") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    singleLine = true,
                    isError = emailInvalid,
                    supportingText = if (emailInvalid) {
                        { Text("Check the email address") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Facility address") },
                    leadingIcon = { Icon(Icons.Outlined.Factory, contentDescription = null) },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete contact", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name, email = email, phone = phone, address = address)) },
                enabled = canSave,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this contact?") },
            text = { Text("\"${initial.name.ifBlank { "Unnamed contact" }}\" will be removed from this link.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun startOrToast(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can do that", Toast.LENGTH_SHORT).show()
    }
}

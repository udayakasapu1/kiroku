package com.kiroku.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** A file picked but not attached yet (e.g. in the save-a-link sheet). */
data class PendingFile(val uri: Uri, val name: String, val mimeType: String?, val size: Long?)

class AttachmentRepository(private val context: Context, private val dao: AttachmentDao) {

    private val dir: File
        get() = File(context.filesDir, "attachments").apply { mkdirs() }

    fun observeFor(type: String, ownerId: String): Flow<List<AttachmentEntity>> = dao.observeFor(type, ownerId)
    suspend fun countFor(type: String, ownerId: String): Int = dao.countFor(type, ownerId)
    fun fileOf(attachment: AttachmentEntity): File = File(dir, attachment.fileName)

    /** Name, type and size of a picked file, without copying it. */
    suspend fun describe(uri: Uri): PendingFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !c.isNull(nameIndex)) name = c.getString(nameIndex)
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
                }
            }
        }
        PendingFile(uri, name ?: uri.lastPathSegment ?: "File", resolver.getType(uri), size)
    }

    /** Copies the picked file into Kiroku and attaches it to the link or note. */
    suspend fun attach(type: String, ownerId: String, uri: Uri): AttachmentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !c.isNull(nameIndex)) name = c.getString(nameIndex)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
            }
        }
        val displayName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "File"
        val id = UUID.randomUUID().toString()
        val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 8 }
        val fileName = if (extension != null) "$id.$extension" else id
        val target = File(dir, fileName)

        val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't open the file")
        val copied = try {
            input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        val entity = AttachmentEntity(
            id = id,
            ownerType = type,
            ownerId = ownerId,
            name = displayName,
            mimeType = resolver.getType(uri),
            size = if (size >= 0) size else copied,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
        entity
    }

    suspend fun remove(attachment: AttachmentEntity) = withContext(Dispatchers.IO) {
        fileOf(attachment).delete()
        dao.delete(attachment.id)
    }

    /** Removes every file of a link or note (used when it's deleted). */
    suspend fun removeAllFor(type: String, ownerId: String) = withContext(Dispatchers.IO) {
        dao.getFor(type, ownerId).forEach { fileOf(it).delete() }
        dao.deleteFor(type, ownerId)
    }

    /** An intent that opens the file in a suitable app (PDF viewer, Office, gallery…). */
    fun openIntent(attachment: AttachmentEntity): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", fileOf(attachment))
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType ?: "*/*")
            clipData = ClipData.newRawUri(attachment.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

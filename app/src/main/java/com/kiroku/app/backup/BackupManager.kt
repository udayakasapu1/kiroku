package com.kiroku.app.backup

import android.content.Context
import android.net.Uri
import com.kiroku.app.data.CategoryEntity
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.FolderSuggestions
import com.kiroku.app.data.FetchState
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.NoteEntity
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.data.Status
import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class ImportResult(val links: Int, val notes: Int, val folders: Int, val skipped: Int)

/**
 * JSON backup of folders, links and notes. Link keys use the same snake_case names as the
 * Supabase `knowledge_items` table. Also reads older Kiroku and Gurthu backups.
 */
class BackupManager(
    private val context: Context,
    private val links: LinkRepository,
    private val notes: NoteRepository,
    private val categories: CategoryRepository,
) {

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val linkItems = links.allItems()
        val noteItems = notes.allNotes()
        val folderItems = categories.getAll()
        val root = JSONObject()
            .put("app", "kiroku")
            .put("format_version", 3)
            .put("folders", JSONArray().also { array -> folderItems.forEach { array.put(it.toJson()) } })
            .put("exported_at", Instant.now().toString())
            .put("items", JSONArray().also { array -> linkItems.forEach { array.put(it.toJson()) } })
            .put("notes", JSONArray().also { array -> noteItems.forEach { array.put(it.toJson()) } })
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Couldn't open the file for writing")
        stream.use { it.write(root.toString(2).toByteArray()) }
        linkItems.size + noteItems.size
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Couldn't open the file")
        val text = stream.bufferedReader().use { it.readText() }.trim()
        val linkArray: JSONArray?
        val noteArray: JSONArray?
        val folderArray: JSONArray?
        if (text.startsWith("[")) {
            linkArray = JSONArray(text)
            noteArray = null
            folderArray = null
        } else {
            val root = JSONObject(text)
            linkArray = root.optJSONArray("items")
            noteArray = root.optJSONArray("notes")
            folderArray = root.optJSONArray("folders")
        }
        if (linkArray == null && noteArray == null) throw IOException("No links or notes found in this file")

        var importedLinks = 0
        var importedNotes = 0
        var importedFolders = 0
        var skipped = 0

        // Folder ids in the backup -> folder ids here (a same-named folder is reused, never duplicated).
        val folderIds = mutableMapOf<String, String>()
        if (folderArray != null) {
            for (i in 0 until folderArray.length()) {
                val folder = folderArray.optJSONObject(i)?.let { parseFolder(it) } ?: continue
                val sameName = categories.findByName(folder.name)
                when {
                    sameName != null -> folderIds[folder.id] = sameName.id
                    categories.insertImported(folder) -> {
                        importedFolders++
                        folderIds[folder.id] = folder.id
                    }
                    else -> folderIds[folder.id] = folder.id // same id already here
                }
            }
        }
        // Map whatever the backup calls a folder to a folder that exists here.
        suspend fun resolveFolder(value: String?): String? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            folderIds[v]?.let { return it }
            val known = categories.getAll()
            known.firstOrNull { it.id == v }?.let { return it.id }
            categories.findByName(v)?.let { return it.id }
            // Older Kiroku backups used built-in keys ("oem", "tier1", …).
            val legacy = FolderSuggestions.legacy.firstOrNull { it.first == v.lowercase() } ?: return null
            categories.findByName(legacy.second)?.let {
                folderIds[v] = it.id
                return it.id
            }
            val created = CategoryEntity(legacy.first, legacy.second, legacy.third, known.size, System.currentTimeMillis())
            if (categories.insertImported(created)) importedFolders++
            folderIds[v] = created.id
            return created.id
        }

        if (linkArray != null) {
            for (i in 0 until linkArray.length()) {
                val item = linkArray.optJSONObject(i)?.let { o ->
                    parseLink(o)?.copy(category = resolveFolder(o.str("category")))
                }
                if (item == null || links.findByUrl(item.url) != null || !links.insertImported(item)) skipped++
                else importedLinks++
            }
        }
        if (noteArray != null) {
            for (i in 0 until noteArray.length()) {
                val note = noteArray.optJSONObject(i)?.let { parseNote(it) }
                if (note == null || !notes.insertImported(note)) skipped++ else importedNotes++
            }
        }
        ImportResult(importedLinks, importedNotes, importedFolders, skipped)
    }

    private fun parseLink(obj: JSONObject): LinkEntity? {
        val url = obj.str("url")?.let { UrlUtils.normalize(it) } ?: return null
        val now = System.currentTimeMillis()
        val title = obj.str("title")
        val tagsArray = obj.optJSONArray("tags")
        val tags = if (tagsArray == null) emptyList() else
            List(tagsArray.length()) { tagsArray.optString(it) }.filter { it.isNotBlank() }
        return LinkEntity(
            id = validId(obj.str("id")),
            url = url,
            title = title,
            description = obj.str("description"),
            category = null, // resolved by import()
            subcategory = obj.str("subcategory"),
            tags = tags,
            sourceDomain = obj.str("source_domain") ?: UrlUtils.domainOf(url),
            previewImageUrl = obj.str("preview_image_url"),
            siteName = obj.str("site_name"),
            notes = obj.str("notes").orEmpty(),
            status = statusOf(obj),
            pinned = obj.optBoolean("pinned", false),
            fetchState = if (title != null) FetchState.DONE else FetchState.PENDING,
            createdAt = parseTime(obj.str("created_at")) ?: now,
            updatedAt = parseTime(obj.str("updated_at")) ?: now,
        )
    }

    private fun parseNote(obj: JSONObject): NoteEntity? {
        val title = obj.str("title").orEmpty()
        val body = if (obj.isNull("body")) "" else obj.optString("body")
        if (title.isBlank() && body.isBlank()) return null
        val now = System.currentTimeMillis()
        return NoteEntity(
            id = validId(obj.str("id")),
            title = title,
            body = body,
            pinned = obj.optBoolean("pinned", false),
            status = statusOf(obj),
            createdAt = parseTime(obj.str("created_at")) ?: now,
            updatedAt = parseTime(obj.str("updated_at")) ?: now,
        )
    }

    private fun LinkEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("url", url)
        .put("title", title ?: JSONObject.NULL)
        .put("description", description ?: JSONObject.NULL)
        .put("category", category ?: JSONObject.NULL)
        .put("subcategory", subcategory ?: JSONObject.NULL)
        .put("tags", JSONArray(tags))
        .put("source_domain", sourceDomain ?: JSONObject.NULL)
        .put("preview_image_url", previewImageUrl ?: JSONObject.NULL)
        .put("site_name", siteName ?: JSONObject.NULL)
        .put("notes", notes)
        .put("status", status)
        .put("pinned", pinned)
        .put("created_at", Instant.ofEpochMilli(createdAt).toString())
        .put("updated_at", Instant.ofEpochMilli(updatedAt).toString())

    private fun parseFolder(obj: JSONObject): CategoryEntity? {
        val id = obj.str("id") ?: return null
        val name = obj.str("name") ?: return null
        return CategoryEntity(
            id = id,
            name = name.take(CategoryRepository.MAX_NAME),
            icon = obj.str("icon") ?: "folder",
            position = obj.optInt("position", 0),
            createdAt = parseTime(obj.str("created_at")) ?: System.currentTimeMillis(),
        )
    }

    private fun CategoryEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("icon", icon)
        .put("position", position)
        .put("created_at", Instant.ofEpochMilli(createdAt).toString())

    private fun NoteEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("body", body)
        .put("pinned", pinned)
        .put("status", status)
        .put("created_at", Instant.ofEpochMilli(createdAt).toString())
        .put("updated_at", Instant.ofEpochMilli(updatedAt).toString())

    private fun statusOf(obj: JSONObject): String =
        if (obj.str("status") == Status.ARCHIVED) Status.ARCHIVED else Status.SAVED

    private fun validId(value: String?): String =
        value?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString()

    /** optString returns "null" for JSON nulls; this returns a real null instead. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun parseTime(value: String?): Long? {
        if (value == null) return null
        val iso = value.replace(' ', 'T')
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse("$iso:00").toInstant().toEpochMilli() }.getOrNull()
    }

    companion object {
        fun suggestedFileName(): String = "kiroku-backup-${LocalDate.now()}.json"
    }
}

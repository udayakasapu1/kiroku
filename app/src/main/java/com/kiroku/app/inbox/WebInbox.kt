package com.kiroku.app.inbox

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kiroku.app.AppPrefs
import com.kiroku.app.KirokuApp
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.ContactEntity
import com.kiroku.app.data.ContactRepository
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.SaveResult
import com.kiroku.app.data.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class InboxResult(val added: Int, val updated: Int, val alreadySaved: Int, val skipped: Int)

/** A contact typed on the web page; its id ("w-…") is kept on the phone so later edits find it. */
data class WebContact(val id: String, val name: String, val email: String, val phone: String, val address: String) {
    fun toEntity(linkId: String, createdAt: Long) =
        ContactEntity(id = id, linkId = linkId, name = name, email = email, phone = phone, address = address, createdAt = createdAt)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("email", email).put("phone", phone).put("address", address)
}

/** What the app shows for a link besides the fields that can be edited on the web. */
data class WebExtras(
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val archived: Boolean,
    val pinned: Boolean,
    val savedMs: Long?,
) {
    fun addTo(json: JSONObject): JSONObject = json
        .put("description", description ?: JSONObject.NULL)
        .put("image_url", imageUrl ?: JSONObject.NULL)
        .put("site_name", siteName ?: JSONObject.NULL)
        .put("archived", archived)
        .put("pinned", pinned)
        .put("saved_ms", savedMs ?: JSONObject.NULL)

    companion object {
        private fun String?.limit(max: Int): String? = this?.trim()?.take(max)?.trim()?.takeIf { it.isNotEmpty() }

        fun fromPhone(link: LinkEntity) = WebExtras(
            description = link.description.limit(2000),
            imageUrl = link.previewImageUrl.limit(2000),
            siteName = link.siteName.limit(200),
            archived = link.status == Status.ARCHIVED,
            pinned = link.pinned,
            savedMs = link.createdAt,
        )

        fun from(json: JSONObject): WebExtras {
            fun text(key: String): String? = if (json.isNull(key)) null else json.optString(key).trim().takeIf { it.isNotEmpty() }
            return WebExtras(
                description = text("description"),
                imageUrl = text("image_url"),
                siteName = text("site_name"),
                archived = json.optBoolean("archived", false),
                pinned = json.optBoolean("pinned", false),
                savedMs = if (json.isNull("saved_ms")) null else json.optLong("saved_ms"),
            )
        }
    }
}

/** One link as the web page has it. */
data class WebItem(
    val url: String,
    val title: String?,
    val folder: String?,
    val tags: List<String>,
    val note: String?,
    val contacts: List<WebContact>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title ?: JSONObject.NULL)
        .put("folder", folder ?: JSONObject.NULL)
        .put("tags", JSONArray(tags))
        .put("note", note ?: JSONObject.NULL)
        .put("contacts", JSONArray(contacts.map { it.toJson() }))

    companion object {
        val EMPTY = WebItem("", null, null, emptyList(), null, emptyList())

        private fun String?.limit(max: Int): String? = this?.trim()?.take(max)?.trim()?.takeIf { it.isNotEmpty() }

        /** The phone's link, tidied the same way the inbox script tidies what it stores. */
        fun fromPhone(link: LinkEntity, folderName: String?, contacts: List<ContactEntity>) = WebItem(
            url = link.url.trim().take(4000),
            title = link.title.limit(500),
            folder = folderName.limit(80),
            tags = link.tags.mapNotNull { it.limit(60) }.take(20),
            note = link.notes.limit(4000),
            contacts = contacts.filterNot { it.isBlank() }.take(20).map {
                WebContact(
                    it.id.take(80),
                    it.name.limit(200).orEmpty(),
                    it.email.limit(200).orEmpty(),
                    it.phone.limit(60).orEmpty(),
                    it.address.limit(1000).orEmpty(),
                )
            },
        )

        fun from(json: JSONObject): WebItem {
            fun JSONObject.text(key: String): String? =
                if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
            val tagArray = json.optJSONArray("tags")
            val contactArray = json.optJSONArray("contacts")
            return WebItem(
                url = json.optString("url").trim(),
                title = json.text("title"),
                folder = json.text("folder"),
                tags = if (tagArray == null) emptyList() else List(tagArray.length()) { tagArray.optString(it).trim() }.filter { it.isNotEmpty() },
                note = json.text("note"),
                contacts = if (contactArray == null) emptyList() else (0 until contactArray.length()).mapNotNull { i ->
                    val c = contactArray.optJSONObject(i) ?: return@mapNotNull null
                    val id = c.text("id") ?: return@mapNotNull null
                    WebContact(id, c.text("name").orEmpty(), c.text("email").orEmpty(), c.text("phone").orEmpty(), c.text("address").orEmpty())
                },
            )
        }
    }
}

/**
 * Web inbox: links saved from a browser (web/index.html) wait in Supabase until this
 * picks them up and saves them like any other link. The phone also keeps a copy of its whole
 * library there (unless turned off), so the web page can show and edit every link; web edits
 * are copied to the phone and phone changes are sent back on each check.
 */
class WebInbox(
    private val prefs: AppPrefs,
    private val http: OkHttpClient,
    private val links: LinkRepository,
    private val categories: CategoryRepository,
    private val contacts: ContactRepository,
) {
    private val lock = Mutex()

    val isConfigured: Boolean
        get() = prefs.inboxUrl.isNotBlank() && prefs.inboxAnonKey.isNotBlank()

    /** Saves the Supabase project details (does not check them; see [checkNow]). */
    fun configure(projectUrl: String, anonKey: String) {
        prefs.inboxUrl = normalizeProjectUrl(projectUrl)
        prefs.inboxAnonKey = anonKey.trim()
        if (prefs.inboxKey.isEmpty()) prefs.inboxKey = newKey()
        prefs.inboxLastError = ""
    }

    fun turnOff() {
        prefs.inboxUrl = ""
        prefs.inboxAnonKey = ""
        prefs.inboxLastError = ""
        prefs.inboxLastCheck = 0L
    }

    /** New secret: every connected browser stops working until it gets a new setup link. */
    fun resetKey() {
        prefs.inboxKey = newKey()
    }

    /** Link that connects a browser: the web page reads the details from the part after '#'. */
    fun setupLink(): String {
        if (prefs.inboxKey.isEmpty()) prefs.inboxKey = newKey()
        val json = JSONObject()
            .put("u", prefs.inboxUrl)
            .put("k", prefs.inboxAnonKey)
            .put("i", prefs.inboxKey)
            .toString()
        val encoded = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "$WEB_PAGE#setup=$encoded"
    }

    /**
     * Collects new links and edits made on the web page. Safe to call often; runs one at a time.
     * Each row comes with what the phone got last time ("delivered"), so an edit on the web
     * only changes the fields that were edited there, not changes made on the phone since.
     */
    suspend fun checkNow(): InboxResult = lock.withLock {
        check(isConfigured) { "Web inbox is not set up" }
        try {
            // Let the web page suggest the phone's folders.
            rpc("kiroku_inbox_set_folders", JSONObject().put("p_folders", JSONArray(categories.getAll().map { it.name })))

            val waiting = JSONArray(rpc("kiroku_inbox_list", JSONObject()))
            val done = JSONArray()
            var added = 0
            var updated = 0
            var already = 0
            var skipped = 0
            for (i in 0 until waiting.length()) {
                val row = waiting.getJSONObject(i)
                try {
                    val item = WebItem.from(row)
                    val previous = row.optJSONObject("delivered")?.let { WebItem.from(it) }
                    val existing = row.str("link_id")?.let { links.getById(it) }
                    val linkId: String? = if (existing != null) {
                        applyEdits(existing.id, item, previous ?: WebItem.EMPTY)
                        updated++
                        existing.id
                    } else {
                        when (val result = links.save(item.url, item.note.orEmpty(), item.title, folderId(item.folder), item.tags)) {
                            is SaveResult.Saved -> {
                                saveContacts(result.item.id, item.contacts)
                                added++
                                result.item.id
                            }
                            is SaveResult.AlreadySaved -> {
                                mergeInto(result.item, item)
                                already++
                                result.item.id
                            }
                            SaveResult.InvalidUrl -> {
                                skipped++
                                null
                            }
                        }
                    }
                    done.put(
                        JSONObject()
                            .put("id", row.getString("id"))
                            .put("version", row.optInt("version", 1))
                            .put("link_id", linkId ?: JSONObject.NULL)
                            .put("snapshot", item.toJson())
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Leave it in the inbox and try again next time.
                }
            }
            if (done.length() > 0) rpc("kiroku_inbox_done", JSONObject().put("p_items", done))
            sendBack()

            prefs.inboxLastCheck = System.currentTimeMillis()
            prefs.inboxLastAdded = added
            prefs.inboxLastError = ""
            InboxResult(added, updated, already, skipped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.inboxLastCheck = System.currentTimeMillis()
            prefs.inboxLastError = e.message ?: e.javaClass.simpleName
            throw e
        }
    }

    /**
     * Keeps the web page showing what the phone has: fetched titles, edits made on the phone,
     * and (unless turned off) every other link too. Links deleted on the phone leave the web.
     */
    private suspend fun sendBack() {
        val collected = JSONArray(rpc("kiroku_inbox_collected", JSONObject()))
        val folderNames = categories.getAll().associate { it.id to it.name }
        val contactsByLink = contacts.getAll().groupBy { it.linkId }
        fun current(link: LinkEntity) =
            WebItem.fromPhone(link, link.category?.let { folderNames[it] }, contactsByLink[link.id].orEmpty())

        val onWeb = mutableSetOf<String>()
        val refresh = JSONArray()
        val forget = JSONArray()
        for (i in 0 until collected.length()) {
            val row = collected.getJSONObject(i)
            val linkId = row.str("link_id") ?: continue
            onWeb += linkId
            if (row.optBoolean("pending")) continue // a web edit is on its way to the phone first
            val id = row.getString("id")
            val version = row.optInt("version", 1)
            val link = links.getById(linkId)
            if (link == null) {
                forget.put(JSONObject().put("id", id).put("version", version))
                continue
            }
            val phone = current(link)
            val extras = WebExtras.fromPhone(link)
            val web = row.optJSONObject("delivered")?.let { WebItem.from(it) }
            val webExtras = row.optJSONObject("extras")?.let { WebExtras.from(it) }
            if (phone != web || extras != webExtras) {
                refresh.put(extras.addTo(phone.toJson()).put("id", id).put("version", version))
            }
        }
        val publish = JSONArray()
        if (prefs.inboxMirror) {
            for (link in links.allItems()) {
                if (link.id in onWeb) continue
                publish.put(WebExtras.fromPhone(link).addTo(current(link).toJson()).put("link_id", link.id))
            }
        }
        refresh.chunks().forEach { rpc("kiroku_inbox_refresh", JSONObject().put("p_items", it)) }
        forget.chunks().forEach { rpc("kiroku_inbox_forget", JSONObject().put("p_items", it)) }
        publish.chunks().forEach { rpc("kiroku_inbox_publish", JSONObject().put("p_items", it)) }
    }

    /** Turns the web copy of the whole library on or off. Off removes it from Supabase. */
    suspend fun setMirror(on: Boolean) {
        prefs.inboxMirror = on
        if (!isConfigured) return
        if (on) checkNow() else lock.withLock { rpc("kiroku_inbox_unpublish", JSONObject()) }
    }

    private fun JSONArray.chunks(size: Int = 200): List<JSONArray> =
        (0 until length()).chunked(size).map { idx -> JSONArray().also { out -> idx.forEach { out.put(get(it)) } } }

    private suspend fun folderId(name: String?): String? = name?.let { categories.create(it, "folder").id }

    /** A link already on the phone was edited on the web: copy over what changed there. */
    private suspend fun applyEdits(linkId: String, now: WebItem, before: WebItem) {
        if (now.url != before.url) links.updateUrl(linkId, now.url)
        if (now.title != before.title) links.updateTitle(linkId, now.title.orEmpty())
        if (now.folder != before.folder) links.setCategory(linkId, folderId(now.folder))
        if (now.tags != before.tags) links.setTags(linkId, now.tags)
        if (now.note != before.note) links.updateNotes(linkId, now.note.orEmpty())

        val old = before.contacts.associateBy { it.id }
        val onPhone = contacts.getFor(linkId).associateBy { it.id }
        now.contacts.forEachIndexed { index, c ->
            if (old[c.id] != c || c.id !in onPhone) {
                contacts.save(linkId, c.toEntity(linkId, onPhone[c.id]?.createdAt ?: (System.currentTimeMillis() + index)))
            }
        }
        val kept = now.contacts.map { it.id }.toSet()
        old.keys.filter { it !in kept && it in onPhone }.forEach { contacts.delete(it) }
    }

    /** The link was already saved on the phone: add what's missing without overwriting anything. */
    private suspend fun mergeInto(link: LinkEntity, item: WebItem) {
        // A title typed on the web beats a fetched one, but not one typed on the phone.
        if (item.title != null && (link.title.isNullOrBlank() || !link.titleCustom)) links.updateTitle(link.id, item.title)
        if (link.category == null && item.folder != null) links.setCategory(link.id, folderId(item.folder))
        val tags = (link.tags + item.tags).distinctBy { it.lowercase() }
        if (tags.size != link.tags.size) links.setTags(link.id, tags)
        val note = item.note
        if (note != null && !link.notes.contains(note)) {
            links.updateNotes(link.id, if (link.notes.isBlank()) note else link.notes.trimEnd() + "\n\n" + note)
        }
        val have = contacts.getFor(link.id).map { it.id }.toSet()
        saveContacts(link.id, item.contacts.filter { it.id !in have })
    }

    private suspend fun saveContacts(linkId: String, list: List<WebContact>) {
        val start = System.currentTimeMillis()
        list.forEachIndexed { index, c -> contacts.save(linkId, c.toEntity(linkId, start + index)) }
    }

    private suspend fun rpc(function: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        body.put("p_key", prefs.inboxKey)
        val request = Request.Builder()
            .url("${prefs.inboxUrl}/rest/v1/rpc/$function")
            .header("apikey", prefs.inboxAnonKey)
            .header("Authorization", "Bearer ${prefs.inboxAnonKey}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
                throw IOException(
                    when {
                        response.code == 401 -> "Supabase rejected the key. Check the anon key."
                        response.code == 404 -> "The Kiroku setup script hasn't been run in this Supabase project yet."
                        message != null -> message
                        else -> "Supabase error ${response.code}"
                    }
                )
            }
            text
        }
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    companion object {
        /** Where web/index.html is published (GitHub Pages of the Kiroku repository). */
        const val WEB_PAGE = "https://udayakasapu1.github.io/kiroku/"
        const val SETUP_SCRIPT = "https://github.com/udayakasapu1/kiroku/blob/main/supabase/kiroku_inbox.sql"

        private const val PERIODIC = "web-inbox"
        private const val NOW = "web-inbox-now"

        fun newKey(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        /** "abcd.supabase.co" or "https://abcd.supabase.co/" -> "https://abcd.supabase.co" */
        fun normalizeProjectUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return withScheme
            return "${url.scheme}://${url.host}" + if (url.port != 443 && url.port != 80) ":${url.port}" else ""
        }

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every ~15 minutes while set up (Android's minimum for background checks). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One check as soon as there's a connection (e.g. when the app opens). */
        fun checkSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<InboxWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}

class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val inbox = (applicationContext as KirokuApp).container.inbox
        if (!inbox.isConfigured) return Result.success()
        return try {
            inbox.checkNow()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success() // error is recorded for the settings screen; the next run tries again
        }
    }
}

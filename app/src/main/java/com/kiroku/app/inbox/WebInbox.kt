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
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.SaveResult
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

data class InboxResult(val added: Int, val alreadySaved: Int, val skipped: Int)

/**
 * Web inbox: links saved from a browser (web/index.html) wait in Supabase until this
 * picks them up, saves them like any other link, and removes them from the inbox.
 */
class WebInbox(
    private val prefs: AppPrefs,
    private val http: OkHttpClient,
    private val links: LinkRepository,
    private val categories: CategoryRepository,
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

    /** Collects waiting links. Safe to call often; runs one at a time. */
    suspend fun checkNow(): InboxResult = lock.withLock {
        check(isConfigured) { "Web inbox is not set up" }
        try {
            // Let the web page suggest the phone's folders.
            rpc("kiroku_inbox_set_folders", JSONObject().put("p_folders", JSONArray(categories.getAll().map { it.name })))

            val waiting = JSONArray(rpc("kiroku_inbox_list", JSONObject()))
            val done = mutableListOf<String>()
            var added = 0
            var already = 0
            var skipped = 0
            for (i in 0 until waiting.length()) {
                val item = waiting.getJSONObject(i)
                try {
                    val folderId = item.str("folder")?.let { categories.create(it, "folder").id }
                    val tagArray = item.optJSONArray("tags")
                    val tags = if (tagArray == null) emptyList() else List(tagArray.length()) { tagArray.optString(it) }
                    when (links.save(item.getString("url"), item.str("note").orEmpty(), item.str("title"), folderId, tags)) {
                        is SaveResult.Saved -> added++
                        is SaveResult.AlreadySaved -> already++
                        SaveResult.InvalidUrl -> skipped++
                    }
                    done += item.getString("id")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Leave it in the inbox and try again next time.
                }
            }
            if (done.isNotEmpty()) rpc("kiroku_inbox_ack", JSONObject().put("p_ids", JSONArray(done)))

            prefs.inboxLastCheck = System.currentTimeMillis()
            prefs.inboxLastAdded = added
            prefs.inboxLastError = ""
            InboxResult(added, already, skipped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            prefs.inboxLastCheck = System.currentTimeMillis()
            prefs.inboxLastError = e.message ?: e.javaClass.simpleName
            throw e
        }
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

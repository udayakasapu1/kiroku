package com.kiroku.app

import android.app.Application
import android.content.Context
import com.kiroku.app.backup.BackupManager
import com.kiroku.app.data.AttachmentRepository
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.KirokuDatabase
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.data.NoteRepository
import com.kiroku.app.fetch.MetadataFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class KirokuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Clean up duplicate folders left by older versions (e.g. two "Tier 1").
        container.appScope.launch { runCatching { container.categories.mergeDuplicates() } }
    }
}

/** Simple manual dependency container (no DI framework needed at this size). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For writes that must finish even if the screen that started them closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = KirokuDatabase.create(appContext)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val links = LinkRepository(appContext, database.linkDao(), MetadataFetcher(httpClient))
    val notes = NoteRepository(database.noteDao())
    val categories = CategoryRepository(database, database.categoryDao(), database.linkDao())
    val backup = BackupManager(appContext, links, notes, categories)
    val prefs = AppPrefs(appContext)
    val attachments = AttachmentRepository(appContext, database.attachmentDao())
    val shares = ShareInbox()
}

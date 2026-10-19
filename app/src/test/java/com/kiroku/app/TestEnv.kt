package com.kiroku.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.ContactRepository
import com.kiroku.app.data.KirokuDatabase
import com.kiroku.app.data.LinkRepository
import com.kiroku.app.fetch.MetadataFetcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** The app's real repositories on an in-memory database, plus a little web server for pages. */
class TestEnv {
    val context: Context = ApplicationProvider.getApplicationContext()

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    val db: KirokuDatabase = Room.inMemoryDatabaseBuilder(context, KirokuDatabase::class.java).build()
    val links = LinkRepository(context, db.linkDao(), MetadataFetcher(OkHttpClient()))
    val categories = CategoryRepository(db, db.categoryDao(), db.linkDao())
    val contacts = ContactRepository(db.contactDao())
    val prefs = AppPrefs(context)

    /** path -> page title; a title of null serves a page without any title. */
    val pageTitles = mutableMapOf<String, String?>()
    val pages = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath ?: "/"
                if (path !in pageTitles) return MockResponse().setResponseCode(404)
                val title = pageTitles[path]
                val head = if (title == null) "" else "<title>$title</title>"
                return MockResponse()
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<!doctype html><html><head>$head<meta name=description content='About $path'></head><body><p>Hi</p></body></html>")
            }
        }
        start()
    }

    /** A page URL on the little web server, with the given title. */
    fun page(path: String, title: String?): String {
        pageTitles[path] = title
        return pages.url(path).toString()
    }

    fun close() {
        pages.shutdown()
        db.close()
    }
}

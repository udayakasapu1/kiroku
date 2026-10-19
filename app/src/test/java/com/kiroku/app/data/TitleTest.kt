package com.kiroku.app.data

import android.app.Application
import com.kiroku.app.TestEnv
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Titles in the app itself: typed titles win over fetched ones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TitleTest {
    private lateinit var env: TestEnv

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() = env.close()

    private suspend fun save(url: String, title: String? = null): LinkEntity =
        (env.links.save(url, titleHint = title) as SaveResult.Saved).item

    @Test fun fetchFillsTitleWhenNoneTyped() = runBlocking {
        val link = save(env.page("/a", "Brake caliper datasheet"))
        assertNull(link.title)
        env.links.fetchAndApply(link.id)
        val after = env.links.getById(link.id)!!
        assertEquals("Brake caliper datasheet", after.title)
        assertFalse(after.titleCustom)
    }

    @Test fun titleTypedWhenSavingIsKeptAfterFetch() = runBlocking {
        val link = save(env.page("/b", "Some long SEO title | Shop"), title = "Supplier price list")
        assertTrue(link.titleCustom)
        env.links.fetchAndApply(link.id)
        assertEquals("Supplier price list", env.links.getById(link.id)!!.title)
    }

    @Test fun titleTypedLaterIsKeptAfterRetry() = runBlocking {
        val link = save(env.page("/c", "Fetched title"))
        env.links.fetchAndApply(link.id)
        env.links.updateTitle(link.id, "  My own title  ")
        env.links.fetchAndApply(link.id) // "Retry fetching details"
        val after = env.links.getById(link.id)!!
        assertEquals("My own title", after.title)
        assertTrue(after.titleCustom)
    }

    @Test fun clearingTitleBringsBackThePageTitle() = runBlocking {
        val link = save(env.page("/d", "Page's own title"), title = "Typed")
        env.links.updateTitle(link.id, "")
        val cleared = env.links.getById(link.id)!!
        assertNull(cleared.title)
        assertFalse(cleared.titleCustom)
        assertEquals(FetchState.PENDING, cleared.fetchState) // a new fetch is queued
        env.links.fetchAndApply(link.id)
        assertEquals("Page's own title", env.links.getById(link.id)!!.title)
    }

    @Test fun placeholderTitlesAreIgnored() = runBlocking {
        for ((path, junk) in listOf("/e1" to "Just a moment...", "/e2" to "Please enable JavaScript to continue", "/e3" to "Attention Required! | Cloudflare")) {
            val link = save(env.page(path, junk))
            env.links.fetchAndApply(link.id)
            assertNull("$junk should not become the title", env.links.getById(link.id)!!.title)
        }
    }

    @Test fun pageWithoutTitleLeavesItEmptyToType() = runBlocking {
        val link = save(env.page("/f", null))
        env.links.fetchAndApply(link.id)
        val after = env.links.getById(link.id)!!
        assertNull(after.title)
        assertEquals(FetchState.DONE, after.fetchState)
        env.links.updateTitle(link.id, "Typed afterwards")
        assertEquals("Typed afterwards", env.links.getById(link.id)!!.title)
    }
}

class PlaceholderTitleTest {
    private val fetcher = com.kiroku.app.fetch.MetadataFetcher(okhttp3.OkHttpClient())

    @Test fun recognisesPlaceholders() {
        listOf("Just a moment...", "Loading…", "Error", "Please enable JavaScript to continue", "Attention Required! | Cloudflare",
            "Access Denied", "You need to enable JavaScript to run this app.").forEach {
            assertTrue(it, fetcher.isPlaceholderTitle(it))
        }
    }

    @Test fun keepsRealTitles() {
        listOf("Error codes for BMS", "Document control at Bosch", "Loading dock safety", "Home charging wallbox guide",
            "JavaScript basics", "Brake caliper datasheet").forEach {
            assertFalse(it, fetcher.isPlaceholderTitle(it))
        }
    }
}

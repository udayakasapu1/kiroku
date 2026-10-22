package com.kiroku.app.inbox

import android.app.Application
import com.kiroku.app.TestEnv
import com.kiroku.app.data.ContactRepository
import com.kiroku.app.data.FetchState
import com.kiroku.app.data.LinkEntity
import com.kiroku.app.data.SaveResult
import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Web page <-> phone, end to end: the phone side is the app's real code on a real (in-memory)
 * database; the server is the real supabase/kiroku_inbox.sql behind PostgREST, the same API
 * layer Supabase uses. The web side makes exactly the calls web/index.html makes.
 *
 * Needs INBOX_TEST_URL (PostgREST) and INBOX_TEST_JWT_SECRET; skipped without them.
 * GitHub Actions starts both before running the tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebInboxSyncTest {
    private lateinit var env: TestEnv
    private lateinit var inbox: WebInbox
    private lateinit var server: String
    private lateinit var anon: String
    private val key = "test-" + UUID.randomUUID() + UUID.randomUUID()
    private val http = OkHttpClient()

    @Before fun setUp() {
        val url = System.getenv("INBOX_TEST_URL")
        val secret = System.getenv("INBOX_TEST_JWT_SECRET")
        assumeTrue("INBOX_TEST_URL not set", !url.isNullOrBlank() && !secret.isNullOrBlank())
        server = url!!.trimEnd('/')
        anon = jwt(secret!!)
        env = TestEnv()
        // Supabase serves PostgREST under /rest/v1; a bare PostgREST serves it at /.
        val phoneHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath.removePrefix("/rest/v1")
            chain.proceed(req.newBuilder().url(req.url.newBuilder().encodedPath(path).build()).build())
        }.build()
        inbox = WebInbox(env.prefs, phoneHttp, env.links, env.categories, env.contacts)
        env.prefs.inboxKey = key
        inbox.configure(server, anon)
    }

    @After fun tearDown() { if (::env.isInitialized) env.close() }

    // ---- The web page's calls ----

    private fun rpc(fn: String, body: JSONObject): String {
        body.put("p_key", key)
        val req = Request.Builder().url("$server/rpc/$fn")
            .header("apikey", anon).header("Authorization", "Bearer $anon")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val text = r.body!!.string()
            check(r.isSuccessful) { "$fn failed (${r.code}): $text" }
            return text
        }
    }

    private fun webAdd(url: String, title: String? = null, note: String? = null, folder: String? = null,
                       tags: List<String> = emptyList(), contacts: JSONArray = JSONArray()): String =
        JSONObject("{\"v\":" + rpc("kiroku_inbox_add", JSONObject()
            .put("p_url", url).put("p_title", title ?: JSONObject.NULL).put("p_folder", folder ?: JSONObject.NULL)
            .put("p_tags", JSONArray(tags)).put("p_note", note ?: JSONObject.NULL).put("p_contacts", contacts)) + "}").getString("v")

    /** Like the page: only the changed fields. */
    private fun webEdit(id: String, changes: JSONObject) {
        rpc("kiroku_inbox_update", JSONObject().put("p_id", id).put("p_changes", changes))
    }

    private fun webRow(id: String): JSONObject? {
        val rows = JSONArray(rpc("kiroku_inbox_recent", JSONObject()))
        return (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
    }

    private fun JSONObject.text(k: String): String? = if (isNull(k)) null else getString(k)

    // ---- Phone helpers ----

    private fun check() = runBlocking { inbox.checkNow() }
    private fun phone(url: String): LinkEntity = runBlocking { env.links.findByUrl(UrlUtils.normalize(url)!!) }!!
    private fun fetch(link: LinkEntity) = runBlocking { env.links.fetchAndApply(link.id) }

    // ---- Titles ----

    @Test fun titleTypedOnWebArrivesAndSurvivesFetch() {
        val url = env.page("/t1", "Generic shop title")
        val id = webAdd(url, title = "Brake supplier – Tier 1 quote")
        check()
        val link = phone(url)
        assertEquals("Brake supplier – Tier 1 quote", link.title)
        assertTrue(link.titleCustom)
        fetch(link)
        assertEquals("Brake supplier – Tier 1 quote", phone(url).title)
        check()
        assertEquals("Brake supplier – Tier 1 quote", webRow(id)!!.text("title"))
        assertFalse(webRow(id)!!.getBoolean("pending"))
    }

    @Test fun noTitleOnWebGetsPageTitleOnPhoneAndWeb() {
        val url = env.page("/t2", "Inverter datasheet")
        val id = webAdd(url)
        check()
        assertNull(phone(url).title)
        fetch(phone(url))
        assertEquals("Inverter datasheet", phone(url).title)
        assertFalse(phone(url).titleCustom)
        check()
        assertEquals("Inverter datasheet", webRow(id)!!.text("title")) // the web list shows it too
    }

    @Test fun pageWithNoUsableTitleCanBeTitledOnTheWebLater() {
        val url = env.page("/t3", "Just a moment...")
        val id = webAdd(url)
        check()
        fetch(phone(url))
        assertNull(phone(url).title)
        webEdit(id, JSONObject().put("title", "Motor test report"))
        check()
        assertEquals("Motor test report", phone(url).title)
        assertTrue(phone(url).titleCustom)
        fetch(phone(url)) // a later retry doesn't undo it
        assertEquals("Motor test report", phone(url).title)
    }

    @Test fun titleFixedRightAfterSavingBeforePhoneChecks() {
        val url = env.page("/t4", "Page")
        val id = webAdd(url, title = "Typo tittle")
        webEdit(id, JSONObject().put("title", "Fixed title").put("note", "forgot this"))
        check()
        assertEquals("Fixed title", phone(url).title)
        assertEquals("forgot this", phone(url).notes)
        assertEquals(1, runBlocking { env.links.allItems() }.size)
    }

    @Test fun webTitleEditKeepsPhoneNoteEdit() {
        val url = env.page("/t5", "Page")
        val id = webAdd(url, title = "First", note = "from web")
        check()
        runBlocking { env.links.updateNotes(phone(url).id, "changed on phone") }
        check()
        assertEquals("changed on phone", webRow(id)!!.text("note"))
        webEdit(id, JSONObject().put("title", "Second"))
        check()
        assertEquals("Second", phone(url).title)
        assertEquals("changed on phone", phone(url).notes)
    }

    @Test fun phoneTitleEditShowsOnWebAndSurvivesWebNoteEdit() {
        val url = env.page("/t6", "Page title")
        val id = webAdd(url)
        check()
        runBlocking { env.links.updateTitle(phone(url).id, "Named on the phone") }
        check()
        assertEquals("Named on the phone", webRow(id)!!.text("title"))
        webEdit(id, JSONObject().put("note", "added on the web"))
        check()
        assertEquals("Named on the phone", phone(url).title)
        assertEquals("added on the web", phone(url).notes)
        assertTrue(phone(url).titleCustom)
    }

    @Test fun clearingTitleOnWebBringsBackPageTitle() {
        val url = env.page("/t7", "Real page title")
        val id = webAdd(url, title = "Temporary")
        check()
        webEdit(id, JSONObject().put("title", JSONObject.NULL))
        check()
        val cleared = phone(url)
        assertNull(cleared.title)
        assertFalse(cleared.titleCustom)
        assertEquals(FetchState.PENDING, cleared.fetchState)
        fetch(cleared)
        assertEquals("Real page title", phone(url).title)
        check()
        assertEquals("Real page title", webRow(id)!!.text("title"))
    }

    @Test fun webTitleForLinkAlreadyOnPhone() {
        // Fetched title on the phone: the title typed on the web replaces it.
        val a = env.page("/t8a", "Fetched A")
        val la = (runBlocking { env.links.save(a) } as SaveResult.Saved).item
        fetch(la)
        webAdd(a, title = "Typed on web A")
        check()
        assertEquals("Typed on web A", phone(a).title)
        // Title typed on the phone: kept.
        val b = env.page("/t8b", "Fetched B")
        runBlocking { env.links.save(b, titleHint = "Typed on phone B") }
        webAdd(b, title = "Typed on web B", tags = listOf("web"))
        check()
        assertEquals("Typed on phone B", phone(b).title)
        assertEquals(listOf("web"), phone(b).tags)
    }

    // ---- Everything else that rides along ----

    @Test fun fieldsFolderTagsAndContactsGoBothWays() {
        val url = env.page("/s1", "Page")
        val contact = JSONObject().put("id", "w-anna").put("name", "Anna Berger").put("email", "anna@x.example")
        val id = webAdd(url, folder = "Tier 1", tags = listOf("brakes"), contacts = JSONArray().put(contact))
        check()
        val link = phone(url)
        assertEquals("Tier 1", runBlocking { env.categories.getAll() }.single { it.id == link.category }.name)
        assertEquals(listOf("brakes"), link.tags)
        assertEquals(listOf("Anna Berger"), runBlocking { env.contacts.getFor(link.id) }.map { it.name })

        // A contact added on the phone shows on the web.
        runBlocking { env.contacts.save(link.id, ContactRepository.draft().copy(name = "Ben Phone")) }
        check()
        val webContacts = webRow(id)!!.getJSONArray("contacts")
        assertEquals(2, webContacts.length())

        // Renaming Anna on the web keeps Ben.
        val edited = JSONArray()
        for (i in 0 until webContacts.length()) {
            val c = webContacts.getJSONObject(i)
            if (c.getString("id") == "w-anna") c.put("name", "Anna Berger-Huber").put("phone", "+43 1 234")
            edited.put(c)
        }
        webEdit(id, JSONObject().put("contacts", edited))
        check()
        val names = runBlocking { env.contacts.getFor(link.id) }.associate { it.name to it.phone }
        assertEquals(mapOf("Anna Berger-Huber" to "+43 1 234", "Ben Phone" to ""), names)

        // Moving folders on the web.
        webEdit(id, JSONObject().put("folder", "OEM"))
        check()
        assertEquals("OEM", runBlocking { env.categories.getAll() }.single { it.id == phone(url).category }.name)
    }

    @Test fun deletedOnPhoneLeavesTheWebList() {
        val url = env.page("/s2", "Page")
        val id = webAdd(url)
        check()
        assertNotNull(webRow(id))
        runBlocking { env.links.delete(phone(url).id) }
        check()
        assertNull(webRow(id))
    }

    @Test fun repeatedChecksChangeNothing() {
        val url = env.page("/s3", "Page")
        val id = webAdd(url, title = "Stable", note = "n")
        check(); check()
        val before = phone(url)
        val result = check()
        assertEquals(0, result.added + result.updated)
        assertEquals(before, phone(url))
        assertFalse(webRow(id)!!.getBoolean("pending"))
    }

    // ---- The whole library on the web ----

    private fun webRows(): List<JSONObject> {
        val rows = JSONArray(rpc("kiroku_inbox_recent", JSONObject()))
        return (0 until rows.length()).map { rows.getJSONObject(it) }
    }

    private fun webByUrl(url: String): List<JSONObject> {
        val key = UrlUtils.normalize(url)
        return webRows().filter { UrlUtils.normalize(it.getString("url")) == key }
    }

    private fun phoneSave(url: String, title: String? = null): LinkEntity =
        (runBlocking { env.links.save(url, titleHint = title) } as SaveResult.Saved).item

    @Test fun phoneLibraryShowsOnTheWeb() {
        val a = env.page("/m1", "Gearbox supplier")
        val b = env.page("/m2", "Old catalogue")
        val la = phoneSave(a)
        val lb = phoneSave(b, title = "Catalogue 2019")
        runBlocking {
            env.links.fetchAndApply(la.id)
            val oem = env.categories.create("OEM", "folder")
            env.links.setCategory(la.id, oem.id)
            env.links.setTags(la.id, listOf("gearbox", "tier1"))
            env.links.setPinned(la.id, true)
            env.contacts.save(la.id, ContactRepository.draft().copy(name = "Carla", email = "carla@gear.example"))
            env.links.archive(lb.id)
        }
        check()
        val ra = webByUrl(a).single()
        assertEquals("Gearbox supplier", ra.text("title"))
        assertEquals("OEM", ra.text("folder"))
        assertEquals(listOf("gearbox", "tier1"), ra.getJSONArray("tags").let { t -> List(t.length()) { t.getString(it) } })
        assertEquals("About /m1", ra.text("description"))
        assertTrue(ra.getBoolean("pinned"))
        assertFalse(ra.getBoolean("archived"))
        assertTrue(ra.getBoolean("on_phone"))
        assertFalse(ra.getBoolean("pending"))
        assertEquals("phone", ra.getString("origin"))
        assertEquals("Carla", ra.getJSONArray("contacts").getJSONObject(0).getString("name"))
        assertEquals(la.createdAt, ra.getLong("saved_ms"))
        val rb = webByUrl(b).single()
        assertEquals("Catalogue 2019", rb.text("title"))
        assertTrue(rb.getBoolean("archived"))
    }

    @Test fun editingAPhoneLinkOnTheWebReachesThePhone() {
        val url = env.page("/m3", "Fetched")
        val link = phoneSave(url)
        check()
        val id = webByUrl(url).single().getString("id")
        webEdit(id, JSONObject().put("title", "Named on web").put("note", "web note").put("folder", "Tools"))
        assertTrue(webRow(id)!!.getBoolean("pending"))
        check()
        val after = runBlocking { env.links.getById(link.id) }!!
        assertEquals("Named on web", after.title)
        assertEquals("web note", after.notes)
        assertEquals("Tools", runBlocking { env.categories.getAll() }.single { it.id == after.category }.name)
        assertFalse(webRow(id)!!.getBoolean("pending"))
        assertEquals(1, webByUrl(url).size)
    }

    @Test fun phoneChangesFollowOnTheWeb() {
        val url = env.page("/m4", "Page")
        val link = phoneSave(url)
        check()
        runBlocking {
            env.links.updateTitle(link.id, "Renamed on phone")
            env.links.archive(link.id)
        }
        check()
        val row = webByUrl(url).single()
        assertEquals("Renamed on phone", row.text("title"))
        assertTrue(row.getBoolean("archived"))
        runBlocking { env.links.delete(link.id) }
        check()
        assertTrue(webByUrl(url).isEmpty())
    }

    @Test fun turningTheWebCopyOffAndOn() {
        val phoneUrl = env.page("/m5", "Phone page")
        phoneSave(phoneUrl)
        val webUrl = env.page("/m6", "Web page")
        webAdd(webUrl, title = "Saved on web")
        check()
        assertEquals(1, webByUrl(phoneUrl).size)
        runBlocking { inbox.setMirror(false) }
        assertTrue(webByUrl(phoneUrl).isEmpty())   // the library copy is gone
        assertEquals(1, webByUrl(webUrl).size)      // what was saved on the web stays
        check()
        assertTrue(webByUrl(phoneUrl).isEmpty())   // and isn't sent again
        runBlocking { inbox.setMirror(true) }
        assertEquals(1, webByUrl(phoneUrl).size)
    }

    @Test fun savingTheSameLinkTwiceOnTheWebGivesOneLink() {
        val url = env.page("/m7", "Page")
        webAdd(url, title = "First", tags = listOf("a"))
        webAdd("$url?utm_source=x", tags = listOf("b"))
        check()
        assertEquals(1, runBlocking { env.links.allItems() }.size)
        assertEquals(1, webByUrl(url).size)
        assertEquals(listOf("a", "b"), phone(url).tags)
        check()
        assertEquals(1, webByUrl(url).size)
    }

    @Test fun savingOnTheWebALinkThePhoneAlreadySent() {
        val url = env.page("/m8", "Fetched title")
        val link = phoneSave(url)
        runBlocking { env.links.fetchAndApply(link.id) }
        check()
        webAdd(url, title = "Typed on web", note = "context")
        check()
        assertEquals(1, webByUrl(url).size)
        assertEquals("Typed on web", phone(url).title)
        assertEquals("context", phone(url).notes)
        assertEquals("Typed on web", webByUrl(url).single().text("title"))
    }

    private fun jwt(secret: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val head = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = enc.encodeToString("""{"role":"anon","iss":"supabase"}""".toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return "$head.$body." + enc.encodeToString(mac.doFinal("$head.$body".toByteArray()))
    }
}

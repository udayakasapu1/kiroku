package com.kiroku.app.fetch

import com.kiroku.app.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

data class PageMetadata(
    val finalUrl: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    val sourceDomain: String?,
) {
    /** How many of the useful preview fields we found. */
    fun score(): Int = listOf(title, description, imageUrl).count { !it.isNullOrBlank() }
}

class FetchHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Downloads a page's HTML and reads its preview metadata (Open Graph / Twitter / standard meta tags).
 * Unlike a headless browser it does not run JavaScript, so purely client-rendered pages may
 * return little; most sites put these tags in the server HTML for link previews.
 */
class MetadataFetcher(private val client: OkHttpClient) {

    suspend fun fetch(url: String): PageMetadata = withContext(Dispatchers.IO) {
        // First try as a normal mobile browser; if the page gives us little, retry as a
        // link-preview bot (some sites only serve preview tags to those).
        val first = attempt { fetchWith(url, BROWSER_UA) }
        val firstValue = first.getOrNull()
        if (firstValue != null && firstValue.score() >= 3) return@withContext firstValue

        val second = attempt { fetchWith(url, PREVIEW_BOT_UA) }.getOrNull()
        val best = listOfNotNull(firstValue, second).maxByOrNull { it.score() }
        best ?: throw (first.exceptionOrNull() ?: IOException("Could not fetch page"))
    }

    private inline fun attempt(block: () -> PageMetadata): Result<PageMetadata> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun fetchWith(url: String, userAgent: String): PageMetadata {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw FetchHttpException(response.code)
            val finalUrl = response.request.url.toString()
            val domain = UrlUtils.domainOf(finalUrl)
            val body = response.body ?: throw IOException("Empty response")
            val mediaType = body.contentType()
            val isHtml = mediaType == null ||
                mediaType.subtype.contains("html", ignoreCase = true) ||
                mediaType.subtype.contains("xml", ignoreCase = true)

            if (!isHtml) {
                // PDFs, images, etc.: use the file name as title.
                val fileName = response.request.url.pathSegments.lastOrNull { it.isNotBlank() }
                val image = if (mediaType?.type == "image") finalUrl else null
                return PageMetadata(finalUrl, fileName, null, image, null, domain)
            }

            val charset = mediaType?.charset()?.name()
            val document = LimitedInputStream(body.byteStream(), MAX_BYTES).use {
                Jsoup.parse(it, charset, finalUrl)
            }
            return parse(document, finalUrl, domain)
        }
    }

    private fun parse(doc: Document, finalUrl: String, domain: String?): PageMetadata {
        fun meta(vararg keys: String): String? {
            for (key in keys) {
                val element = doc.selectFirst("meta[property='$key'], meta[name='$key']") ?: continue
                val value = element.attr("content").trim()
                if (value.isNotEmpty()) return value
            }
            return null
        }

        val title = (
            meta("og:title", "twitter:title")
                ?: doc.title().trim().takeIf { it.isNotEmpty() }
                ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            )?.takeUnless { isPlaceholderTitle(it) }

        val description = meta("og:description", "twitter:description", "description")

        val rawImage = meta("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
            ?: doc.selectFirst("link[rel=image_src]")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("link[rel=apple-touch-icon]")?.attr("href")?.takeIf { it.isNotBlank() }

        val image = rawImage?.let { finalUrl.toHttpUrlOrNull()?.resolve(it)?.toString() }
        val siteName = meta("og:site_name", "application-name")

        return PageMetadata(
            finalUrl = finalUrl,
            title = title?.clean(300),
            description = description?.clean(1000),
            imageUrl = image,
            siteName = siteName?.clean(100),
            sourceDomain = domain,
        )
    }

    /** Titles of "please wait / enable JavaScript / blocked" pages, not of the page itself. */
    private fun isPlaceholderTitle(title: String): Boolean {
        val t = title.lowercase().trim()
        return t.length < 2 || placeholderTitles.any { t == it || t.startsWith(it) } ||
            t.contains("enable javascript") || t.contains("javascript is required") ||
            t.contains("javascript required") || t.contains("browser is not supported")
    }

    private val placeholderTitles = listOf(
        "just a moment", "attention required", "access denied", "loading", "please wait", "redirecting",
        "403 forbidden", "forbidden", "404 not found", "not found", "error", "untitled", "document",
        "security check", "are you a robot", "captcha", "verifying", "one moment", "checking your browser",
    )

    private fun String.clean(maxLength: Int): String? =
        replace(Regex("\\s+"), " ").trim().take(maxLength).takeIf { it.isNotEmpty() }

    /** Stops reading after [limit] bytes; preview tags are in the <head>, so that's plenty. */
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            if (count >= limit) return -1
            val b = super.read()
            if (b != -1) count++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (count >= limit) return -1
            val allowed = minOf(len.toLong(), limit - count).toInt()
            val n = super.read(b, off, allowed)
            if (n > 0) count += n
            return n
        }
    }

    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private const val PREVIEW_BOT_UA =
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    }
}

package com.kiroku.app.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** URL helpers with no Android APIs, so they're unit-testable on the JVM. */
object UrlUtils {

    private val httpUrlRegex = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val bareDomainRegex =
        Regex("""^(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,}(?::\d+)?(?:/\S*)?$""", RegexOption.IGNORE_CASE)
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "si", "ref_src", "_ga",
    )

    /**
     * Finds the first web link inside arbitrary shared text, e.g.
     * "Great read: Some Title https://example.com/post?id=1." -> "https://example.com/post?id=1"
     * Also accepts a bare domain when the whole text is just that ("example.com/page").
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        httpUrlRegex.find(text)?.let { return cleanTrailing(it.value) }
        val trimmed = text.trim()
        if (bareDomainRegex.matches(trimmed)) return "https://" + cleanTrailing(trimmed)
        return null
    }

    /**
     * Canonical form used for duplicate detection:
     * lowercase scheme/host, no default port, no trailing "/" for the root,
     * tracking parameters (utm_*, fbclid, …) and plain #fragments removed.
     * Returns null if this isn't a valid http(s) URL.
     */
    fun normalize(input: String): String? {
        val raw = input.trim()
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
        val withScheme = if (schemeRegex.containsMatchIn(raw)) raw else "https://$raw"
        if (!withScheme.startsWith("http://", ignoreCase = true) &&
            !withScheme.startsWith("https://", ignoreCase = true)
        ) return null
        // OkHttp's parser is lenient (percent-encodes stray characters, handles IDN hosts).
        val url = withScheme.toHttpUrlOrNull() ?: return null
        val scheme = url.scheme
        val host = url.host.takeIf { it.contains('.') || it.contains(':') || it == "localhost" } ?: return null
        val port = url.port.takeIf { it != HttpUrl.defaultPort(scheme) }
        val query = url.encodedQuery
            ?.split('&')
            ?.filter { param ->
                val key = param.substringBefore('=').lowercase()
                param.isNotEmpty() && !key.startsWith("utm_") && key !in trackingParams
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotEmpty() }
        // Keep "#/route" or "#!route" style fragments (single-page apps); drop plain anchors.
        val fragment = url.encodedFragment?.takeIf { it.startsWith("/") || it.startsWith("!") }
        val path = url.encodedPath.let { if (it == "/" && fragment == null) "" else it }

        return buildString {
            append(scheme).append("://")
            append(if (host.contains(':')) "[$host]" else host)
            if (port != null) append(':').append(port)
            append(path)
            if (query != null) append('?').append(query)
            if (fragment != null) append('#').append(fragment)
        }
    }

    /** "https://www.example.com/a" -> "example.com" */
    fun domainOf(url: String): String? = url.toHttpUrlOrNull()?.host?.removePrefix("www.")

    /** "https://example.com/a/b" -> "example.com/a/b" for compact display. */
    fun displayUrl(url: String): String =
        url.replaceFirst(Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE), "").trimEnd('/')

    private fun cleanTrailing(raw: String): String {
        var s = raw
        while (s.isNotEmpty()) {
            val strip = when (s.last()) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '>', '*' -> true
                ')' -> s.count { it == ')' } > s.count { it == '(' }
                ']' -> s.count { it == ']' } > s.count { it == '[' }
                '}' -> s.count { it == '}' } > s.count { it == '{' }
                else -> false
            }
            if (!strip) break
            s = s.dropLast(1)
        }
        return s
    }
}

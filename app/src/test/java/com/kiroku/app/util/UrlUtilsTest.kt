package com.kiroku.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlUtilsTest {

    @Test fun extractsUrlFromChromeShare() =
        assertEquals("https://example.com/a?b=1", UrlUtils.extractUrl("https://example.com/a?b=1"))

    @Test fun extractsUrlFromTitlePlusLink() =
        assertEquals(
            "https://news.site.com/story/42",
            UrlUtils.extractUrl("Great read: Battery cells explained https://news.site.com/story/42."),
        )

    @Test fun keepsBalancedParentheses() =
        assertEquals(
            "https://en.wikipedia.org/wiki/Tesla_(unit)",
            UrlUtils.extractUrl("see (https://en.wikipedia.org/wiki/Tesla_(unit))"),
        )

    @Test fun acceptsBareDomain() =
        assertEquals("https://example.com/page", UrlUtils.extractUrl("example.com/page"))

    @Test fun rejectsPlainText() = assertNull(UrlUtils.extractUrl("just some words. Nothing here"))

    @Test fun normalizesForDuplicates() =
        assertEquals(
            "https://example.com/post?id=7",
            UrlUtils.normalize("HTTPS://Example.COM:443/post?utm_source=x&id=7&fbclid=abc#comments"),
        )

    @Test fun normalizesRootSlash() =
        assertEquals("https://example.com", UrlUtils.normalize("https://example.com/"))

    @Test fun addsSchemeWhenMissing() =
        assertEquals("https://example.com/x", UrlUtils.normalize("example.com/x"))

    @Test fun keepsSpaHashRoutes() =
        assertEquals("https://app.io/#/item/3", UrlUtils.normalize("https://app.io/#/item/3"))

    @Test fun rejectsNonWebSchemes() = assertNull(UrlUtils.normalize("ftp://example.com/file"))

    @Test fun rejectsGarbage() = assertNull(UrlUtils.normalize("not a url"))

    @Test fun domainStripsWww() = assertEquals("example.com", UrlUtils.domainOf("https://www.example.com/a"))
}

package com.pureread.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UrlUtils] 单元测试。
 */
internal class UrlUtilsTest {

    @Test
    fun `normalizeUrl removes fragment and tracking params`() {
        val url = "https://example.com/novel/ch1.html?utm_source=index#comment"
        val result = UrlUtils.normalizeUrl(url)
        assertEquals("https://example.com/novel/ch1.html", result)
    }

    @Test
    fun `normalizeUrl lowercases scheme and host`() {
        val url = "HTTPS://EXAMPLE.COM/Path/"
        val result = UrlUtils.normalizeUrl(url)
        assertEquals("https://example.com/Path/", result)
    }

    @Test
    fun `normalizeUrl strips default https port`() {
        val url = "https://example.com:443/page"
        val result = UrlUtils.normalizeUrl(url)
        assertEquals("https://example.com/page", result)
    }

    @Test
    fun `resolveUrl resolves relative link against base`() {
        val baseUrl = "https://example.com/novel/list.html"
        val result = UrlUtils.resolveUrl(baseUrl, "ch1.html")
        assertEquals("https://example.com/novel/ch1.html", result)
    }

    @Test
    fun `resolveUrl resolves root relative link`() {
        val baseUrl = "https://example.com/novel/list.html"
        val result = UrlUtils.resolveUrl(baseUrl, "/ch1.html")
        assertEquals("https://example.com/ch1.html", result)
    }

    @Test
    fun `resolveUrl keeps absolute link unchanged`() {
        val baseUrl = "https://example.com/"
        val result = UrlUtils.resolveUrl(baseUrl, "https://cdn.com/app.apk")
        assertEquals("https://cdn.com/app.apk", result)
    }

    @Test
    fun `cleanLinkForCluster removes query fragment and trailing slash`() {
        val url = "https://example.com/novel/ch1.html?from=index#comment"
        val result = UrlUtils.cleanLinkForCluster(url)
        assertEquals("https://example.com/novel/ch1.html", result)
    }

    @Test
    fun `cleanLinkForCluster lowercases host`() {
        val url = "https://WWW.EXAMPLE.COM/Path/"
        val result = UrlUtils.cleanLinkForCluster(url)
        assertEquals("https://example.com/Path/", result)
    }

    @Test
    fun `isSameHost returns true for same host ignoring scheme`() {
        assertTrue(UrlUtils.isSameHost("https://example.com/a", "http://example.com/b"))
    }

    @Test
    fun `isSameHost returns false for different host`() {
        assertFalse(UrlUtils.isSameHost("https://example.com/a", "https://other.com/b"))
    }
}

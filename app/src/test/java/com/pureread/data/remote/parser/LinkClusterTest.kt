package com.pureread.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LinkCluster] 单元测试。
 */
internal class LinkClusterTest {

    @Test
    fun `clusterLinks groups links by parent directory`() {
        val links = listOf(
            "https://example.com/novel/ch1.html",
            "https://example.com/novel/ch2.html",
            "https://example.com/novel/ch3.html",
            "https://example.com/blog/post1.html",
            "https://example.com/blog/post2.html",
        )

        val result = LinkCluster.clusterLinks(links)

        assertEquals(2, result.size)
        assertEquals(
            listOf(
                "https://example.com/novel/ch1.html",
                "https://example.com/novel/ch2.html",
                "https://example.com/novel/ch3.html",
            ),
            result["example.com/novel"]
        )
        assertEquals(
            listOf(
                "https://example.com/blog/post1.html",
                "https://example.com/blog/post2.html",
            ),
            result["example.com/blog"]
        )
    }

    @Test
    fun `clusterLinks keeps single links under host key`() {
        val links = listOf(
            "https://example.com/single/page.html",
        )

        val result = LinkCluster.clusterLinks(links)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("example.com/single"))
    }

    @Test
    fun `clusterLinks preserves duplicate urls in group`() {
        val links = listOf(
            "https://example.com/novel/ch1.html",
            "https://example.com/novel/ch1.html",
            "https://example.com/novel/ch2.html",
        )

        val result = LinkCluster.clusterLinks(links)

        assertEquals(3, result["example.com/novel"]?.size)
    }

    @Test
    fun `cleanLinkForCluster removes www query and fragment`() {
        val url = "https://www.Example.COM/novel/ch1.html?from=index#comment"
        val result = LinkCluster.cleanLinkForCluster(url)
        assertEquals("example.com/novel/ch1.html", result)
    }

    @Test
    fun `cleanLinkForCluster returns original for malformed url`() {
        val url = "not a url"
        val result = LinkCluster.cleanLinkForCluster(url)
        assertEquals(url, result)
    }
}

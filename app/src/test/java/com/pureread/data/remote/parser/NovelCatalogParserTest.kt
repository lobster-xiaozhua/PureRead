package com.pureread.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NovelCatalogParser] 单元测试。
 */
internal class NovelCatalogParserTest {

    private val baseUrl = "https://example.com/novel/"
    private val parser = NovelCatalogParser()

    @Test
    fun `parseCatalog extracts chapter links from anchors`() {
        val html = """
            <html>
              <body>
                <h1>目录</h1>
                <ul>
                  <li><a href="ch1.html">第一章 启程</a></li>
                  <li><a href="ch2.html">第二章 迷雾</a></li>
                  <li><a href="ch3.html">第三章 归途</a></li>
                </ul>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseCatalog(html, baseUrl)

        assertEquals(3, result.size)
        assertEquals("第一章 启程", result[0].title)
        assertEquals("https://example.com/novel/ch1.html", result[0].url)
        assertEquals(0, result[0].index)
        assertEquals("https://example.com/novel/ch2.html", result[1].url)
        assertEquals("https://example.com/novel/ch3.html", result[2].url)
    }

    @Test
    fun `parseCatalog falls back to all anchors when list count is below threshold`() {
        val html = """
            <html>
              <body>
                <a href="ch1.html">第一章</a>
                <a href="ch2.html">第二章</a>
                <a href="ch3.html">第三章</a>
                <a href="#top">返回顶部</a>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseCatalog(html, baseUrl)

        assertEquals(3, result.size)
        assertTrue(result.none { it.url.contains("#") })
    }

    @Test
    fun `parseCatalog returns empty list for empty html`() {
        val result = parser.parseCatalog("<html></html>", baseUrl)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCatalog normalizes absolute links`() {
        val html = """
            <html>
              <body>
                <ul>
                  <li><a href="https://other.com/novel/ch1.html">第一章</a></li>
                </ul>
              </body>
            </html>
        """.trimIndent()

        val result = parser.parseCatalog(html, baseUrl)

        assertEquals(1, result.size)
        assertEquals("https://other.com/novel/ch1.html", result[0].url)
    }

    @Test
    fun `findNextPageUrl extracts next page anchor`() {
        val html = """
            <html>
              <body>
                <a href="page2.html">下一页</a>
              </body>
            </html>
        """.trimIndent()

        val result = parser.findNextPageUrl(html, baseUrl)

        assertEquals("https://example.com/novel/page2.html", result)
    }

    @Test
    fun `findNextPageUrl returns empty when no next page`() {
        val html = "<html><body><a href=\"ch1.html\">第一章</a></body></html>"
        val result = parser.findNextPageUrl(html, baseUrl)
        assertEquals("", result)
    }
}

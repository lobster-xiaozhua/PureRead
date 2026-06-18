package com.pureread.data.remote.api

import com.pureread.data.model.Result
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DefaultArticleExtractor] 单元测试。
 */
internal class DefaultArticleExtractorTest {

    private lateinit var readabilityJSBridge: ReadabilityJSBridge
    private lateinit var extractor: DefaultArticleExtractor

    @Before
    fun setUp() {
        readabilityJSBridge = mockk(relaxed = true)
        extractor = DefaultArticleExtractor(readabilityJSBridge)
    }

    @Test
    fun `extract returns js result when valid`() = runTest {
        val expected = successResult("JS 标题", "a".repeat(500))
        coEvery { readabilityJSBridge.extractFromHtml(any(), any()) } returns expected

        val result = extractor.extract("https://example.com/article", "<html></html>")

        assertTrue(result is Result.Success)
        assertEquals("JS 标题", (result as Result.Success).data.title)
    }

    @Test
    fun `extract falls back to jsoup heuristic when js fails`() = runTest {
        coEvery { readabilityJSBridge.extractFromHtml(any(), any()) } returns
            Result.Error(com.pureread.data.model.PureError.Extract("JS 失败"))

        val html = """
            <html>
              <head><title>Jsoup 标题</title></head>
              <body>
                <article>${"正文 ".repeat(200)}</article>
              </body>
            </html>
        """.trimIndent()

        val result = extractor.extract("https://example.com/article", html)

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals("Jsoup 标题", data.title)
        assertTrue(data.bodyHtml.length >= 200)
    }

    @Test
    fun `extract returns raw html when all strategies fail`() = runTest {
        coEvery { readabilityJSBridge.extractFromHtml(any(), any()) } returns
            Result.Error(com.pureread.data.model.PureError.Extract("JS 失败"))

        val html = "<html><body><p>短</p></body></html>"

        val result = extractor.extract("https://example.com/article", html)

        assertTrue(result is Result.Success)
    }

    private fun successResult(title: String, bodyHtml: String): Result.Success<ArticleExtractResult> {
        return Result.Success(
            ArticleExtractResult(
                title = title,
                bodyHtml = bodyHtml,
                plainText = bodyHtml,
                siteName = "example.com",
                coverImageUrl = "",
                url = "https://example.com/article"
            )
        )
    }
}

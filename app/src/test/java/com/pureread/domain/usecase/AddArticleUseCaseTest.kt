package com.pureread.domain.usecase

import com.pureread.data.local.dao.ArticleBodyDao
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.Result
import com.pureread.data.remote.api.ArticleExtractResult
import com.pureread.data.remote.api.ArticleExtractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AddArticleUseCase] 单元测试。
 */
internal class AddArticleUseCaseTest {

    private lateinit var articleDao: ArticleDao
    private lateinit var articleBodyDao: ArticleBodyDao
    private lateinit var articleExtractor: ArticleExtractor
    private lateinit var useCase: AddArticleUseCase

    @Before
    fun setUp() {
        articleDao = mockk(relaxed = true)
        articleBodyDao = mockk(relaxed = true)
        articleExtractor = mockk()
        useCase = AddArticleUseCase(articleDao, articleBodyDao, articleExtractor)
    }

    @Test
    fun `invoke saves article and body when extraction succeeds`() = runTest {
        val extractResult = ArticleExtractResult(
            title = "测试标题",
            bodyHtml = "<p>正文</p>",
            plainText = "正文",
            siteName = "example.com",
            coverImageUrl = "",
            url = "https://example.com/article"
        )
        coEvery { articleExtractor.extract(any(), any()) } returns Result.Success(extractResult)
        coEvery { articleDao.insertArticle(any()) } returns 42L

        val result = useCase("https://example.com/article", "<html></html>")

        assertTrue(result is Result.Success)
        assertEquals(42L, (result as Result.Success).data)
        coVerify { articleBodyDao.insertArticleBody(any<ArticleBodyEntity>()) }
    }

    @Test
    fun `invoke returns error when extraction fails`() = runTest {
        coEvery { articleExtractor.extract(any(), any()) } returns
            Result.Error(com.pureread.data.model.PureError.Extract("提取失败"))

        val result = useCase("https://example.com/article", "<html></html>")

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { articleDao.insertArticle(any<ArticleEntity>()) }
    }

    @Test
    fun `invoke returns storage error when dao throws`() = runTest {
        val extractResult = ArticleExtractResult(
            title = "测试标题",
            bodyHtml = "<p>正文</p>",
            plainText = "正文",
            siteName = "example.com",
            coverImageUrl = "",
            url = "https://example.com/article"
        )
        coEvery { articleExtractor.extract(any(), any()) } returns Result.Success(extractResult)
        coEvery { articleDao.insertArticle(any()) } throws RuntimeException("数据库异常")

        val result = useCase("https://example.com/article", "<html></html>")

        assertTrue(result is Result.Error)
    }
}

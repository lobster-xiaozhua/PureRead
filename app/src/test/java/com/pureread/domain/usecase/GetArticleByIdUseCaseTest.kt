package com.pureread.domain.usecase

import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.Result
import com.pureread.data.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GetArticleByIdUseCase] 单元测试。
 */
internal class GetArticleByIdUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var useCase: GetArticleByIdUseCase

    @Before
    fun setUp() {
        articleRepository = mockk()
        useCase = GetArticleByIdUseCase(articleRepository)
    }

    @Test
    fun `invoke returns article and body html`() = runTest {
        val article = createArticle(1L)
        val body = ArticleBodyEntity(
            articleId = 1L,
            bodyHtml = "<p>正文</p>",
            plainText = "正文"
        )
        coEvery { articleRepository.getArticleById(1L) } returns article
        coEvery { articleRepository.getArticleBodyById(1L) } returns body

        val result = useCase(1L)

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(article, data.first)
        assertEquals(body.bodyHtml, data.second)
    }

    @Test
    fun `invoke returns article with empty body when body missing`() = runTest {
        val article = createArticle(2L)
        coEvery { articleRepository.getArticleById(2L) } returns article
        coEvery { articleRepository.getArticleBodyById(2L) } returns null

        val result = useCase(2L)

        assertTrue(result is Result.Success)
        assertEquals("", (result as Result.Success).data.second)
    }

    @Test
    fun `invoke returns not found when article missing`() = runTest {
        coEvery { articleRepository.getArticleById(3L) } returns null

        val result = useCase(3L)

        assertTrue(result is Result.Error)
    }

    private fun createArticle(articleId: Long): ArticleEntity {
        return ArticleEntity(
            articleId = articleId,
            title = "测试文章",
            sourceUrl = "https://example.com/article",
            url = "https://example.com/article"
        )
    }
}

package com.pureread.domain.usecase

import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.Result
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CreateNovelArticleUseCase] 单元测试。
 */
internal class CreateNovelArticleUseCaseTest {

    private lateinit var articleDao: ArticleDao
    private lateinit var useCase: CreateNovelArticleUseCase

    @Before
    fun setUp() {
        articleDao = mockk(relaxed = true)
        useCase = CreateNovelArticleUseCase(articleDao)
    }

    @Test
    fun `invoke creates novel article when not exists`() = runTest {
        coEvery { articleDao.getArticleByUrl(any()) } returns null
        coEvery { articleDao.insertArticle(any()) } returns 42L

        val result = useCase("https://example.com/novel", "小说标题")

        assertTrue(result is Result.Success)
        assertEquals(42L, (result as Result.Success).data)
        coVerify { articleDao.insertArticle(any<ArticleEntity>()) }
    }

    @Test
    fun `invoke returns existing article id when duplicated`() = runTest {
        val existing = ArticleEntity(
            articleId = 7L,
            url = "https://example.com/novel",
            title = "已有小说",
            sourceUrl = "https://example.com/novel"
        )
        coEvery { articleDao.getArticleByUrl(any()) } returns existing

        val result = useCase("https://example.com/novel", "小说标题")

        assertTrue(result is Result.Success)
        assertEquals(7L, (result as Result.Success).data)
    }
}

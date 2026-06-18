package com.pureread.domain.usecase

import com.pureread.data.model.Result
import com.pureread.data.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * [SaveArticleProgressUseCase] 单元测试。
 */
internal class SaveArticleProgressUseCaseTest {

    private lateinit var articleRepository: ArticleRepository
    private lateinit var useCase: SaveArticleProgressUseCase

    @Before
    fun setUp() {
        articleRepository = mockk(relaxed = true)
        useCase = SaveArticleProgressUseCase(articleRepository)
    }

    @Test
    fun `invoke delegates to repository`() = runTest {
        coEvery { articleRepository.updateReadProgress(1L, 75) } returns Result.Success(Unit)

        useCase(1L, 75)

        coVerify { articleRepository.updateReadProgress(1L, 75) }
    }

    @Test
    fun `invoke catches repository exception`() = runTest {
        coEvery { articleRepository.updateReadProgress(any(), any()) } throws RuntimeException("失败")

        useCase(1L, 50)

        coVerify { articleRepository.updateReadProgress(1L, 50) }
    }
}

package com.pureread.data.repository

import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity
import com.pureread.data.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 文章仓库单元测试。
 *
 * 说明：
 * - 由于当前 test 依赖未引入 Robolectric，无法直接跑 Room in-memory 数据库。
 * - 本测试使用内存实现的 [InMemoryArticleRepository] 验证仓库接口契约；
 *   Room 真实实现请使用 [com.pureread.data.repository.RoomArticleRepository] 在 androidTest 中覆盖。
 */
internal class ArticleRepositoryTest {

    private lateinit var repository: ArticleRepository

    @Before
    fun setUp() {
        repository = InMemoryArticleRepository()
    }

    @Test
    fun `addArticle returns success id`() = runTest {
        val result = repository.addArticle("https://example.com/article")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data > 0)
    }

    @Test
    fun `addArticle rejects blank url`() = runTest {
        val result = repository.addArticle(" ")

        assertTrue(result is Result.Error)
    }

    @Test
    fun `insert article body and retrieve by id`() = runTest {
        val article = createArticle(title = "有正文文章")
        val articleId = repository.insertArticle(article)
        val body = createBody(articleId)
        repository.insertArticleBody(body)

        val storedBody = repository.getArticleBodyById(articleId)
        assertEquals("正文内容", storedBody?.plainText)
    }

    @Test
    fun `observeArticles emits saved articles`() = runTest {
        repository.insertArticle(createArticle(title = "文章 A"))
        repository.insertArticle(createArticle(title = "文章 B"))

        val articles = repository.observeArticles().first()
        assertEquals(2, articles.size)
        assertTrue(articles.any { it.title == "文章 A" })
        assertTrue(articles.any { it.title == "文章 B" })
    }

    @Test
    fun `deleteArticle removes article`() = runTest {
        val articleId = repository.insertArticle(createArticle(title = "待删除"))

        val deleteResult = repository.deleteArticle(articleId)
        assertTrue(deleteResult is Result.Success)

        val getResult = repository.getArticle(articleId)
        assertTrue(getResult is Result.Error)
    }

    @Test
    fun `get nonexistent article returns error`() = runTest {
        val result = repository.getArticle(articleId = 999L)
        assertTrue(result is Result.Error)
    }

    @Test
    fun `updateReadProgress clamps and persists`() = runTest {
        val articleId = repository.insertArticle(createArticle())

        val result = repository.updateReadProgress(articleId, 150)

        assertTrue(result is Result.Success)
        val article = (repository.getArticle(articleId) as Result.Success).data
        assertEquals(100, article.readProgress)
    }

    @Test
    fun `searchArticles filters by title`() = runTest {
        repository.insertArticle(createArticle(title = "Kotlin 入门"))
        repository.insertArticle(createArticle(title = "Java 进阶"))

        val result = repository.searchArticles("kotlin").first()
        assertTrue(result is Result.Success)
        val articles = (result as Result.Success).data
        assertEquals(1, articles.size)
        assertEquals("Kotlin 入门", articles[0].title)
    }

    private fun createArticle(title: String = "测试文章"): ArticleEntity {
        return ArticleEntity(
            articleId = 0L,
            title = title,
            sourceUrl = "https://example.com/article",
            url = "https://example.com/article",
            extractTime = System.currentTimeMillis(),
            createTimeMs = System.currentTimeMillis(),
            updateTimeMs = System.currentTimeMillis(),
        )
    }

    private fun createBody(articleId: Long): ArticleBodyEntity {
        return ArticleBodyEntity(
            articleId = articleId,
            plainText = "正文内容",
            bodyHtml = "<p>正文内容</p>",
            extractConfidenceFloat = 0.95f,
        )
    }

    /**
     * 内存实现的文章仓库，仅用于单元测试验证接口契约。
     */
    private class InMemoryArticleRepository : ArticleRepository {

        private val articles = mutableMapOf<Long, ArticleEntity>()
        private val bodies = mutableMapOf<Long, ArticleBodyEntity>()
        private val chapters = mutableMapOf<Long, MutableList<ChapterEntity>>()
        private val downloadTasks = mutableListOf<DownloadTaskEntity>()
        private var nextId = 1L
        private var nextChapterId = 1L

        override suspend fun addArticle(urlString: String): Result<Long> {
            if (urlString.isBlank()) {
                return Result.Error(com.pureread.data.model.PureError.Extract("URL 为空"))
            }
            val article = createArticle().copy(
                articleId = 0L,
                url = urlString,
                sourceUrl = urlString,
            )
            val assignedId = insertArticle(article)
            return Result.Success(assignedId)
        }

        override fun getArticles(): Flow<Result<List<ArticleEntity>>> {
            return flowOf(Result.Success(articles.values.toList()))
        }

        override suspend fun getArticle(articleId: Long): Result<ArticleEntity> {
            val article = articles[articleId]
                ?: return Result.Error(com.pureread.data.model.PureError.NotFound("articleId=$articleId"))
            return Result.Success(article)
        }

        override suspend fun deleteArticle(articleId: Long): Result<Unit> {
            if (!articles.containsKey(articleId)) {
                return Result.Error(com.pureread.data.model.PureError.NotFound("articleId=$articleId"))
            }
            articles.remove(articleId)
            bodies.remove(articleId)
            chapters.remove(articleId)
            return Result.Success(Unit)
        }

        override fun searchArticles(queryString: String): Flow<Result<List<ArticleEntity>>> {
            val trimmedQuery = queryString.trim()
            val filtered = if (trimmedQuery.isBlank()) {
                articles.values.toList()
            } else {
                articles.values.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
            }
            return flowOf(Result.Success(filtered))
        }

        override suspend fun updateReadProgress(
            articleId: Long,
            readProgressPercentInt: Int
        ): Result<Unit> {
            val article = articles[articleId]
                ?: return Result.Error(com.pureread.data.model.PureError.NotFound("articleId=$articleId"))
            val updated = article.copy(
                readProgress = readProgressPercentInt.coerceIn(0, 100),
                lastReadTimeMs = System.currentTimeMillis()
            )
            articles[articleId] = updated
            return Result.Success(Unit)
        }

        override suspend fun refreshArticle(articleId: Long): Result<Unit> {
            val article = articles[articleId]
                ?: return Result.Error(com.pureread.data.model.PureError.NotFound("articleId=$articleId"))
            articles[articleId] = article.copy(
                extractStatus = "EXTRACTING",
                extractTime = System.currentTimeMillis()
            )
            return Result.Success(Unit)
        }

        override suspend fun insertArticle(article: ArticleEntity): Long {
            val assignedId = nextId++
            val savedArticle = article.copy(articleId = assignedId)
            articles[assignedId] = savedArticle
            return assignedId
        }

        override suspend fun insertArticleBody(body: ArticleBodyEntity) {
            bodies[body.articleId] = body
        }

        override fun observeArticles(): Flow<List<ArticleEntity>> {
            return flowOf(articles.values.toList())
        }

        override suspend fun getArticleById(articleId: Long): ArticleEntity? {
            return articles[articleId]
        }

        override suspend fun getArticleBodyById(articleId: Long): ArticleBodyEntity? {
            return bodies[articleId]
        }

        override suspend fun updateArticle(
            articleId: Long,
            title: String,
            siteName: String,
            coverImageUrl: String,
            updatedAtMs: Long,
            status: String
        ) {
            val article = articles[articleId] ?: return
            articles[articleId] = article.copy(
                title = title,
                siteName = siteName,
                coverImageUrl = coverImageUrl,
                updateTimeMs = updatedAtMs,
                status = status
            )
        }

        override suspend fun updateArticleBody(
            articleId: Long,
            bodyHtml: String,
            plainText: String
        ) {
            val existing = bodies[articleId]
            bodies[articleId] = existing?.copy(
                bodyHtml = bodyHtml,
                plainText = plainText
            ) ?: ArticleBodyEntity(
                articleId = articleId,
                bodyHtml = bodyHtml,
                plainText = plainText
            )
        }

        override suspend fun getChaptersByArticleId(articleId: Long): List<ChapterEntity> {
            return chapters[articleId] ?: emptyList()
        }

        override suspend fun insertChapter(chapter: ChapterEntity): Long {
            val assignedId = nextChapterId++
            val savedChapter = chapter.copy(chapterId = assignedId)
            chapters.getOrPut(chapter.articleId) { mutableListOf() }.add(savedChapter)
            return assignedId
        }

        override suspend fun insertDownloadTask(task: DownloadTaskEntity) {
            downloadTasks.add(task)
        }
    }
}

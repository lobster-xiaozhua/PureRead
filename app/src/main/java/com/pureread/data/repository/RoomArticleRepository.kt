package com.pureread.data.repository

import com.pureread.core.log.PureLog
import com.pureread.data.local.PureReadDatabase
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 基于 Room 的文章仓库实现。
 *
 * 职责：
 * - 通过 [PureReadDatabase] 在事务中完成主表与 Body 表写入
 * - 将 Room 异常转换为 [PureError] 业务错误
 *
 * 线程安全：所有挂起方法切换至 [Dispatchers.IO]，DAO 操作为 Room 线程安全。
 */
public class RoomArticleRepository(
    private val database: PureReadDatabase,
) : ArticleRepository {

    private val articleDao = database.articleDao()
    private val articleBodyDao = database.articleBodyDao()
    private val chapterDao = database.chapterDao()
    private val downloadTaskDao = database.downloadTaskDao()

    public override suspend fun addArticle(urlString: String): Result<Long> = withContext(Dispatchers.IO) {
        if (urlString.isBlank()) {
            return@withContext Result.Error(PureError.Extract(messageString = "URL 为空"))
        }
        try {
            val existing = articleDao.getArticleByUrl(urlString)
            if (existing != null) {
                PureLog.i(TAG, "addArticle", "文章已存在 | articleId=${existing.id}")
                return@withContext Result.Success(existing.id)
            }
            val article = ArticleEntity(
                url = urlString,
                sourceUrl = urlString,
                extractTime = System.currentTimeMillis(),
                createTimeMs = System.currentTimeMillis(),
                updateTimeMs = System.currentTimeMillis(),
                status = ArticleEntity.STATUS_PENDING
            )
            val articleId = articleDao.insertArticle(article)
            PureLog.i(TAG, "addArticle", "添加成功 | articleId=$articleId")
            Result.Success(articleId)
        } catch (e: Exception) {
            PureLog.e(TAG, "addArticle", e, "添加失败")
            Result.Error(PureError.Storage(throwable = e, messageString = "添加文章失败"))
        }
    }

    public override fun getArticles(): Flow<Result<List<ArticleEntity>>> {
        return articleDao.observeArticles()
            .map { articleList -> Result.Success(articleList) }
            .catch { throwable ->
                PureLog.e(TAG, "getArticles", throwable, "观察列表失败")
                emit(Result.Error(PureError.Storage(throwable = throwable, messageString = "观察文章列表失败")))
            }
    }

    public override suspend fun getArticle(articleId: Long): Result<ArticleEntity> = withContext(Dispatchers.IO) {
        try {
            val article = articleDao.getArticleById(articleId)
            if (article == null) {
                Result.Error(PureError.NotFound(resourceNameString = "articleId=$articleId"))
            } else {
                Result.Success(article)
            }
        } catch (e: Exception) {
            PureLog.e(TAG, "getArticle", e, "查询失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "查询文章失败"))
        }
    }

    public override suspend fun deleteArticle(articleId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val article = articleDao.getArticleById(articleId)
                ?: return@withContext Result.Error(PureError.NotFound(resourceNameString = "articleId=$articleId"))
            articleDao.deleteArticle(article)
            PureLog.i(TAG, "deleteArticle", "删除成功 | articleId=$articleId")
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e(TAG, "deleteArticle", e, "删除失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "删除文章失败"))
        }
    }

    public override fun searchArticles(queryString: String): Flow<Result<List<ArticleEntity>>> {
        val trimmedQuery = queryString.trim()
        return articleDao.observeArticles()
            .map { list ->
                val filtered = if (trimmedQuery.isBlank()) {
                    list
                } else {
                    list.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
                }
                Result.Success(filtered)
            }
            .catch { throwable ->
                PureLog.e(TAG, "searchArticles", throwable, "搜索失败 | query=$trimmedQuery")
                emit(Result.Error(PureError.Storage(throwable = throwable, messageString = "搜索文章失败")))
            }
    }

    public override suspend fun updateReadProgress(
        articleId: Long,
        readProgressPercentInt: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val clampedProgress = readProgressPercentInt.coerceIn(0, 100)
        val nowMs = System.currentTimeMillis()
        try {
            val affectedRows = articleDao.updateReadProgress(
                articleId = articleId,
                readProgressPercentInt = clampedProgress,
                lastReadTimeMs = nowMs
            )
            if (affectedRows == 0) {
                return@withContext Result.Error(PureError.NotFound(resourceNameString = "articleId=$articleId"))
            }
            PureLog.d(TAG, "updateReadProgress", "更新成功 | articleId=$articleId progress=$clampedProgress")
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e(TAG, "updateReadProgress", e, "更新失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "更新阅读进度失败"))
        }
    }

    public override suspend fun refreshArticle(articleId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val article = articleDao.getArticleById(articleId)
                ?: return@withContext Result.Error(PureError.NotFound(resourceNameString = "articleId=$articleId"))
            val nowMs = System.currentTimeMillis()
            val refreshing = article.copy(
                status = ArticleEntity.STATUS_EXTRACTING,
                extractTime = nowMs,
                updateTimeMs = nowMs
            )
            articleDao.updateArticle(refreshing)
            PureLog.i(TAG, "refreshArticle", "触发刷新 | articleId=$articleId")
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e(TAG, "refreshArticle", e, "刷新失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "刷新文章失败"))
        }
    }

    /**
     * 保存文章（主表 + Body 表事务写入）。
     *
     * 前置条件：[article] 主表实体必填；[body] 可选。
     * 后置条件：主表与 Body 表均持久化成功，或全部回滚。
     * 副作用：写入磁盘数据库。
     */
    public suspend fun save(
        article: ArticleEntity,
        body: ArticleBodyEntity?,
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            var newArticleId: Long = -1
            database.runInTransaction {
                newArticleId = articleDao.insertArticle(article)
                if (body != null) {
                    articleBodyDao.insertArticleBody(body.copy(articleId = newArticleId))
                }
            }
            PureLog.i(TAG, "save", "成功 | articleId=$newArticleId")
            Result.Success(newArticleId)
        } catch (e: Exception) {
            PureLog.e(TAG, "save", e, "保存失败")
            Result.Error(PureError.Storage(throwable = e, messageString = "保存文章失败"))
        }
    }

    /**
     * 查询文章详情（联查正文）。
     */
    public suspend fun getArticleWithBody(
        articleId: Long,
    ): Result<Pair<ArticleEntity, ArticleBodyEntity?>> = withContext(Dispatchers.IO) {
        try {
            val article = articleDao.getArticleById(articleId)
            if (article == null) {
                Result.Error(PureError.NotFound(resourceNameString = "articleId=$articleId"))
            } else {
                val body = articleBodyDao.getArticleBodyById(articleId)
                Result.Success(article to body)
            }
        } catch (e: Exception) {
            PureLog.e(TAG, "getArticleWithBody", e, "查询失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "查询文章失败"))
        }
    }

    // region 低阶 CRUD

    public override suspend fun insertArticle(article: ArticleEntity): Long =
        articleDao.insertArticle(article)

    public override suspend fun insertArticleBody(body: ArticleBodyEntity): Unit {
        articleBodyDao.insertArticleBody(body)
    }

    public override fun observeArticles(): Flow<List<ArticleEntity>> {
        return articleDao.observeArticles()
    }

    public override suspend fun getArticleById(articleId: Long): ArticleEntity? =
        articleDao.getArticleById(articleId)

    public override suspend fun getArticleBodyById(articleId: Long): ArticleBodyEntity? =
        articleBodyDao.getArticleBodyById(articleId)

    public override suspend fun updateArticle(
        articleId: Long,
        title: String,
        siteName: String,
        coverImageUrl: String,
        updatedAtMs: Long,
        status: String
    ): Unit {
        articleDao.updateArticle(
            articleId = articleId,
            title = title,
            siteName = siteName,
            coverImageUrl = coverImageUrl,
            updatedAtMs = updatedAtMs,
            status = status
        )
    }

    public override suspend fun updateArticleBody(
        articleId: Long,
        bodyHtml: String,
        plainText: String
    ): Unit {
        articleBodyDao.insertArticleBody(
            ArticleBodyEntity(
                articleId = articleId,
                bodyHtml = bodyHtml,
                plainText = plainText
            )
        )
    }

    public override suspend fun getChaptersByArticleId(articleId: Long): List<ChapterEntity> =
        chapterDao.getChaptersByArticleId(articleId)

    public override suspend fun insertChapter(chapter: ChapterEntity): Long =
        chapterDao.insertChapter(chapter)

    public override suspend fun insertDownloadTask(task: DownloadTaskEntity): Unit {
        downloadTaskDao.insertDownloadTask(task)
    }

    // endregion

    /**
     * 删除文章及其正文。
     */
    public suspend fun delete(articleId: Long): Result<Unit> = deleteArticle(articleId)

    private companion object {
        private const val TAG = "RoomArticleRepository"
    }
}

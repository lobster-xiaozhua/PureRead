package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.data.local.dao.ArticleBodyDao
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.data.remote.api.ArticleExtractor
import com.pureread.data.remote.api.ArticleExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 添加单篇文章用例。
 *
 * 职责：提取文章内容并保存到数据库（主表 + Body 表）。
 *
 * 线程安全：无状态，线程安全。
 */
public class AddArticleUseCase(
    private val articleDao: ArticleDao,
    private val articleBodyDao: ArticleBodyDao,
    private val articleExtractor: ArticleExtractor
) {

    private companion object {
        private const val TAG = "AddArticleUseCase"
    }

    /**
     * 添加文章。
     *
     * @param urlString 文章 URL
     * @param htmlString 原始 HTML
     * @return 插入后的 articleId
     */
    public suspend operator fun invoke(
        urlString: String,
        htmlString: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：urlString 非空
        // 后置条件：文章与正文已持久化
        // 副作用：写入数据库
        when (val extractResult = articleExtractor.extract(urlString, htmlString)) {
            is Result.Success -> {
                saveArticle(extractResult.data, startTimeMs)
            }
            is Result.Error -> {
                PureLog.w(
                    TAG,
                    "invoke",
                    "提取失败 | error=${extractResult.error} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
                )
                extractResult
            }
        }
    }

    private suspend fun saveArticle(
        articleExtractResult: ArticleExtractResult,
        startTimeMs: Long
    ): Result<Long> {
        val nowMs = System.currentTimeMillis()
        val articleEntity = ArticleEntity(
            url = articleExtractResult.url,
            title = articleExtractResult.title,
            siteName = articleExtractResult.siteName,
            coverImageUrl = articleExtractResult.coverImageUrl,
            sourceUrl = articleExtractResult.url,
            wordCount = articleExtractResult.plainText.length,
            createTimeMs = nowMs,
            updateTimeMs = nowMs,
            extractTime = nowMs,
            status = ArticleEntity.STATUS_COMPLETED
        )
        return try {
            val articleId = articleDao.insertArticle(articleEntity)
            val bodyEntity = ArticleBodyEntity(
                articleId = articleId,
                bodyHtml = articleExtractResult.bodyHtml,
                plainText = articleExtractResult.plainText,
                extractConfidenceFloat = 0f
            )
            articleBodyDao.insertArticleBody(bodyEntity)
            PureLog.i(
                TAG,
                "saveArticle",
                "保存成功 | articleId=$articleId | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(articleId)
        } catch (e: Exception) {
            PureLog.e(TAG, "saveArticle", e, "数据库保存失败")
            Result.Error(PureError.Storage(throwable = e, messageString = "保存文章失败"))
        }
    }
}

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
import com.pureread.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 刷新文章用例。
 *
 * 职责：重新提取文章并更新数据库。
 *
 * 线程安全：无状态，线程安全。
 */
public class RefreshArticleUseCase(
    private val articleRepository: ArticleRepository,
    private val articleDao: ArticleDao,
    private val articleBodyDao: ArticleBodyDao,
    private val articleExtractor: ArticleExtractor
) {

    private companion object {
        private const val TAG = "RefreshArticleUseCase"
    }

    /**
     * 刷新文章。
     *
     * @param articleId 文章 ID
     * @param htmlString 新的原始 HTML
     * @return 空结果
     */
    public suspend operator fun invoke(
        articleId: Long,
        htmlString: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：articleId 有效
        // 后置条件：文章与正文已更新
        // 副作用：更新数据库
        when (val articleResult = articleRepository.getArticle(articleId)) {
            is Result.Error -> articleResult
            is Result.Success -> {
                val article = articleResult.data
                when (val extractResult = articleExtractor.extract(article.sourceUrl, htmlString)) {
                    is Result.Success -> {
                        updateArticle(articleId, extractResult.data, startTimeMs)
                    }
                    is Result.Error -> {
                        PureLog.w(
                            TAG,
                            "invoke",
                            "重新提取失败 | articleId=$articleId | error=${extractResult.error}"
                        )
                        extractResult
                    }
                }
            }
        }
    }

    private suspend fun updateArticle(
        articleId: Long,
        articleExtractResult: ArticleExtractResult,
        startTimeMs: Long
    ): Result<Unit> {
        return try {
            val nowMs = System.currentTimeMillis()
            val existing = articleDao.getArticleById(articleId)
            if (existing != null) {
                val updatedArticle = existing.copy(
                    title = articleExtractResult.title,
                    siteName = articleExtractResult.siteName,
                    coverImageUrl = articleExtractResult.coverImageUrl,
                    wordCount = articleExtractResult.plainText.length,
                    extractTime = nowMs,
                    updateTimeMs = nowMs,
                    status = ArticleEntity.STATUS_COMPLETED
                )
                articleDao.updateArticle(updatedArticle)
            }
            articleBodyDao.insertArticleBody(
                ArticleBodyEntity(
                    articleId = articleId,
                    bodyHtml = articleExtractResult.bodyHtml,
                    plainText = articleExtractResult.plainText,
                    extractConfidenceFloat = 0f
                )
            )
            PureLog.i(
                TAG,
                "updateArticle",
                "刷新成功 | articleId=$articleId | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e(TAG, "updateArticle", e, "刷新保存失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "刷新文章失败"))
        }
    }
}

package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.core.utils.UrlUtils
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 创建小说文章记录用例。
 *
 * 职责：为小说目录页创建一条 article 主表记录，供后续章节下载关联。
 *
 * 线程安全：无状态，线程安全。
 */
public class CreateNovelArticleUseCase(
    private val articleDao: ArticleDao
) {

    private companion object {
        private const val TAG = "CreateNovelArticleUseCase"
    }

    /**
     * 创建小说文章记录。
     *
     * @param urlString 小说目录页 URL
     * @param titleString 小说标题
     * @return 新增 articleId 或错误
     */
    public suspend operator fun invoke(
        urlString: String,
        titleString: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        val normalizedUrl = UrlUtils.normalizeUrl(urlString)
        if (normalizedUrl.isBlank()) {
            return@withContext Result.Error(PureError.Extract(messageString = "URL 无效"))
        }
        try {
            val existing = articleDao.getArticleByUrl(normalizedUrl)
            if (existing != null) {
                PureLog.i(TAG, "invoke", "小说记录已存在 | articleId=${existing.id}")
                return@withContext Result.Success(existing.id)
            }
            val nowMs = System.currentTimeMillis()
            val articleEntity = ArticleEntity(
                url = normalizedUrl,
                sourceUrl = normalizedUrl,
                title = titleString,
                siteName = UrlUtils.cleanLinkForCluster(normalizedUrl),
                createTimeMs = nowMs,
                updateTimeMs = nowMs,
                extractTime = nowMs,
                status = ArticleEntity.STATUS_PENDING
            )
            val articleId = articleDao.insertArticle(articleEntity)
            PureLog.i(
                TAG,
                "invoke",
                "创建小说记录 | articleId=$articleId | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(articleId)
        } catch (e: Exception) {
            PureLog.e(TAG, "invoke", e, "创建小说记录失败")
            Result.Error(PureError.Storage(throwable = e, messageString = "创建小说记录失败"))
        }
    }
}

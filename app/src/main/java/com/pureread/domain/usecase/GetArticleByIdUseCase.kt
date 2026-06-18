package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按 ID 获取文章及正文用例。
 *
 * 职责：联查文章主表与 Body 表，返回正文 HTML 字符串。
 *
 * 线程安全：无状态，线程安全。
 */
public class GetArticleByIdUseCase(
    private val articleRepository: ArticleRepository
) {

    private companion object {
        private const val TAG = "GetArticleByIdUseCase"
    }

    /**
     * 获取文章与正文 HTML。
     *
     * @param articleIdLong 文章 ID
     * @return Pair<文章实体, 正文 HTML> 或错误
     */
    public suspend operator fun invoke(
        articleIdLong: Long
    ): Result<Pair<ArticleEntity, String>> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        try {
            val article = articleRepository.getArticleById(articleIdLong)
                ?: return@withContext Result.Error(
                    PureError.NotFound(resourceNameString = "articleId=$articleIdLong")
                )
            val body = articleRepository.getArticleBodyById(articleIdLong)
            val bodyHtml = body?.bodyHtml ?: ""
            PureLog.i(
                TAG,
                "invoke",
                "查询成功 | articleId=$articleIdLong | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(article to bodyHtml)
        } catch (e: Exception) {
            PureLog.e(TAG, "invoke", e, "查询失败 | articleId=$articleIdLong")
            Result.Error(PureError.Storage(throwable = e, messageString = "查询文章失败"))
        }
    }
}

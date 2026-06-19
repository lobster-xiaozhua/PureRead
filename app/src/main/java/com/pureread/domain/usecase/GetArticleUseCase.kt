package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.data.local.dao.ArticleBodyDao
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pureread.data.model.Result

/**
 * 获取单篇文章及正文用例。
 *
 * 职责：联查文章主表与 Body 表。
 *
 * 线程安全：无状态，线程安全。
 */
public class GetArticleUseCase(
    private val articleRepository: ArticleRepository,
    private val articleBodyDao: ArticleBodyDao
) {

    private companion object {
        private const val TAG = "GetArticleUseCase"
    }

    /**
     * 获取文章与正文。
     *
     * @param articleId 文章 ID
     * @return Pair<ArticleEntity, ArticleBodyEntity?>
     */
    public suspend operator fun invoke(
        articleId: Long
    ): Result<Pair<ArticleEntity, ArticleBodyEntity?>> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        when (val articleResult = articleRepository.getArticle(articleId)) {
            is Result.Error -> articleResult
            is Result.Success -> {
                val body = articleBodyDao.getArticleBodyById(articleId)
                PureLog.i(
                    TAG,
                    "invoke",
                    "查询成功 | articleId=$articleId | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
                )
                Result.Success(articleResult.data to body)
            }
        }
    }
}

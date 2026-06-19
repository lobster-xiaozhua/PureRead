package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 保存阅读进度用例。
 *
 * 职责：将阅读进度百分比持久化到数据库。
 *
 * 线程安全：无状态，线程安全。
 */
public class SaveArticleProgressUseCase(
    private val articleRepository: ArticleRepository
) {

    private companion object {
        private const val TAG = "SaveArticleProgressUseCase"
    }

    /**
     * 保存阅读进度。
     *
     * @param articleIdLong        文章 ID
     * @param progressPercentInt   进度百分比（0-100）
     */
    public suspend operator fun invoke(
        articleIdLong: Long,
        progressPercentInt: Int
    ): Unit = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        try {
            articleRepository.updateReadProgress(articleIdLong, progressPercentInt)
            PureLog.d(
                TAG,
                "invoke",
                "保存成功 | articleId=$articleIdLong | progress=$progressPercentInt% | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
        } catch (e: Exception) {
            PureLog.e(TAG, "invoke", e, "保存失败 | articleId=$articleIdLong")
        }
    }
}

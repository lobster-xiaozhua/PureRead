package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.core.utils.FileUtils
import com.pureread.data.local.dao.ChapterDao
import com.pureread.data.model.Result
import com.pureread.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 删除文章用例。
 *
 * 职责：删除文章、正文、章节记录及本地文件。
 *
 * 线程安全：无状态，线程安全。
 */
public class DeleteArticleUseCase(
    private val articleRepository: ArticleRepository,
    private val chapterDao: ChapterDao
) {

    private companion object {
        private const val TAG = "DeleteArticleUseCase"
    }

    /**
     * 批量删除文章。
     *
     * @param articleIdList 文章 ID 列表
     * @return 空结果
     */
    public suspend operator fun invoke(
        articleIdList: List<Long>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：articleIdList 非空
        // 后置条件：文章及相关数据已从数据库和文件系统移除
        // 副作用：删除数据库记录与本地文件
        if (articleIdList.isEmpty()) {
            return@withContext Result.Success(Unit)
        }
        var firstError: Result<Unit>? = null
        articleIdList.forEach { articleId ->
            if (firstError != null) {
                return@forEach
            }
            try {
                val chapterList = chapterDao.observeChaptersByArticleId(articleId).first()
                chapterList.forEach { chapterEntity ->
                    val localPath = chapterEntity.localPath
                    if (localPath.isNotBlank()) {
                        FileUtils.deleteFile(localPath)
                    }
                }
            } catch (e: Exception) {
                PureLog.e(TAG, "invoke", e, "删除文件失败 | articleId=$articleId")
            }
            when (val deleteResult = articleRepository.deleteArticle(articleId)) {
                is Result.Error -> firstError = deleteResult
                is Result.Success -> { /* 继续下一篇 */ }
            }
        }
        PureLog.i(
            TAG,
            "invoke",
            "删除完成 | count=${articleIdList.size} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
        )
        firstError ?: Result.Success(Unit)
    }
}

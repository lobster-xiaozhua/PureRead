package com.pureread.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pureread.core.log.PureLog
import com.pureread.core.network.NetworkObserver
import com.pureread.data.local.dao.ChapterDao
import com.pureread.data.local.dao.DownloadTaskDao
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.data.remote.parser.ChapterCandidate
import com.pureread.data.remote.parser.NovelCatalogParser
import com.pureread.worker.NovelDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 下载小说用例。
 *
 * 职责：解析目录、创建章节与下载任务，并加入 WorkManager 队列。
 *
 * 线程安全：无状态，线程安全。
 */
public class DownloadNovelUseCase(
    private val novelCatalogParser: NovelCatalogParser,
    private val chapterDao: ChapterDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val workManager: WorkManager,
    private val networkObserver: NetworkObserver
) {

    private companion object {
        private const val TAG = "DownloadNovelUseCase"
        private const val WORK_NAME_PREFIX = "novel_download_"
    }

    /**
     * 开始小说下载。
     *
     * @param articleId 小说文章 ID
     * @param catalogHtmlString 目录页 HTML
     * @param baseUrlString 目录页基础 URL
     * @return 空结果
     */
    public suspend operator fun invoke(
        articleId: Long,
        catalogHtmlString: String,
        baseUrlString: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：articleId 有效，catalogHtmlString 非空
        // 后置条件：章节、任务已持久化，Worker 已入队
        // 副作用：写入数据库并调度 Worker
        if (!networkObserver.isNetworkAvailable()) {
            PureLog.w(TAG, "invoke", "网络不可用，终止小说下载入队")
            return@withContext Result.Error(
                PureError.Network(messageString = "当前无网络，请连接后重试")
            )
        }

        val candidateList: List<ChapterCandidate> =
            novelCatalogParser.parseCatalog(catalogHtmlString, baseUrlString)
        if (candidateList.isEmpty()) {
            PureLog.w(TAG, "invoke", "未解析到章节 | articleId=$articleId")
            return@withContext Result.Error(
                PureError.Extract(messageString = "目录中未找到章节")
            )
        }
        try {
            saveCandidatesAndTask(articleId, candidateList)
            enqueueDownloadWorker(articleId)
            PureLog.i(
                TAG,
                "invoke",
                "小说下载已入队 | articleId=$articleId | chapterCount=${candidateList.size} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e(TAG, "invoke", e, "小说下载入队失败 | articleId=$articleId")
            Result.Error(PureError.Storage(throwable = e, messageString = "保存小说下载任务失败"))
        }
    }

    private suspend fun saveCandidatesAndTask(
        articleId: Long,
        candidateList: List<ChapterCandidate>
    ) {
        val chapterEntityList = candidateList.map { candidate ->
            ChapterEntity(
                articleId = articleId,
                url = candidate.url,
                title = candidate.title,
                indexInt = candidate.index,
                status = ChapterEntity.STATUS_PENDING,
                localPath = "",
                wordCountInt = 0
            )
        }
        chapterDao.insertChapters(chapterEntityList)
        val taskEntity = DownloadTaskEntity(
            articleId = articleId,
            type = DownloadTaskEntity.TYPE_CHAPTER,
            status = DownloadTaskEntity.STATUS_PENDING,
            totalCountInt = candidateList.size,
            completedCountInt = 0
        )
        downloadTaskDao.insertTask(taskEntity)
    }

    private fun enqueueDownloadWorker(articleId: Long) {
        val inputData = workDataOf(NovelDownloadWorker.KEY_ARTICLE_ID to articleId)
        val workRequest = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
            .setInputData(inputData)
            .build()
        val workName = "$WORK_NAME_PREFIX$articleId"
        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}

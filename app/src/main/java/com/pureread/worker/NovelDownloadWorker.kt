package com.pureread.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pureread.core.log.PureLog
import com.pureread.data.local.dao.ChapterDao
import com.pureread.data.local.dao.DownloadTaskDao
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity
import com.pureread.data.remote.downloader.ChapterDownloader
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * 小说批量下载 Worker。
 *
 * 职责：在后台批量下载小说章节并上报进度。
 *
 * 线程安全：由 WorkManager 单例调度，无共享可变状态。
 */
public class NovelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val chapterDao: ChapterDao by inject()
    private val downloadTaskDao: DownloadTaskDao by inject()
    private val chapterDownloader: ChapterDownloader by inject()

    /**
     * 执行小说章节批量下载。
     *
     * @return Worker 执行结果
     */
    public override suspend fun doWork(): androidx.work.Result {
        val startTimeMs = System.currentTimeMillis()
        val articleId = inputData.getLong(KEY_ARTICLE_ID, -1L)
        if (articleId < 0) {
            PureLog.w(TAG, "doWork", "articleId 无效")
            return androidx.work.Result.failure()
        }
        PureLog.i(TAG, "doWork", "开始下载 | articleId=$articleId")
        return try {
            processPendingTasks(articleId, startTimeMs)
        } catch (e: Exception) {
            PureLog.e(TAG, "doWork", e, "Worker 异常 | articleId=$articleId")
            androidx.work.Result.failure()
        }
    }

    private suspend fun processPendingTasks(
        articleId: Long,
        startTimeMs: Long
    ): androidx.work.Result {
        val pendingChapterList: List<ChapterEntity> =
            chapterDao.observeChaptersByArticleId(articleId).first()
                .filter { it.status == ChapterEntity.STATUS_PENDING }
        if (pendingChapterList.isEmpty()) {
            PureLog.i(TAG, "processPendingTasks", "无待下载任务 | articleId=$articleId")
            return androidx.work.Result.success()
        }

        val task = findOrCreateTask(articleId, pendingChapterList.size)
        updateTaskStatus(task, DownloadTaskEntity.STATUS_RUNNING)

        val outputDir = File(applicationContext.filesDir, "novels/$articleId")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        var successCountInt = 0
        pendingChapterList.forEachIndexed { index, chapter ->
            val progressInt = (index + 1) * 100 / pendingChapterList.size
            setProgress(workDataOf(KEY_PROGRESS to progressInt))
            when (downloadSingleChapter(chapter, outputDir)) {
                is com.pureread.data.model.Result.Success -> {
                    successCountInt++
                    updateTaskProgress(task, successCountInt)
                }
                is com.pureread.data.model.Result.Error -> {
                    // 单章失败继续后续章节
                }
            }
        }

        val finalStatus = if (successCountInt == pendingChapterList.size) {
            DownloadTaskEntity.STATUS_COMPLETED
        } else {
            DownloadTaskEntity.STATUS_ERROR
        }
        updateTaskStatus(task, finalStatus, successCountInt)
        PureLog.i(
            TAG,
            "processPendingTasks",
            "批量下载完成 | success=$successCountInt total=${pendingChapterList.size} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
        )
        return if (successCountInt == pendingChapterList.size) {
            androidx.work.Result.success()
        } else {
            androidx.work.Result.failure()
        }
    }

    private suspend fun findOrCreateTask(
        articleId: Long,
        totalCountInt: Int
    ): DownloadTaskEntity {
        val taskList = downloadTaskDao.observeTasksByArticleId(articleId).first()
        return taskList.firstOrNull { it.type == DownloadTaskEntity.TYPE_CHAPTER }
            ?: DownloadTaskEntity(
                articleId = articleId,
                type = DownloadTaskEntity.TYPE_CHAPTER,
                status = DownloadTaskEntity.STATUS_PENDING,
                totalCountInt = totalCountInt,
                completedCountInt = 0
            ).let { newTask ->
                val taskId = downloadTaskDao.insertTask(newTask)
                newTask.copy(taskId = taskId)
            }
    }

    private suspend fun downloadSingleChapter(
        chapter: ChapterEntity,
        outputDir: File
    ): com.pureread.data.model.Result<String> {
        val fileName = "chapter_${chapter.chapterId}"
        return when (val result = chapterDownloader.downloadChapter(chapter.url, outputDir, fileName)) {
            is com.pureread.data.model.Result.Success -> {
                chapterDao.updateStatusAndPath(
                    chapterId = chapter.chapterId,
                    status = ChapterEntity.STATUS_COMPLETED,
                    localPath = result.data,
                    updateTimeMs = System.currentTimeMillis()
                )
                result
            }
            is com.pureread.data.model.Result.Error -> {
                PureLog.w(TAG, "downloadSingleChapter", "下载失败 | chapterId=${chapter.chapterId}")
                chapterDao.updateStatusAndPath(
                    chapterId = chapter.chapterId,
                    status = ChapterEntity.STATUS_ERROR,
                    localPath = "",
                    updateTimeMs = System.currentTimeMillis()
                )
                result
            }
        }
    }

    private suspend fun updateTaskStatus(
        task: DownloadTaskEntity,
        status: String,
        completedCountInt: Int = task.completedCountInt
    ) {
        downloadTaskDao.updateTask(
            task.copy(status = status, completedCountInt = completedCountInt)
        )
    }

    private suspend fun updateTaskProgress(
        task: DownloadTaskEntity,
        completedCountInt: Int
    ) {
        downloadTaskDao.updateTask(task.copy(completedCountInt = completedCountInt))
    }

    public companion object {
        public const val KEY_ARTICLE_ID: String = "article_id"
        public const val KEY_PROGRESS: String = "progress"
        private const val TAG = "NovelDownloadWorker"
    }
}

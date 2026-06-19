package com.pureread.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.pureread.core.log.PureLog
import com.pureread.core.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * 后台 APK 下载 Worker。
 *
 * 职责：
 * - 在后台使用 OkHttp 下载 APK
 * - 通过 [UpdateNotificationHelper] 展示进度/完成/失败通知
 * - 下载完成后返回文件路径
 *
 * 线程安全：每次 Worker 实例独立运行，不共享可变状态。
 */
public class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val notificationHelper = UpdateNotificationHelper(context)

    /**
     * 执行后台下载任务。
     *
     * 前置条件：
     * - InputData 包含 [KEY_APK_URL] 与 [KEY_FILE_NAME]
     * - 应用私有目录可写
     *
     * 后置条件：
     * - 成功时 OutputData 包含 [KEY_OUTPUT_FILE_PATH]
     * - 失败时返回 [WorkResult.failure] 并附带错误信息
     */
    public override suspend fun doWork(): WorkResult {
        val apkUrl = inputData.getString(KEY_APK_URL)
        val fileName = inputData.getString(KEY_FILE_NAME)
        val title = inputData.getString(KEY_NOTIFICATION_TITLE) ?: DEFAULT_TITLE

        if (apkUrl.isNullOrBlank() || fileName.isNullOrBlank()) {
            return WorkResult.failure(
                Data.Builder()
                    .putString(KEY_OUTPUT_ERROR, "缺少 apkUrl 或 fileName")
                    .build()
            )
        }

        setForeground(createInitialForegroundInfo(title))

        return try {
            val resultFile = downloadWithProgress(apkUrl, fileName, title)
            notificationHelper.showCompleteNotification(
                title,
                "下载完成，点击安装"
            )
            WorkResult.success(
                Data.Builder()
                    .putString(KEY_OUTPUT_FILE_PATH, resultFile.absolutePath)
                    .build()
            )
        } catch (e: IOException) {
            PureLog.e("UpdateWorker", "doWork", e, "下载失败 | apkUrl=$apkUrl")
            notificationHelper.showErrorNotification(title, "下载失败: ${e.message}")
            WorkResult.failure(
                Data.Builder()
                    .putString(KEY_OUTPUT_ERROR, e.message)
                    .build()
            )
        } catch (e: Exception) {
            PureLog.e("UpdateWorker", "doWork", e, "未知异常 | apkUrl=$apkUrl")
            notificationHelper.showErrorNotification(title, "下载异常: ${e.message}")
            WorkResult.failure(
                Data.Builder()
                    .putString(KEY_OUTPUT_ERROR, e.message)
                    .build()
            )
        }
    }

    private suspend fun downloadWithProgress(
        apkUrl: String,
        fileName: String,
        title: String,
    ): File = withContext(Dispatchers.IO) {
        val apkDir = File(applicationContext.filesDir, APK_DIR_NAME)
        if (!FileUtils.ensureDirectory(apkDir)) {
            throw IOException("无法创建 APK 目录")
        }

        val targetFile = File(apkDir, "$fileName.apk")
        val tmpFile = File(apkDir, "$fileName.apk.tmp")

        val request = Request.Builder()
            .url(apkUrl)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("响应体为空")
            val totalBytes = body.contentLength().coerceAtLeast(0L)

            BufferedInputStream(body.byteStream()).use { inputStream ->
                FileOutputStream(tmpFile).use { outputStream ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE_INT)
                    var downloadedBytes = 0L
                    var lastReportedProgress = -1

                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val progressInt = ((downloadedBytes * 100) / totalBytes).toInt()
                            if (progressInt - lastReportedProgress >= PROGRESS_STEP_INT) {
                                lastReportedProgress = progressInt
                                updateProgressNotification(progressInt, title)
                            }
                        }
                    }
                    outputStream.flush()
                }
            }
        }

        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.delete()
            throw IOException("APK 文件重命名失败")
        }

        PureLog.i("UpdateWorker", "downloadWithProgress", "成功 | file=${targetFile.absolutePath}")
        targetFile
    }

    private suspend fun createInitialForegroundInfo(title: String): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            notificationHelper.buildProgressNotification(-1, title)
        )
    }

    private suspend fun updateProgressNotification(progressInt: Int, title: String): Unit {
        setForeground(
            ForegroundInfo(
                NOTIFICATION_ID,
                notificationHelper.buildProgressNotification(progressInt, title)
            )
        )
    }

    private companion object {
        private const val APK_DIR_NAME = "apks"
        private const val DEFAULT_TITLE = "PureRead 更新下载"
        private const val NOTIFICATION_ID = 1001
        private const val DOWNLOAD_BUFFER_SIZE_INT = 8 * 1024
        private const val PROGRESS_STEP_INT = 5

        public const val KEY_APK_URL: String = "apk_url"
        public const val KEY_FILE_NAME: String = "file_name"
        public const val KEY_NOTIFICATION_TITLE: String = "notification_title"
        public const val KEY_OUTPUT_FILE_PATH: String = "output_file_path"
        public const val KEY_OUTPUT_ERROR: String = "output_error"

        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

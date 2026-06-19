package com.pureread.data.remote.downloader

import com.pureread.core.log.PureLog
import com.pureread.core.utils.FileUtils
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 章节下载器。
 *
 * 职责：使用 OkHttp 下载章节内容并通过两阶段提交写入本地文件。
 *
 * 线程安全：依赖外部传入的 OkHttpClient，本身无状态。
 */
public class ChapterDownloader(
    private val okHttpClient: OkHttpClient
) {

    private companion object {
        private const val TAG = "ChapterDownloader"
        private const val MAX_BODY_LENGTH_BYTES = 5L * 1024 * 1024
    }

    /**
     * 下载章节并保存到本地。
     *
     * @param urlString 章节 URL
     * @param outputDir 输出目录
     * @param fileName 文件名（不含扩展名）
     * @return 保存后的本地文件绝对路径
     */
    public suspend fun downloadChapter(
        urlString: String,
        outputDir: File,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：outputDir 存在且可写
        // 后置条件：返回本地文件路径或错误
        // 副作用：写入 .tmp 文件并重命名为 .txt
        try {
            val request = Request.Builder()
                .url(urlString)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                PureLog.w(
                    TAG,
                    "downloadChapter",
                    "HTTP 失败 | code=${response.code} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
                )
                return@withContext Result.Error(
                    PureError.Network(
                        throwable = null,
                        messageString = "HTTP ${response.code}"
                    )
                )
            }
            val bodyString = response.body?.string() ?: ""
            if (bodyString.toByteArray().size > MAX_BODY_LENGTH_BYTES) {
                return@withContext Result.Error(
                    PureError.Network(messageString = "章节内容超过大小限制")
                )
            }
            val localPath = writeChapterFile(outputDir, fileName, bodyString)
            PureLog.i(
                TAG,
                "downloadChapter",
                "完成 | localPath=$localPath | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(localPath)
        } catch (e: IOException) {
            PureLog.e(
                TAG,
                "downloadChapter",
                e,
                "网络异常 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Error(PureError.Network(throwable = e, messageString = "下载失败"))
        } catch (e: Exception) {
            PureLog.e(
                TAG,
                "downloadChapter",
                e,
                "未知异常 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Error(PureError.Fatal(e))
        }
    }

    private fun writeChapterFile(
        outputDir: File,
        fileName: String,
        contentString: String
    ): String {
        // 前置条件：outputDir 已存在
        // 后置条件：.txt 文件已原子创建
        // 副作用：创建并写入文件
        val finalFile = File(outputDir, "$fileName.txt")
        val isSuccess = FileUtils.writeTwoPhaseCommit(finalFile, contentString)
        if (!isSuccess) {
            throw IOException("两阶段提交失败: ${finalFile.absolutePath}")
        }
        return finalFile.absolutePath
    }
}

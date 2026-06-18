package com.pureread.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pureread.core.log.PureLog
import com.pureread.core.utils.FileUtils
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用更新管理器。
 *
 * 职责：
 * - 检查更新
 * - 下载 APK 到应用私有目录 `filesDir/apks/`
 * - 通过 [FileProvider] 发起安装 Intent
 *
 * 线程安全：[okHttpClient] 可复用，文件写入按调用方串行处理。
 */
public class UpdateManager(
    private val context: Context,
    private val updateChecker: UpdateChecker,
    private val okHttpClient: OkHttpClient = DEFAULT_OK_HTTP_CLIENT,
) {

    /**
     * 检查是否存在新版本。
     *
     * 前置条件：[serverUrl] 为合法更新接口地址。
     * 后置条件：返回 [Result.Success] 时，[UpdateInfo.versionCodeInt] 大于 0。
     *
     * @param serverUrl 更新接口地址
     * @return 更新信息或业务错误
     */
    public suspend fun check(serverUrl: String): Result<UpdateInfo> {
        return updateChecker.check(serverUrl)
    }

    /**
     * 下载 APK 到应用私有目录。
     *
     * 前置条件：[apkUrl] 为合法 HTTPS 下载地址。
     * 后置条件：
     * - 下载文件以 `.tmp` 写入，完成后原子 renameTo 为 `.apk`
     * - 返回 [Result.Success] 时，文件存在且可读
     *
     * 副作用：写入 `filesDir/apks/` 目录。
     *
     * @param apkUrl APK 下载地址
     * @param fileName 保存文件名（不含扩展名）
     * @return 保存后的 APK 文件或业务错误
     */
    public suspend fun downloadApk(
        apkUrl: String,
        fileName: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        val apkDir = File(context.filesDir, APK_DIR_NAME)
        if (!FileUtils.ensureDirectory(apkDir)) {
            return@withContext Result.Error(
                PureError.Storage("无法创建 APK 目录 | path=${apkDir.absolutePath}")
            )
        }

        val targetFile = File(apkDir, "$fileName.apk")
        val tmpFile = File(apkDir, "$fileName.apk.tmp")

        try {
            val request = Request.Builder()
                .url(apkUrl)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Error(
                        PureError.Server("下载失败 HTTP ${response.code} | $apkUrl")
                    )
                }

                val body = response.body
                    ?: return@withContext Result.Error(
                        PureError.Server("下载响应体为空 | $apkUrl")
                    )

                BufferedInputStream(body.byteStream()).use { inputStream ->
                    FileOutputStream(tmpFile).use { outputStream ->
                        inputStream.copyTo(outputStream, DOWNLOAD_BUFFER_SIZE_INT)
                        outputStream.flush()
                    }
                }
            }

            if (!tmpFile.renameTo(targetFile)) {
                PureLog.e(
                    "UpdateManager",
                    "downloadApk",
                    null,
                    "renameTo 失败 | tmp=${tmpFile.absolutePath}"
                )
                tmpFile.delete()
                return@withContext Result.Error(
                    PureError.Storage("APK 文件重命名失败")
                )
            }

            PureLog.i(
                "UpdateManager",
                "downloadApk",
                "成功 | file=${targetFile.absolutePath}"
            )
            Result.Success(targetFile)
        } catch (e: IOException) {
            PureLog.e("UpdateManager", "downloadApk", e, "下载异常 | apkUrl=$apkUrl")
            tmpFile.delete()
            Result.Error(PureError.Network("APK 下载异常: ${e.message}"))
        } catch (e: Exception) {
            PureLog.e("UpdateManager", "downloadApk", e, "未知异常 | apkUrl=$apkUrl")
            tmpFile.delete()
            Result.Error(PureError.Unknown("APK 下载未知异常: ${e.message}"))
        }
    }

    /**
     * 安装已下载的 APK。
     *
     * 前置条件：[apkFile] 存在且为合法 APK 文件；已持有 REQUEST_INSTALL_PACKAGES 权限。
     * 副作用：启动系统安装器。
     *
     * @param apkFile APK 文件
     * @return 安装 Intent 启动结果
     */
    public fun installApk(apkFile: File): Result<Unit> {
        if (!apkFile.exists()) {
            return Result.Error(PureError.Storage("APK 文件不存在 | ${apkFile.absolutePath}"))
        }

        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            PureLog.i("UpdateManager", "installApk", "已启动安装 | file=${apkFile.absolutePath}")
            Result.Success(Unit)
        } catch (e: Exception) {
            PureLog.e("UpdateManager", "installApk", e, "安装启动失败 | file=${apkFile.absolutePath}")
            Result.Error(PureError.Storage("安装启动失败: ${e.message}"))
        }
    }

    private companion object {
        private const val APK_DIR_NAME = "apks"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_BUFFER_SIZE_INT = 8 * 1024

        private val DEFAULT_OK_HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

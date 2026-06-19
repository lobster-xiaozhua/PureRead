package com.pureread.update

import com.pureread.core.log.PureLog
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 更新检查器接口。
 *
 * 职责：从服务端拉取最新版本信息并返回 [UpdateInfo]。
 *
 * 线程安全：实现类需保证并发安全。
 */
public interface UpdateChecker {

    /**
     * 向 [serverUrl] 发起检查请求。
     *
     * @param serverUrl 更新接口地址
     * @return 更新信息或业务错误
     */
    public suspend fun check(serverUrl: String): Result<UpdateInfo>
}

/**
 * 基于 OkHttp 的网络更新检查器。
 *
 * 职责：
 * - 使用 OkHttp 同步请求（在协程中调用）获取更新 JSON
 * - 解析 [UpdateInfo] 并校验必要字段
 *
 * 线程安全：[OkHttpClient] 实例可复用，线程安全。
 */
public class NetworkUpdateChecker(
    private val okHttpClient: OkHttpClient = DEFAULT_OK_HTTP_CLIENT,
) : UpdateChecker {

    /**
     * 检查更新。
     *
     * 前置条件：[serverUrl] 为合法 HTTPS URL。
     * 后置条件：返回 [UpdateInfo] 时，[UpdateInfo.apkUrl] 非空且 [UpdateInfo.versionCodeInt] 大于 0。
     */
    public override suspend fun check(serverUrl: String): Result<UpdateInfo> {
        val request = Request.Builder()
            .url(serverUrl)
            .get()
            .header("Accept", "application/json")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.Error(
                        PureError.Server("HTTP ${response.code} | $serverUrl")
                    )
                }
                val bodyString = response.body?.string().orEmpty()
                parseUpdateInfo(bodyString)
            }
        } catch (e: IOException) {
            PureLog.e("NetworkUpdateChecker", "check", e, "网络异常 | serverUrl=$serverUrl")
            Result.Error(PureError.Network("网络异常: ${e.message}"))
        } catch (e: Exception) {
            PureLog.e("NetworkUpdateChecker", "check", e, "解析异常 | serverUrl=$serverUrl")
            Result.Error(PureError.Server("解析异常: ${e.message}"))
        }
    }

    private fun parseUpdateInfo(jsonString: String): Result<UpdateInfo> {
        return try {
            val json = JSONObject(jsonString)
            val versionCodeInt = json.optInt(KEY_VERSION_CODE, 0)
            val versionName = json.optString(KEY_VERSION_NAME, "")
            val apkUrl = json.optString(KEY_APK_URL, "")
            val releaseNote = json.optString(KEY_RELEASE_NOTE, "")
            if (versionCodeInt <= 0 || apkUrl.isBlank()) {
                Result.Error(PureError.Server("更新 JSON 字段缺失或非法"))
            } else {
                Result.Success(
                    UpdateInfo(
                        versionCodeInt = versionCodeInt,
                        versionName = versionName,
                        apkUrl = apkUrl,
                        releaseNote = releaseNote,
                    )
                )
            }
        } catch (e: Exception) {
            PureLog.e("NetworkUpdateChecker", "parseUpdateInfo", e, "JSON 解析失败")
            Result.Error(PureError.Server("JSON 解析失败: ${e.message}"))
        }
    }

    private companion object {
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_VERSION_NAME = "versionName"
        private const val KEY_APK_URL = "apkUrl"
        private const val KEY_RELEASE_NOTE = "releaseNote"

        private val DEFAULT_OK_HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

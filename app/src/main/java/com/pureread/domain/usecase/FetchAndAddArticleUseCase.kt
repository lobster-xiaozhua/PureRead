package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.core.utils.UrlUtils
import com.pureread.data.local.dao.ArticleBodyDao
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.data.remote.api.ArticleExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException

/**
 * 后台抓取并添加文章用例。
 *
 * 职责：通过 URL 直接下载 HTML，提取正文后持久化到数据库。
 *
 * 线程安全：无状态，线程安全。
 */
public class FetchAndAddArticleUseCase(
    private val okHttpClient: OkHttpClient,
    private val articleExtractor: ArticleExtractor,
    private val articleDao: ArticleDao,
    private val articleBodyDao: ArticleBodyDao,
) {

    private companion object {
        private const val TAG = "FetchAndAddArticleUseCase"
        private const val MAX_BODY_LENGTH_BYTES = 10L * 1024 * 1024
    }

    /**
     * 下载并保存文章。
     *
     * @param urlString 文章 URL
     * @return 插入后的 articleId 或错误
     */
    public suspend operator fun invoke(urlString: String): Result<Long> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：urlString 非空且可解析
        // 后置条件：文章与正文已持久化
        // 副作用：发起网络请求并写入数据库
        val normalizedUrl = UrlUtils.normalizeUrl(urlString)
        if (normalizedUrl.isBlank()) {
            return@withContext Result.Error(PureError.Extract(messageString = "URL 无效"))
        }

        val existing = articleDao.getArticleByUrl(normalizedUrl)
        if (existing != null) {
            PureLog.i(TAG, "invoke", "文章已存在 | articleId=${existing.id}")
            return@withContext Result.Success(existing.id)
        }

        when (val htmlResult = fetchHtml(normalizedUrl, startTimeMs)) {
            is Result.Error -> htmlResult
            is Result.Success -> saveArticle(normalizedUrl, htmlResult.data, startTimeMs)
        }
    }

    private suspend fun fetchHtml(urlString: String, startTimeMs: Long): Result<String> {
        return try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                PureLog.w(
                    TAG,
                    "fetchHtml",
                    "HTTP 失败 | code=${response.code} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
                )
                return Result.Error(PureError.Network(messageString = "HTTP ${response.code}"))
            }
            val bodyString = response.body?.string() ?: ""
            if (bodyString.toByteArray(Charsets.UTF_8).size > MAX_BODY_LENGTH_BYTES) {
                return Result.Error(PureError.Network(messageString = "页面内容超过大小限制"))
            }
            PureLog.i(
                TAG,
                "fetchHtml",
                "下载成功 | length=${bodyString.length} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(bodyString)
        } catch (e: UnknownHostException) {
            PureLog.e(TAG, "fetchHtml", e, "DNS 解析失败 | url=$urlString")
            Result.Error(PureError.Network(throwable = e, messageString = "无法访问该网址"))
        } catch (e: IOException) {
            PureLog.e(TAG, "fetchHtml", e, "网络异常 | url=$urlString")
            Result.Error(PureError.Network(throwable = e, messageString = "网络请求失败"))
        } catch (e: Exception) {
            PureLog.e(TAG, "fetchHtml", e, "未知异常 | url=$urlString")
            Result.Error(PureError.Unknown(throwable = e, messageString = "抓取失败"))
        }
    }

    private suspend fun saveArticle(
        urlString: String,
        htmlString: String,
        startTimeMs: Long
    ): Result<Long> {
        return when (val extractResult = articleExtractor.extract(urlString, htmlString)) {
            is Result.Error -> {
                PureLog.w(
                    TAG,
                    "saveArticle",
                    "提取失败 | error=${extractResult.error} | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
                )
                extractResult
            }
            is Result.Success -> {
                persistArticle(extractResult.data, startTimeMs)
            }
        }
    }

    private suspend fun persistArticle(
        articleExtractResult: com.pureread.data.remote.api.ArticleExtractResult,
        startTimeMs: Long
    ): Result<Long> {
        return try {
            val nowMs = System.currentTimeMillis()
            val articleEntity = ArticleEntity(
                url = articleExtractResult.url,
                title = articleExtractResult.title,
                siteName = articleExtractResult.siteName,
                coverImageUrl = articleExtractResult.coverImageUrl,
                sourceUrl = articleExtractResult.url,
                wordCount = articleExtractResult.plainText.length,
                createTimeMs = nowMs,
                updateTimeMs = nowMs,
                extractTime = nowMs,
                status = ArticleEntity.STATUS_COMPLETED
            )
            val articleId = articleDao.insertArticle(articleEntity)
            val bodyEntity = ArticleBodyEntity(
                articleId = articleId,
                bodyHtml = articleExtractResult.bodyHtml,
                plainText = articleExtractResult.plainText,
                extractConfidenceFloat = 0f
            )
            articleBodyDao.insertArticleBody(bodyEntity)
            PureLog.i(
                TAG,
                "persistArticle",
                "保存成功 | articleId=$articleId | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Success(articleId)
        } catch (e: Exception) {
            PureLog.e(TAG, "persistArticle", e, "数据库保存失败")
            Result.Error(PureError.Storage(throwable = e, messageString = "保存文章失败"))
        }
    }
}

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

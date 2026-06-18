package com.pureread.data.remote.api

import com.pureread.core.log.PureLog
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL

/**
 * 文章提取器接口。
 *
 * 职责：定义从 HTML 提取标准化文章结果的契约。
 *
 * 线程安全：实现类需保证线程安全。
 */
public interface ArticleExtractor {

    /**
     * 提取文章。
     *
     * @param urlString 文章 URL
     * @param htmlString 原始 HTML
     * @return 提取结果
     */
    public suspend fun extract(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult>
}

/**
 * 站点适配器接口。
 *
 * 职责：针对特定站点实现定制化的文章提取逻辑。
 *
 * 线程安全：实现类需保证线程安全。
 */
public interface SiteAdapter {

    /**
     * 是否支持该 host。
     *
     * @param host URL host
     * @return 是否支持
     */
    public fun canHandle(host: String): Boolean

    /**
     * 提取文章。
     *
     * @param document Jsoup Document
     * @param urlString 文章 URL
     * @return 提取结果
     */
    public fun extract(
        document: Document,
        urlString: String
    ): Result<ArticleExtractResult>
}

/**
 * 站点适配器注册表。
 *
 * 职责：维护站点适配器列表并提供查找能力。
 *
 * 线程安全：无状态，线程安全。
 */
public object SiteAdapterRegistry {

    private val adapterList: List<SiteAdapter> = emptyList()

    /**
     * 查找支持该 URL 的站点适配器。
     *
     * @param urlString 文章 URL
     * @return 适配器或 null
     */
    public fun findAdapter(urlString: String): SiteAdapter? {
        val host = runCatching { URL(urlString).host }.getOrDefault("")
        return adapterList.firstOrNull { it.canHandle(host) }
    }
}

/**
 * 默认文章提取器。
 *
 * 职责：
 * - 第 1 级：JS Readability 提取
 * - 第 2 级：站点适配器
 * - 第 3 级：Jsoup 启发式
 * - 第 4 级：原始 HTML
 *
 * 线程安全：无状态，线程安全。
 */
public class DefaultArticleExtractor(
    private val readabilityJSBridge: ReadabilityJSBridge
) : ArticleExtractor {

    private companion object {
        private const val TAG = "DefaultArticleExtractor"
        private const val MIN_BODY_LENGTH = 200
        private const val MAX_PLAIN_TEXT_LENGTH = 5000
    }

    public override suspend fun extract(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult> = withContext(Dispatchers.Default) {
        val startTimeMs = System.currentTimeMillis()
        // 前置条件：urlString 非空且 htmlString 非空
        // 后置条件：返回非空 Result<ArticleExtractResult>
        // 副作用：调用 WebView JS 与 Jsoup 解析
        val jsResult = readabilityJSBridge.extractFromHtml(urlString, htmlString)
        if (isExtractValid(jsResult)) {
            PureLog.i(
                TAG,
                "extract",
                "Readability 提取成功 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            return@withContext jsResult
        }

        PureLog.w(
            TAG,
            "extract",
            "Readability 失败，进入站点适配 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
        )
        val siteResult = extractBySiteAdapter(urlString, htmlString)
        if (isExtractValid(siteResult)) {
            PureLog.i(
                TAG,
                "extract",
                "站点适配成功 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            return@withContext siteResult
        }

        PureLog.w(
            TAG,
            "extract",
            "站点适配失败，进入 Jsoup 启发式 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
        )
        val jsoupResult = extractByJsoupHeuristic(urlString, htmlString)
        if (isExtractValid(jsoupResult)) {
            PureLog.i(
                TAG,
                "extract",
                "Jsoup 启发式成功 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            return@withContext jsoupResult
        }

        PureLog.w(
            TAG,
            "extract",
            "Jsoup 启发式失败，使用原始 HTML | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
        )
        extractRawHtml(urlString, htmlString)
    }

    private fun isExtractValid(result: Result<ArticleExtractResult>): Boolean {
        if (result !is Result.Success) return false
        val data = result.data
        return data.bodyHtml.length >= MIN_BODY_LENGTH || data.title.isNotBlank()
    }

    private fun extractBySiteAdapter(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult> {
        return runCatching {
            val document = Jsoup.parse(htmlString, urlString)
            val adapter = SiteAdapterRegistry.findAdapter(urlString)
            if (adapter != null) {
                adapter.extract(document, urlString)
            } else {
                Result.Error(PureError.Extract(messageString = "无站点适配器"))
            }
        }.getOrElse { e ->
            PureLog.e(TAG, "extractBySiteAdapter", e, "站点适配失败")
            Result.Error(PureError.Fatal(e))
        }
    }

    private fun extractByJsoupHeuristic(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult> {
        return runCatching {
            val document = Jsoup.parse(htmlString, urlString)
            val title = resolveTitle(document)
            val bodyElement = resolveBodyElement(document)
            val bodyHtml = bodyElement?.html() ?: document.body().html()
            val plainText = bodyElement?.text() ?: document.body().text()
            val siteName = resolveSiteName(document, urlString)
            Result.Success(
                ArticleExtractResult(
                    title = title,
                    bodyHtml = bodyHtml,
                    plainText = plainText.take(MAX_PLAIN_TEXT_LENGTH),
                    siteName = siteName,
                    coverImageUrl = resolveCoverImage(bodyElement ?: document.body(), urlString),
                    url = urlString
                )
            )
        }.getOrElse { e ->
            PureLog.e(TAG, "extractByJsoupHeuristic", e, "Jsoup 启发式失败")
            Result.Error(PureError.Fatal(e))
        }
    }

    private fun extractRawHtml(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult> {
        val document = Jsoup.parse(htmlString, urlString)
        val title = resolveTitle(document)
        val body = document.body()
        val plainText = body.text().take(MAX_PLAIN_TEXT_LENGTH)
        return Result.Success(
            ArticleExtractResult(
                title = title,
                bodyHtml = body.html(),
                plainText = plainText,
                siteName = resolveSiteName(document, urlString),
                coverImageUrl = "",
                url = urlString
            )
        )
    }

    private fun resolveTitle(document: Document): String {
        return document.selectFirst("h1")?.text()
            ?: document.title()
            ?: ""
    }

    private fun resolveBodyElement(document: Document): Element? {
        val selectorList = listOf(
            "article",
            "[role='main']",
            "main",
            ".post-content",
            ".entry-content",
            ".article-content",
            ".content",
            "#content",
            ".post",
            ".entry"
        )
        return selectorList.firstNotNullOfOrNull { document.selectFirst(it) }
    }

    private fun resolveSiteName(document: Document, urlString: String): String {
        return document.selectFirst("meta[property='og:site_name']")?.attr("content")
            ?: document.selectFirst("meta[name='application-name']")?.attr("content")
            ?: extractSiteNameFromUrl(urlString)
    }

    private fun resolveCoverImage(element: Element, baseUrlString: String): String {
        val image = element.selectFirst("img[src]")
            ?: element.selectFirst("img[data-src]")
        return image?.let {
            val src = it.absUrl("src").ifBlank { it.absUrl("data-src") }
            src
        } ?: ""
    }

    private fun extractSiteNameFromUrl(urlString: String): String {
        return runCatching {
            URL(urlString).host.removePrefix("www.")
        }.getOrDefault("")
    }
}

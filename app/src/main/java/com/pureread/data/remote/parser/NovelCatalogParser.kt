package com.pureread.data.remote.parser

import com.pureread.core.log.PureLog
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 小说目录章节候选。
 *
 * @property title 章节标题
 * @property url 章节链接
 * @property index 章节索引
 */
public data class ChapterCandidate(
    public val title: String,
    public val url: String,
    public val index: Int
)

/**
 * 小说目录解析器。
 *
 * 职责：从 HTML 中解析出小说章节目录及下一页链接。
 *
 * 线程安全：无状态，线程安全。
 */
public class NovelCatalogParser {

    private companion object {
        private const val TAG = "NovelCatalogParser"
        private const val CHAPTER_TITLE_MIN_LENGTH = 1
        private const val CHAPTER_TITLE_MAX_LENGTH = 200
        private val CHAPTER_KEYWORD_LIST = listOf("章", "chapter", "回", "话", "节", "卷")
    }

    /**
     * 解析小说目录。
     *
     * @param htmlString 目录页 HTML
     * @param baseUrlString 目录页基础 URL，用于解析相对链接
     * @return 章节候选列表
     */
    public fun parseCatalog(
        htmlString: String,
        baseUrlString: String
    ): List<ChapterCandidate> {
        // 前置条件：htmlString 非空
        // 后置条件：返回按 index 排序的章节列表
        // 副作用：使用 Jsoup 解析 HTML
        return runCatching {
            val document = Jsoup.parse(htmlString, baseUrlString)
            val linkPairList = collectChapterLinks(document, baseUrlString)
            linkPairList
                .distinctBy { it.second }
                .mapIndexed { index, pair ->
                    ChapterCandidate(title = pair.first, url = pair.second, index = index)
                }
        }.getOrElse { e ->
            PureLog.e(TAG, "parseCatalog", e, "目录解析失败")
            emptyList()
        }
    }

    /**
     * 查找下一页链接。
     *
     * @param htmlString 目录页 HTML
     * @param baseUrlString 基础 URL
     * @return 下一页 URL，若不存在则返回空字符串
     */
    public fun findNextPageUrl(
        htmlString: String,
        baseUrlString: String
    ): String {
        return runCatching {
            val document = Jsoup.parse(htmlString, baseUrlString)
            resolveNextPageUrl(document, baseUrlString)
        }.getOrElse { e ->
            PureLog.e(TAG, "findNextPageUrl", e, "下一页解析失败")
            ""
        }
    }

    private fun collectChapterLinks(
        document: Document,
        baseUrlString: String
    ): List<Pair<String, String>> {
        val candidateList = mutableListOf<Pair<String, String>>()
        document.select("a[href]").forEach { element ->
            val title = element.text().trim()
            val href = element.absUrl("href")
            if (isChapterCandidate(title, href)) {
                candidateList.add(title to href)
            }
        }
        return candidateList
    }

    private fun isChapterCandidate(title: String, urlString: String): Boolean {
        if (title.length !in CHAPTER_TITLE_MIN_LENGTH..CHAPTER_TITLE_MAX_LENGTH) return false
        if (urlString.isBlank()) return false
        val lowerTitle = title.lowercase()
        val containsKeyword = CHAPTER_KEYWORD_LIST.any { lowerTitle.contains(it) }
            || title.contains(Regex("\\d+"))
        return containsKeyword
    }

    private fun resolveNextPageUrl(document: Document, baseUrlString: String): String {
        val selectorList = listOf(
            "a:contains(下一页)",
            "a:contains(下页)",
            "a:contains(Next)",
            "a:contains(next)",
            "a[rel='next']",
            "a.pagination-next"
        )
        for (selector in selectorList) {
            val element = document.selectFirst(selector)
            if (element != null) {
                val href = element.absUrl("href")
                if (href.isNotBlank()) return href
            }
        }
        return ""
    }
}

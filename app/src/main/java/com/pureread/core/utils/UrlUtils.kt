package com.pureread.core.utils

import java.net.MalformedURLException
import java.net.URL

/**
 * URL 处理工具单例。
 *
 * 职责：
 * - 规范化 URL（去片段、去默认端口、小写 scheme）
 * - 为聚类生成可比较的链接键
 * - 判断两个 URL 是否同站
 * - 将相对 URL 解析为绝对 URL
 *
 * 线程安全：无状态对象，可在任意线程调用。
 */
public object UrlUtils {

    private val TRACKING_PARAM_REGEX = Regex(
        "^[\\?&](utm_source|utm_medium|utm_campaign|utm_term|utm_content|fbclid|gclid|ref|spm|ns_key|ie|from)=",
        RegexOption.IGNORE_CASE
    )

    /**
     * 规范化 URL，用于持久化与去重。
     *
     * 前置条件：urlString 不为空。
     * 后置条件：返回去除片段、去除常见跟踪参数、小写 scheme 的 URL 字符串。
     * 副作用：无。
     *
     * @param urlString 原始 URL
     * @return 规范化后的 URL，若解析失败则原样返回并裁剪
     */
    public fun normalizeUrl(urlString: String): String {
        if (urlString.isBlank()) return urlString

        return try {
            val trimmedUrlString = urlString.trim()
            val url = URL(trimmedUrlString)
            val protocolString = url.protocol.lowercase()
            val hostString = url.host.lowercase()
            val portInt = normalizePortInt(url.port, protocolString)
            val portSuffixString = if (portInt == -1) "" else ":$portInt"
            val pathString = url.path.ifEmpty { "/" }
            val cleanQueryString = cleanQueryString(url.query)
            "$protocolString://$hostString$portSuffixString$pathString$cleanQueryString"
        } catch (e: MalformedURLException) {
            PureLog.e("UrlUtils", "normalizeUrl", e, "URL 解析失败 | url=$urlString")
            stripFragmentOnly(urlString)
        }
    }

    /**
     * 生成用于链接聚类的键（忽略查询参数与片段）。
     *
     * 前置条件：urlString 不为空。
     * 后置条件：返回仅包含 scheme、host、path 的字符串。
     * 副作用：无。
     *
     * @param urlString 原始 URL
     * @return 聚类键
     */
    public fun cleanLinkForCluster(urlString: String): String {
        if (urlString.isBlank()) return urlString

        return try {
            val url = URL(urlString.trim())
            val protocolString = url.protocol.lowercase()
            val hostString = url.host.lowercase()
            val pathString = url.path.ifEmpty { "/" }
            "$protocolString://$hostString$pathString"
        } catch (e: MalformedURLException) {
            PureLog.e("UrlUtils", "cleanLinkForCluster", e, "URL 解析失败 | url=$urlString")
            stripFragmentOnly(urlString)
        }
    }

    /**
     * 判断两个 URL 是否属于同一主机。
     *
     * 前置条件：两个参数均不为空。
     * 后置条件：返回是否同主机（忽略 scheme 与端口）。
     * 副作用：无。
     *
     * @param firstUrlString  第一个 URL
     * @param secondUrlString 第二个 URL
     * @return 同主机返回 true，否则 false
     */
    public fun isSameHost(firstUrlString: String, secondUrlString: String): Boolean {
        if (firstUrlString.isBlank() || secondUrlString.isBlank()) return false

        return try {
            val firstHostString = URL(firstUrlString.trim()).host.lowercase()
            val secondHostString = URL(secondUrlString.trim()).host.lowercase()
            firstHostString == secondHostString && firstHostString.isNotBlank()
        } catch (e: MalformedURLException) {
            PureLog.e("UrlUtils", "isSameHost", e, "URL 解析失败")
            false
        }
    }

    /**
     * 基于 base URL 解析相对 URL。
     *
     * 前置条件：baseUrlString 为合法绝对 URL，relativeUrlString 非空。
     * 后置条件：返回绝对 URL 字符串。
     * 副作用：无。
     *
     * @param baseUrlString     基础绝对 URL
     * @param relativeUrlString 相对 URL 或绝对 URL
     * @return 解析后的绝对 URL，若失败则返回 relativeUrlString 原值
     */
    public fun resolveUrl(baseUrlString: String, relativeUrlString: String): String {
        if (baseUrlString.isBlank()) return relativeUrlString.trim()
        if (relativeUrlString.isBlank()) return baseUrlString.trim()

        return try {
            val baseUrl = URL(baseUrlString.trim())
            val resolvedUrl = URL(baseUrl, relativeUrlString.trim())
            normalizeUrl(resolvedUrl.toString())
        } catch (e: MalformedURLException) {
            PureLog.e("UrlUtils", "resolveUrl", e, "解析失败 base=$baseUrlString relative=$relativeUrlString")
            relativeUrlString.trim()
        }
    }

    private fun normalizePortInt(portInt: Int, protocolString: String): Int {
        return when {
            portInt == -1 -> -1
            protocolString == "http" && portInt == 80 -> -1
            protocolString == "https" && portInt == 443 -> -1
            else -> portInt
        }
    }

    private fun cleanQueryString(queryString: String?): String {
        if (queryString.isNullOrBlank()) return ""
        val pairs = queryString.split("&")
        val keptPairs = pairs.filter { pairString ->
            !TRACKING_PARAM_REGEX.containsMatchIn("?$pairString")
        }
        return if (keptPairs.isEmpty()) "" else "?${keptPairs.joinToString("&")}"
    }

    private fun stripFragmentOnly(urlString: String): String {
        val fragmentIndexInt = urlString.indexOf('#')
        return if (fragmentIndexInt >= 0) urlString.substring(0, fragmentIndexInt) else urlString
    }
}

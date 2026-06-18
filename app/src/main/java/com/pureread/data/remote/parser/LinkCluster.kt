package com.pureread.data.remote.parser

import com.pureread.core.log.PureLog
import java.net.URI

/**
 * URL 链接聚类工具。
 *
 * 职责：根据 URL 相似性对链接进行分组，常用于小说目录章节识别。
 *
 * 线程安全：无状态，线程安全。
 */
public object LinkCluster {

    private const val TAG = "LinkCluster"
    private const val MIN_SEGMENTS_FOR_CLUSTER = 2

    /**
     * 清洗链接用于聚类。
     *
     * 职责：移除协议、www、查询参数与片段，保留 host 与 path。
     *
     * @param urlString 原始 URL
     * @return 清洗后的 URL 字符串
     */
    public fun cleanLinkForCluster(urlString: String): String {
        return runCatching {
            val uri = URI(urlString)
            val host = uri.host?.removePrefix("www.") ?: ""
            val path = uri.path ?: "/"
            "$host$path"
        }.getOrElse { e ->
            PureLog.e(TAG, "cleanLinkForCluster", e, "URL 清洗失败: $urlString")
            urlString
        }
    }

    /**
     * 对链接列表进行聚类。
     *
     * 职责：根据 URL 路径公共前缀将链接分组。
     *
     * @param urlList URL 列表
     * @return 聚类键到 URL 列表的映射
     */
    public fun clusterLinks(urlList: List<String>): Map<String, List<String>> {
        // 前置条件：urlList 非空
        // 后置条件：返回的 Map 包含输入中所有链接
        // 副作用：无
        return urlList.groupBy { extractClusterKey(it) }
    }

    private fun extractClusterKey(urlString: String): String {
        val cleanUrl = cleanLinkForCluster(urlString)
        val host = cleanUrl.substringBefore('/', "")
        val path = cleanUrl.substringAfter('/', defaultValue = "")
        val segmentList = path.split('/').filter { it.isNotBlank() }
        return if (segmentList.size >= MIN_SEGMENTS_FOR_CLUSTER && host.isNotBlank()) {
            "$host/${segmentList.take(segmentList.size - 1).joinToString(separator = "/")}"
        } else {
            cleanUrl
        }
    }
}

package com.pureread.data.remote.api

/**
 * 文章提取结果。
 *
 * 职责：封装 Readability.js / 站点适配 / Jsoup 启发式提取后的标准化文章数据。
 *
 * 线程安全：不可变数据类，线程安全。
 *
 * @property title 文章标题
 * @property bodyHtml 正文 HTML
 * @property plainText 纯文本摘要
 * @property siteName 站点名称
 * @property coverImageUrl 封面图 URL
 * @property url 原始 URL
 */
public data class ArticleExtractResult(
    public val title: String,
    public val bodyHtml: String,
    public val plainText: String,
    public val siteName: String,
    public val coverImageUrl: String,
    public val url: String
)

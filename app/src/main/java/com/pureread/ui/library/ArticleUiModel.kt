package com.pureread.ui.library

/**
 * 书架列表项 UI 模型。
 *
 * 职责：
 * - 隔离数据层 [com.pureread.data.local.entity.ArticleEntity]，仅暴露列表渲染所需字段。
 * - 所有数值字段均带单位/类型后缀，符合命名规范。
 */
public data class ArticleUiModel public constructor(
    public val idLong: Long,
    public val title: String,
    public val urlString: String,
    public val siteNameString: String,
    public val coverImageUrlString: String,
    public val readProgressPercentInt: Int,
    public val lastReadTimeMs: Long?,
    public val author: String?,
    public val wordCountInt: Int,
)

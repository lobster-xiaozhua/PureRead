package com.pureread.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 文章内容体表实体（详情查询使用，重量）。
 *
 * 职责：存储文章正文 HTML/Text，与 [ArticleEntity] 一一对应。
 *
 * 线程安全：不可变 Room 实体，线程安全。
 */
@Entity(
    tableName = "article_bodies",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["article_id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
public data class ArticleBodyEntity(
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    public val articleId: Long,

    @ColumnInfo(name = "body_html")
    public val bodyHtml: String = "",

    @ColumnInfo(name = "plain_text")
    public val plainText: String = "",

    @ColumnInfo(name = "extract_confidence_float")
    public val extractConfidenceFloat: Float = 0f,
)

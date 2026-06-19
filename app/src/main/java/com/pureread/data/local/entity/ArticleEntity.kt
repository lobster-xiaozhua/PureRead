package com.pureread.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文章主表实体（列表查询使用，轻量）。
 *
 * 职责：
 * - 存储文章元数据，与 [ArticleBodyEntity] 一一对应分离存储
 * - 同时兼容 URL 入口字段与阅读进度字段
 *
 * 线程安全：不可变 Room 实体，线程安全。
 */
@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["update_time_ms"]),
    ],
)
public data class ArticleEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "article_id")
    public val articleId: Long = 0L,

    @ColumnInfo(name = "url")
    public val url: String = "",

    @ColumnInfo(name = "title")
    public val title: String = "",

    @ColumnInfo(name = "site_name")
    public val siteName: String = "",

    @ColumnInfo(name = "cover_image_url")
    public val coverImageUrl: String = "",

    @ColumnInfo(name = "author")
    public val author: String? = null,

    @ColumnInfo(name = "source_url")
    public val sourceUrl: String = "",

    @ColumnInfo(name = "word_count_int")
    public val wordCount: Int = 0,

    @ColumnInfo(name = "read_progress_int")
    public val readProgress: Int = 0,

    @ColumnInfo(name = "last_read_time_ms")
    public val lastReadTimeMs: Long? = null,

    @ColumnInfo(name = "create_time_ms")
    public val createTimeMs: Long = 0L,

    @ColumnInfo(name = "update_time_ms")
    public val updateTimeMs: Long = 0L,

    @ColumnInfo(name = "extract_time_ms")
    public val extractTime: Long = 0L,

    @ColumnInfo(name = "status")
    public val status: String = STATUS_PENDING,

    @ColumnInfo(name = "extract_status")
    public val extractStatus: String = "SUCCESS",

    @ColumnInfo(name = "is_favorite")
    public val isFavorite: Boolean = false,
) {

    /**
     * 与 [articleId] 同义的兼容性属性（UI 层使用 [id]）。
     */
    @get:Ignore
    public val id: Long
        get() = articleId

    /**
     * 兼容 UI 层旧命名 [wordCountInt]。
     */
    @get:Ignore
    public val wordCountInt: Int
        get() = wordCount

    /**
     * 兼容 UI 层旧命名 [readProgressInt]。
     */
    @get:Ignore
    public val readProgressInt: Int
        get() = readProgress

    public companion object {
        public const val STATUS_PENDING: String = "PENDING"
        public const val STATUS_EXTRACTING: String = "EXTRACTING"
        public const val STATUS_COMPLETED: String = "COMPLETED"
        public const val STATUS_ERROR: String = "ERROR"
    }
}

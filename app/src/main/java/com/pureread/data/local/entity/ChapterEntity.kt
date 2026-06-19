package com.pureread.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 章节表实体。
 *
 * 职责：
 * - 保存章节元数据与本地文件路径
 * - 支持同一文章下多章节按 [indexInt] 排序
 *
 * 线程安全：不可变数据类，线程安全。
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["article_id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["article_id", "index_int"], unique = true),
        Index(value = ["article_id", "url"], unique = true)
    ]
)
public data class ChapterEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chapter_id")
    public val chapterId: Long = 0L,

    @ColumnInfo(name = "article_id")
    public val articleId: Long,

    @ColumnInfo(name = "url")
    public val url: String,

    @ColumnInfo(name = "title")
    public val title: String = "",

    @ColumnInfo(name = "index_int")
    public val indexInt: Int = 0,

    @ColumnInfo(name = "status")
    public val status: String = STATUS_PENDING,

    @ColumnInfo(name = "local_path")
    public val localPath: String = "",

    @ColumnInfo(name = "word_count_int")
    public val wordCountInt: Int = 0,

    @ColumnInfo(name = "update_time_ms")
    public val updateTimeMs: Long = System.currentTimeMillis()
) {

    public companion object {
        public const val STATUS_PENDING: String = "PENDING"
        public const val STATUS_DOWNLOADING: String = "DOWNLOADING"
        public const val STATUS_COMPLETED: String = "COMPLETED"
        public const val STATUS_ERROR: String = "ERROR"
    }
}

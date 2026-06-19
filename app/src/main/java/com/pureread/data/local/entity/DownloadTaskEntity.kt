package com.pureread.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 下载任务表实体。
 *
 * 职责：
 * - 记录文章或章节的批量下载任务进度
 * - 通过 [articleId] 与 [ArticleEntity] 关联
 *
 * 线程安全：不可变数据类，线程安全。
 */
@Entity(
    tableName = "download_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["article_id"],
            childColumns = ["article_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["article_id"]),
        Index(value = ["status"])
    ]
)
public data class DownloadTaskEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "task_id")
    public val taskId: Long = 0L,

    @ColumnInfo(name = "article_id")
    public val articleId: Long,

    @ColumnInfo(name = "type")
    public val type: String = TYPE_ARTICLE,

    @ColumnInfo(name = "status")
    public val status: String = STATUS_PENDING,

    @ColumnInfo(name = "total_count_int")
    public val totalCountInt: Int = 0,

    @ColumnInfo(name = "completed_count_int")
    public val completedCountInt: Int = 0,

    @ColumnInfo(name = "created_at_ms")
    public val createdAtMs: Long = System.currentTimeMillis()
) {

    public companion object {
        public const val TYPE_ARTICLE: String = "ARTICLE"
        public const val TYPE_CHAPTER: String = "CHAPTER"

        public const val STATUS_PENDING: String = "PENDING"
        public const val STATUS_RUNNING: String = "RUNNING"
        public const val STATUS_PAUSED: String = "PAUSED"
        public const val STATUS_COMPLETED: String = "COMPLETED"
        public const val STATUS_ERROR: String = "ERROR"
    }
}

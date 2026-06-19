package com.pureread.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pureread.data.local.dao.ArticleBodyDao
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.dao.ChapterDao
import com.pureread.data.local.dao.DownloadTaskDao
import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity

/**
 * PureRead Room 数据库。
 *
 * 职责：
 * - 聚合文章、正文、章节、下载任务四张表
 * - 暴露各表 DAO 供 Repository 使用
 *
 * 线程安全：Room 保证单例访问，线程安全。
 */
@Database(
    entities = [
        ArticleEntity::class,
        ArticleBodyEntity::class,
        ChapterEntity::class,
        DownloadTaskEntity::class
    ],
    version = DATABASE_VERSION_INT,
    exportSchema = false
)
public abstract class PureReadDatabase : RoomDatabase() {

    /**
     * 文章主表 DAO。
     */
    public abstract fun articleDao(): ArticleDao

    /**
     * 文章正文表 DAO。
     */
    public abstract fun articleBodyDao(): ArticleBodyDao

    /**
     * 章节表 DAO。
     */
    public abstract fun chapterDao(): ChapterDao

    /**
     * 下载任务表 DAO。
     */
    public abstract fun downloadTaskDao(): DownloadTaskDao

    public companion object {
        public const val DATABASE_VERSION_INT: Int = 1
        public const val DATABASE_NAME: String = "pureread.db"
    }
}

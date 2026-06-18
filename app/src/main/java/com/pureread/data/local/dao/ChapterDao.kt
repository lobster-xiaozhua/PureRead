package com.pureread.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pureread.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/**
 * 章节表数据访问对象。
 *
 * 职责：
 * - 提供章节的增删改查
 * - 按文章 ID 观察章节列表
 * - 更新章节状态与本地路径
 *
 * 线程安全：Room 自动调度到后台线程，Flow 在协程中发射。
 */
@Dao
public interface ChapterDao {

    /**
     * 插入或替换章节。
     *
     * @param chapter 章节实体
     * @return 新增行的自增主键
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertChapter(chapter: ChapterEntity): Long

    /**
     * 批量插入或替换章节。
     *
     * @param chapterList 章节实体列表
     * @return 新增行的自增主键列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertChapters(chapterList: List<ChapterEntity>): List<Long>

    /**
     * 更新章节。
     *
     * @param chapter 章节实体
     * @return 受影响的行数
     */
    @Update
    public suspend fun updateChapter(chapter: ChapterEntity): Int

    /**
     * 删除章节。
     *
     * @param chapter 章节实体
     * @return 受影响的行数
     */
    @Delete
    public suspend fun deleteChapter(chapter: ChapterEntity): Int

    /**
     * 按文章 ID 删除全部章节。
     *
     * @param articleId 文章主键
     * @return 受影响的行数
     */
    @Query("DELETE FROM chapters WHERE article_id = :articleId")
    public suspend fun deleteChaptersByArticleId(articleId: Long): Int

    /**
     * 按文章 ID 观察章节列表，按序号升序。
     *
     * @param articleId 文章主键
     * @return 章节列表的 Flow
     */
    @Query("SELECT * FROM chapters WHERE article_id = :articleId ORDER BY index_int ASC")
    public fun observeChaptersByArticleId(articleId: Long): Flow<List<ChapterEntity>>

    /**
     * 按文章 ID 与序号查询章节。
     *
     * @param articleId 文章主键
     * @param indexInt  章节序号
     * @return 章节实体，不存在则为 null
     */
    @Query("SELECT * FROM chapters WHERE article_id = :articleId AND index_int = :indexInt LIMIT 1")
    public suspend fun getChapterByIndex(articleId: Long, indexInt: Int): ChapterEntity?

    /**
     * 按文章 ID 查询全部章节。
     *
     * @param articleId 文章主键
     * @return 章节列表
     */
    @Query("SELECT * FROM chapters WHERE article_id = :articleId ORDER BY index_int ASC")
    public suspend fun getChaptersByArticleId(articleId: Long): List<ChapterEntity>

    /**
     * 更新章节状态与本地路径。
     *
     * @param chapterId      章节主键
     * @param status         新状态
     * @param localPath      本地文件路径
     * @param updateTimeMs   更新时间戳
     * @return 受影响的行数
     */
    @Query(
        """
        UPDATE chapters 
        SET status = :status, 
            local_path = :localPath, 
            update_time_ms = :updateTimeMs 
        WHERE chapter_id = :chapterId
        """
    )
    public suspend fun updateStatusAndPath(
        chapterId: Long,
        status: String,
        localPath: String,
        updateTimeMs: Long
    ): Int
}

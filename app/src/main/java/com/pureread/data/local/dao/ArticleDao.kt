package com.pureread.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pureread.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 文章主表数据访问对象。
 *
 * 职责：提供文章元数据的增删改查与 Flow 订阅。
 *
 * 线程安全：Room 自动处理线程切换，挂起函数可在 IO 线程安全调用。
 */
@Dao
public interface ArticleDao {

    /**
     * 插入文章元数据。
     *
     * @param article 文章实体
     * @return 生成的自增主键
     */
    @Insert
    public suspend fun insertArticle(article: ArticleEntity): Long

    /**
     * 更新文章元数据。
     *
     * @param article 文章实体
     */
    @Update
    public suspend fun updateArticle(article: ArticleEntity): Unit

    /**
     * 删除文章元数据。
     *
     * @param article 文章实体
     */
    @Delete
    public suspend fun deleteArticle(article: ArticleEntity): Unit

    /**
     * 查询所有文章，按更新时间倒序。
     *
     * @return 文章列表的 [Flow]
     */
    @Query("SELECT * FROM articles ORDER BY update_time_ms DESC")
    public fun observeArticles(): Flow<List<ArticleEntity>>

    /**
     * 根据 ID 查询文章。
     *
     * @param articleId 文章 ID
     * @return 文章实体或 null
     */
    @Query("SELECT * FROM articles WHERE article_id = :articleId LIMIT 1")
    public suspend fun getArticleById(articleId: Long): ArticleEntity?

    /**
     * 根据 URL 查询文章。
     *
     * @param urlString 文章 URL
     * @return 文章实体或 null
     */
    @Query("SELECT * FROM articles WHERE url = :urlString LIMIT 1")
    public suspend fun getArticleByUrl(urlString: String): ArticleEntity?

    /**
     * 搜索文章（标题、站点名、URL 模糊匹配）。
     *
     * @param queryString 搜索关键词
     * @return 文章列表的 [Flow]
     */
    @Query(
        """
        SELECT * FROM articles 
        WHERE title LIKE '%' || :queryString || '%' 
           OR site_name LIKE '%' || :queryString || '%' 
           OR url LIKE '%' || :queryString || '%'
        ORDER BY update_time_ms DESC
        """
    )
    public fun searchArticles(queryString: String): Flow<List<ArticleEntity>>

    /**
     * 更新阅读进度。
     *
     * @param articleId 文章 ID
     * @param readProgressPercentInt 阅读进度百分比
     * @param lastReadTimeMs 最后阅读时间戳
     * @return 受影响的行数
     */
    @Query(
        """
        UPDATE articles 
        SET read_progress_int = :readProgressPercentInt, 
            last_read_time_ms = :lastReadTimeMs,
            update_time_ms = :lastReadTimeMs
        WHERE article_id = :articleId
        """
    )
    public suspend fun updateReadProgress(
        articleId: Long,
        readProgressPercentInt: Int,
        lastReadTimeMs: Long
    ): Int

    /**
     * 按主键更新文章指定字段。
     *
     * @param articleId 文章 ID
     * @param title 标题
     * @param siteName 站点名
     * @param coverImageUrl 封面图 URL
     * @param updatedAtMs 更新时间戳
     * @param status 状态
     * @return 受影响的行数
     */
    @Query(
        """
        UPDATE articles 
        SET title = :title,
            site_name = :siteName,
            cover_image_url = :coverImageUrl,
            update_time_ms = :updatedAtMs,
            status = :status
        WHERE article_id = :articleId
        """
    )
    public suspend fun updateArticle(
        articleId: Long,
        title: String,
        siteName: String,
        coverImageUrl: String,
        updatedAtMs: Long,
        status: String
    ): Int
}

package com.pureread.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pureread.data.local.entity.ArticleBodyEntity

/**
 * 文章内容体表数据访问对象。
 *
 * 职责：提供文章正文的插入、查询与按 articleId 删除。
 *
 * 线程安全：Room 自动处理线程切换，挂起函数可在 IO 线程安全调用。
 */
@Dao
public interface ArticleBodyDao {

    /**
     * 插入或替换文章正文。
     *
     * @param body 正文实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertArticleBody(body: ArticleBodyEntity): Unit

    /**
     * 根据文章 ID 查询正文。
     *
     * @param articleId 文章 ID
     * @return 正文实体或 null
     */
    @Query("SELECT * FROM article_bodies WHERE article_id = :articleId LIMIT 1")
    public suspend fun getArticleBodyById(articleId: Long): ArticleBodyEntity?

    /**
     * 根据文章 ID 删除正文。
     *
     * @param articleId 文章 ID
     * @return 受影响的行数
     */
    @Query("DELETE FROM article_bodies WHERE article_id = :articleId")
    public suspend fun deleteBodyByArticleId(articleId: Long): Int
}

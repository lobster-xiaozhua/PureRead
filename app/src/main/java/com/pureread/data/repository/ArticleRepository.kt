package com.pureread.data.repository

import com.pureread.data.local.entity.ArticleBodyEntity
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.local.entity.ChapterEntity
import com.pureread.data.local.entity.DownloadTaskEntity
import com.pureread.data.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * 文章仓库接口。
 *
 * 职责：
 * - 定义文章本地数据源的访问契约
 * - 统一返回 [Result] 与 [Flow]，便于 UI 层处理加载/错误状态
 * - 同时暴露低阶 CRUD 方法供 UseCase 直接操作
 *
 * 线程安全：实现类负责在协程中执行数据库操作。
 */
public interface ArticleRepository {

    /**
     * 添加一篇文章（仅插入元数据，状态为待提取）。
     *
     * @param urlString 文章 URL
     * @return 新增文章主键或错误
     */
    public suspend fun addArticle(urlString: String): Result<Long>

    /**
     * 观察全部文章列表。
     *
     * @return 文章列表结果流
     */
    public fun getArticles(): Flow<Result<List<ArticleEntity>>>

    /**
     * 按主键查询单篇文章。
     *
     * @param articleId 文章主键
     * @return 文章实体或错误
     */
    public suspend fun getArticle(articleId: Long): Result<ArticleEntity>

    /**
     * 删除文章及其关联数据。
     *
     * @param articleId 文章主键
     * @return 成功或错误
     */
    public suspend fun deleteArticle(articleId: Long): Result<Unit>

    /**
     * 搜索文章（标题或摘要匹配）。
     *
     * @param queryString 搜索关键词
     * @return 匹配文章列表结果流
     */
    public fun searchArticles(queryString: String): Flow<Result<List<ArticleEntity>>>

    /**
     * 更新阅读进度。
     *
     * @param articleId              文章主键
     * @param readProgressPercentInt 阅读进度百分比（0-100）
     * @return 成功或错误
     */
    public suspend fun updateReadProgress(
        articleId: Long,
        readProgressPercentInt: Int
    ): Result<Unit>

    /**
     * 刷新文章（触发重新提取/同步）。
     *
     * @param articleId 文章主键
     * @return 成功或错误
     */
    public suspend fun refreshArticle(articleId: Long): Result<Unit>

    // region 低阶 CRUD（供现有 UseCase 直接使用）

    /**
     * 插入文章主表记录。
     *
     * @param article 文章实体
     * @return 新增行主键
     */
    public suspend fun insertArticle(article: ArticleEntity): Long

    /**
     * 插入或替换文章正文。
     *
     * @param body 正文实体
     */
    public suspend fun insertArticleBody(body: ArticleBodyEntity): Unit

    /**
     * 观察全部文章列表（原始 Flow）。
     *
     * @return 文章列表 Flow
     */
    public fun observeArticles(): Flow<List<ArticleEntity>>

    /**
     * 按主键查询文章，不存在返回 null。
     *
     * @param articleId 文章主键
     * @return 文章实体或 null
     */
    public suspend fun getArticleById(articleId: Long): ArticleEntity?

    /**
     * 按文章 ID 查询正文，不存在返回 null。
     *
     * @param articleId 文章主键
     * @return 正文实体或 null
     */
    public suspend fun getArticleBodyById(articleId: Long): ArticleBodyEntity?

    /**
     * 更新文章主表指定字段。
     *
     * @param articleId      文章主键
     * @param title          标题
     * @param siteName       站点名
     * @param coverImageUrl  封面图 URL
     * @param updatedAtMs    更新时间戳
     * @param status         状态
     */
    public suspend fun updateArticle(
        articleId: Long,
        title: String,
        siteName: String,
        coverImageUrl: String,
        updatedAtMs: Long,
        status: String
    ): Unit

    /**
     * 更新文章正文。
     *
     * @param articleId  文章主键
     * @param bodyHtml   正文 HTML
     * @param plainText  纯文本
     */
    public suspend fun updateArticleBody(
        articleId: Long,
        bodyHtml: String,
        plainText: String
    ): Unit

    /**
     * 按文章 ID 查询章节列表。
     *
     * @param articleId 文章主键
     * @return 章节列表
     */
    public suspend fun getChaptersByArticleId(articleId: Long): List<ChapterEntity>

    /**
     * 插入章节。
     *
     * @param chapter 章节实体
     * @return 新增行主键
     */
    public suspend fun insertChapter(chapter: ChapterEntity): Long

    /**
     * 插入下载任务。
     *
     * @param task 任务实体
     */
    public suspend fun insertDownloadTask(task: DownloadTaskEntity): Unit

    // endregion
}

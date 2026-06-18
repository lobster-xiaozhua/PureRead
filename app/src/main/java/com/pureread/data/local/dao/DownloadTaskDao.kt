package com.pureread.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pureread.data.local.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * 下载任务表数据访问对象。
 *
 * 职责：
 * - 提供下载任务的增删改查
 * - 按文章 ID 或全局观察任务状态变化
 *
 * 线程安全：Room 自动调度到后台线程，Flow 在协程中发射。
 */
@Dao
public interface DownloadTaskDao {

    /**
     * 插入或替换下载任务。
     *
     * @param task 任务实体
     * @return 新增行的自增主键
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertTask(task: DownloadTaskEntity): Long

    /**
     * 插入下载任务（兼容命名）。
     *
     * @param task 任务实体
     * @return 新增行的自增主键
     */
    public suspend fun insertDownloadTask(task: DownloadTaskEntity): Long = insertTask(task)

    /**
     * 更新下载任务。
     *
     * @param task 任务实体
     * @return 受影响的行数
     */
    @Update
    public suspend fun updateTask(task: DownloadTaskEntity): Int

    /**
     * 删除下载任务。
     *
     * @param task 任务实体
     * @return 受影响的行数
     */
    @Delete
    public suspend fun deleteTask(task: DownloadTaskEntity): Int

    /**
     * 按主键查询任务。
     *
     * @param taskId 任务主键
     * @return 任务实体，不存在则为 null
     */
    @Query("SELECT * FROM download_tasks WHERE task_id = :taskId")
    public suspend fun getTaskById(taskId: Long): DownloadTaskEntity?

    /**
     * 按文章 ID 观察任务列表。
     *
     * @param articleId 文章主键
     * @return 任务列表的 Flow
     */
    @Query("SELECT * FROM download_tasks WHERE article_id = :articleId ORDER BY created_at_ms DESC")
    public fun observeTasksByArticleId(articleId: Long): Flow<List<DownloadTaskEntity>>

    /**
     * 观察全部任务。
     *
     * @return 任务列表的 Flow
     */
    @Query("SELECT * FROM download_tasks ORDER BY created_at_ms DESC")
    public fun observeAllTasks(): Flow<List<DownloadTaskEntity>>
}

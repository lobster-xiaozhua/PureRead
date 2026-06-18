package com.pureread.domain.usecase

import com.pureread.core.log.PureLog
import com.pureread.data.local.dao.ArticleDao
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 获取文章列表用例。
 *
 * 职责：观察文章主表数据流并包装为 [Result]。
 *
 * 线程安全：Flow 在调用者协程中收集。
 */
public class GetArticlesUseCase(
    private val articleDao: ArticleDao
) {

    private companion object {
        private const val TAG = "GetArticlesUseCase"
    }

    /**
     * 获取文章列表数据流。
     *
     * @return 文章列表结果 Flow
     */
    public operator fun invoke(): Flow<Result<List<ArticleEntity>>> {
        return articleDao.observeArticles()
            .map { articleList -> Result.Success(articleList) }
            .catch { throwable ->
                PureLog.e(TAG, "invoke", throwable, "观察文章列表失败")
                emit(Result.Error(PureError.Storage(throwable = throwable, messageString = "观察文章列表失败")))
            }
    }
}

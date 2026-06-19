package com.pureread.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureread.core.log.PureLog
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.Result
import com.pureread.domain.usecase.DeleteArticleUseCase
import com.pureread.domain.usecase.FetchAndAddArticleUseCase
import com.pureread.domain.usecase.GetArticlesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 书架页面 ViewModel。
 *
 * 职责：
 * - 通过 [GetArticlesUseCase] 观察文章列表并响应搜索关键字过滤。
 * - 通过 [DeleteArticleUseCase] 执行批量删除。
 *
 * 线程安全：所有公开 Flow 均在 viewModelScope 内生产，UI 在主线程消费。
 */
public class LibraryViewModel public constructor(
    private val getArticlesUseCase: GetArticlesUseCase,
    private val deleteArticleUseCase: DeleteArticleUseCase,
    private val fetchAndAddArticleUseCase: FetchAndAddArticleUseCase,
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val _isLoadingFlow = MutableStateFlow(false)
    private val _fetchResultFlow = MutableStateFlow<Result<Long>?>(null)

    /**
     * 当前是否处于刷新/加载状态。
     */
    public val isLoadingFlow: StateFlow<Boolean> = _isLoadingFlow.asStateFlow()

    /**
     * 最近一次后台抓取文章结果。
     */
    public val fetchResultFlow: StateFlow<Result<Long>?> = _fetchResultFlow.asStateFlow()

    /**
     * 搜索过滤后的文章列表流。
     */
    public val articlesUiFlow: StateFlow<List<ArticleUiModel>> = searchQueryFlow
        .flatMapLatest { query ->
            getArticlesUseCase().map { result -> Pair(result, query) }
        }
        .onStart { _isLoadingFlow.value = true }
        .onEach { _isLoadingFlow.value = false }
        .map { (result, query) -> mapResultToUi(result, query) }
        .catch { throwable ->
            PureLog.e(TAG, "articlesUiFlow", throwable, "列表流异常")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    /**
     * 设置搜索关键字。
     *
     * @param query 用户输入的关键字
     */
    public fun search(query: String): Unit {
        searchQueryFlow.value = query
    }

    /**
     * 触发下拉刷新指示器。
     *
     * 副作用：短暂置位 [_isLoadingFlow]，依赖 Room Flow 自动重发最新数据。
     */
    public fun refresh(): Unit {
        viewModelScope.launch {
            _isLoadingFlow.value = true
            delay(REFRESH_INDICATOR_DELAY_MS)
            _isLoadingFlow.value = false
        }
    }

    /**
     * 后台抓取 URL 并加入书架。
     *
     * @param urlString 文章 URL
     */
    public fun fetchArticle(urlString: String): Unit {
        viewModelScope.launch {
            _isLoadingFlow.value = true
            try {
                _fetchResultFlow.value = fetchAndAddArticleUseCase(urlString)
            } catch (e: Exception) {
                PureLog.e(TAG, "fetchArticle", e, "抓取异常")
                _fetchResultFlow.value = Result.Error(
                    com.pureread.data.model.PureError.Unknown(throwable = e)
                )
            } finally {
                _isLoadingFlow.value = false
            }
        }
    }

    /**
     * 消费最近一次抓取结果，避免重复提示。
     */
    public fun consumeFetchResult(): Unit {
        _fetchResultFlow.value = null
    }

    /**
     * 删除选中的文章。
     *
     * @param articleIds 待删除的文章 ID 集合
     */
    public fun deleteSelected(articleIds: Set<Long>): Unit {
        if (articleIds.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _isLoadingFlow.value = true
            try {
                when (val result = deleteArticleUseCase(articleIds.toList())) {
                    is Result.Success -> {
                        PureLog.i(TAG, "deleteSelected", "删除 ${articleIds.size} 篇文章 | 成功")
                    }

                    is Result.Error -> {
                        PureLog.e(TAG, "deleteSelected", "删除失败 | error=${result.error}")
                    }
                }
            } catch (e: Exception) {
                PureLog.e(TAG, "deleteSelected", e, "删除异常")
            } finally {
                _isLoadingFlow.value = false
            }
        }
    }

    private fun mapResultToUi(
        result: Result<List<ArticleEntity>>,
        query: String,
    ): List<ArticleUiModel> = when (result) {
        is Result.Success -> {
            result.data
                .filter { entity -> matchesQuery(entity, query) }
                .map { entity -> entity.toUiModel() }
        }

        is Result.Error -> {
            PureLog.e(TAG, "mapResultToUi", "获取文章列表失败 | error=${result.error}")
            emptyList()
        }
    }

    private fun matchesQuery(entity: ArticleEntity, query: String): Boolean {
        if (query.isBlank()) {
            return true
        }
        val normalizedQuery = query.trim().lowercase()
        return entity.title.contains(normalizedQuery, ignoreCase = true) ||
            entity.siteName.contains(normalizedQuery, ignoreCase = true) ||
            entity.url.contains(normalizedQuery, ignoreCase = true)
    }

    private fun ArticleEntity.toUiModel(): ArticleUiModel = ArticleUiModel(
        idLong = articleId,
        title = title,
        urlString = url,
        siteNameString = siteName,
        coverImageUrlString = coverImageUrl,
        readProgressPercentInt = readProgressInt,
        lastReadTimeMs = lastReadTimeMs,
        author = author,
        wordCountInt = wordCountInt,
    )

    private companion object {
        private const val TAG = "LibraryViewModel"
        private const val STOP_TIMEOUT_MS = 5000L
        private const val REFRESH_INDICATOR_DELAY_MS = 300L
    }
}

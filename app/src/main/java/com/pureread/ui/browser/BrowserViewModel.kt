package com.pureread.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureread.core.log.PureLog
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.data.remote.api.ArticleExtractResult
import com.pureread.domain.usecase.AddArticleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 浏览器页面 ViewModel。
 *
 * 职责：
 * - 维护当前 URL 状态。
 * - 通过 [AddArticleUseCase] 请求正文提取并加入书架。
 *
 * 线程安全：状态流在主线程发射，UseCase 调用在 viewModelScope 内执行。
 */
public class BrowserViewModel public constructor(
    private val addArticleUseCase: AddArticleUseCase,
) : ViewModel() {

    private val _currentUrlFlow = MutableStateFlow("")

    /**
     * 当前浏览器地址栏 URL。
     */
    public val currentUrlFlow: StateFlow<String> = _currentUrlFlow.asStateFlow()

    private val _extractResultFlow = MutableStateFlow<Result<ArticleExtractResult>?>(null)

    /**
     * 最近一次提取结果。
     */
    public val extractResultFlow: StateFlow<Result<ArticleExtractResult>?> = _extractResultFlow.asStateFlow()

    private val _isExtractingFlow = MutableStateFlow(false)

    /**
     * 是否正在提取正文。
     */
    public val isExtractingFlow: StateFlow<Boolean> = _isExtractingFlow.asStateFlow()

    /**
     * 更新当前 URL。
     *
     * @param url 目标 URL
     */
    public fun onUrlChanged(url: String): Unit {
        _currentUrlFlow.value = url
    }

    /**
     * 将当前网页加入书架（触发远程正文提取）。
     *
     * @param sourceUrl 待提取的源 URL
     * @param htmlString 当前页原始 HTML
     */
    public fun addToLibrary(sourceUrl: String, htmlString: String): Unit {
        viewModelScope.launch {
            _isExtractingFlow.value = true
            try {
                _extractResultFlow.value = addArticleUseCase(sourceUrl, htmlString)
            } catch (e: Exception) {
                PureLog.e(TAG, "addToLibrary", e, "提取异常")
                _extractResultFlow.value = Result.Error(PureError.Unknown(throwable = e))
            } finally {
                _isExtractingFlow.value = false
            }
        }
    }

    /**
     * 消费最近一次提取结果，避免重复显示。
     */
    public fun consumeExtractResult(): Unit {
        _extractResultFlow.value = null
    }

    private companion object {
        private const val TAG = "BrowserViewModel"
    }
}

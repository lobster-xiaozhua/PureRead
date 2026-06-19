package com.pureread.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureread.core.log.PureLog
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.domain.usecase.AddArticleUseCase
import com.pureread.domain.usecase.CreateNovelArticleUseCase
import com.pureread.domain.usecase.DownloadNovelUseCase
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
    private val createNovelArticleUseCase: CreateNovelArticleUseCase,
    private val downloadNovelUseCase: DownloadNovelUseCase,
) : ViewModel() {

    private val _currentUrlFlow = MutableStateFlow("")

    /**
     * 当前浏览器地址栏 URL。
     */
    public val currentUrlFlow: StateFlow<String> = _currentUrlFlow.asStateFlow()

    private val _extractResultFlow = MutableStateFlow<Result<Long>?>(null)

    /**
     * 最近一次提取结果（成功为文章 ID，失败为错误）。
     */
    public val extractResultFlow: StateFlow<Result<Long>?> = _extractResultFlow.asStateFlow()

    private val _isExtractingFlow = MutableStateFlow(false)

    /**
     * 是否正在提取正文。
     */
    public val isExtractingFlow: StateFlow<Boolean> = _isExtractingFlow.asStateFlow()

    private val _novelDownloadResultFlow = MutableStateFlow<Result<Unit>?>(null)

    /**
     * 最近一次小说下载入队结果。
     */
    public val novelDownloadResultFlow: StateFlow<Result<Unit>?> = _novelDownloadResultFlow.asStateFlow()

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

    /**
     * 将当前目录页作为小说下载源入队。
     *
     * @param sourceUrl 目录页 URL
     * @param htmlString 目录页 HTML
     * @param titleString 小说标题
     */
    public fun downloadNovel(sourceUrl: String, htmlString: String, titleString: String): Unit {
        viewModelScope.launch {
            _isExtractingFlow.value = true
            try {
                val createResult = createNovelArticleUseCase(sourceUrl, titleString)
                _novelDownloadResultFlow.value = when (createResult) {
                    is Result.Error -> createResult
                    is Result.Success -> {
                        downloadNovelUseCase(createResult.data, htmlString, sourceUrl)
                    }
                }
            } catch (e: Exception) {
                PureLog.e(TAG, "downloadNovel", e, "小说下载入队异常")
                _novelDownloadResultFlow.value = Result.Error(PureError.Unknown(throwable = e))
            } finally {
                _isExtractingFlow.value = false
            }
        }
    }

    /**
     * 消费最近一次小说下载结果。
     */
    public fun consumeNovelDownloadResult(): Unit {
        _novelDownloadResultFlow.value = null
    }

    private companion object {
        private const val TAG = "BrowserViewModel"
    }
}

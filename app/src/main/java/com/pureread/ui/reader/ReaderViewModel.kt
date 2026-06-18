package com.pureread.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pureread.core.log.PureLog
import com.pureread.data.local.entity.ArticleEntity
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import com.pureread.domain.usecase.GetArticleByIdUseCase
import com.pureread.domain.usecase.SaveArticleProgressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读器页面 ViewModel。
 *
 * 职责：
 * - 通过 [GetArticleByIdUseCase] 加载文章元数据与正文。
 * - 通过 [SaveArticleProgressUseCase] 保存阅读进度。
 * - 维护阅读主题与字号状态。
 *
 * 线程安全：状态流在主线程发射，UseCase 调用在 viewModelScope 内执行。
 */
public class ReaderViewModel public constructor(
    private val getArticleByIdUseCase: GetArticleByIdUseCase,
    private val saveArticleProgressUseCase: SaveArticleProgressUseCase,
) : ViewModel() {

    private val _articleResultFlow = MutableStateFlow<Result<Pair<ArticleEntity, String>>?>(null)

    /**
     * 文章与正文加载结果。
     */
    public val articleResultFlow: StateFlow<Result<Pair<ArticleEntity, String>>?> =
        _articleResultFlow.asStateFlow()

    private val _readerThemeFlow = MutableStateFlow(ReaderTheme.DAY)

    /**
     * 当前阅读主题。
     */
    public val readerThemeFlow: StateFlow<ReaderTheme> = _readerThemeFlow.asStateFlow()

    private val _fontSizeSpFlow = MutableStateFlow(DEFAULT_FONT_SIZE_SP)

    /**
     * 当前正文字号（单位 sp）。
     */
    public val fontSizeSpFlow: StateFlow<Int> = _fontSizeSpFlow.asStateFlow()

    /**
     * 加载指定文章。
     *
     * @param articleIdLong 文章 ID
     */
    public fun loadArticle(articleIdLong: Long): Unit {
        viewModelScope.launch {
            try {
                _articleResultFlow.value = getArticleByIdUseCase(articleIdLong)
            } catch (e: Exception) {
                PureLog.e(TAG, "loadArticle", e, "加载文章异常")
                _articleResultFlow.value = Result.Error(PureError.Unknown(throwable = e))
            }
        }
    }

    /**
     * 保存阅读进度。
     *
     * @param articleIdLong 文章 ID
     * @param progressPercentInt 进度百分比（0-100）
     */
    public fun saveProgress(articleIdLong: Long, progressPercentInt: Int): Unit {
        viewModelScope.launch {
            try {
                saveArticleProgressUseCase(articleIdLong, progressPercentInt)
            } catch (e: Exception) {
                PureLog.e(TAG, "saveProgress", e, "保存进度异常")
            }
        }
    }

    /**
     * 切换阅读主题。
     *
     * @param theme 目标主题
     */
    public fun setTheme(theme: ReaderTheme): Unit {
        _readerThemeFlow.value = theme
    }

    /**
     * 设置正文字号。
     *
     * @param fontSizeSp 字号（单位 sp）
     */
    public fun setFontSize(fontSizeSp: Int): Unit {
        _fontSizeSpFlow.value = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    }

    /**
     * 阅读器主题枚举。
     */
    public enum class ReaderTheme {
        DAY,
        NIGHT,
        EYE,
    }

    private companion object {
        private const val TAG = "ReaderViewModel"
        private const val DEFAULT_FONT_SIZE_SP = 18
        private const val MIN_FONT_SIZE_SP = 12
        private const val MAX_FONT_SIZE_SP = 32
    }
}

package com.pureread.ui.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pureread.R
import com.pureread.core.log.PureLog
import com.pureread.data.model.Result
import com.pureread.databinding.ActivityReaderBinding
import com.pureread.ui.common.EdgeToEdgeHelper
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 阅读器 Activity。
 *
 * 职责：
 * - 使用 WebView 渲染文章正文。
 * - 支持日间/夜间/护眼主题与字号调节。
 * - 通过手势实现点击左右翻页，滚动停止 1.5s 后防抖保存进度。
 *
 * 线程安全：UI 操作运行在主线程，进度保存委托给 [ReaderViewModel]。
 */
public class ReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReaderBinding
    private val viewModel: ReaderViewModel by viewModel()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val saveProgressRunnable = Runnable { saveProgressImmediate() }

    private var articleIdLong: Long = INVALID_ARTICLE_ID
    private var currentBodyHtml: String = ""

    protected override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        articleIdLong = intent.getLongExtra(EXTRA_ARTICLE_ID, INVALID_ARTICLE_ID)
        require(articleIdLong > 0) { "文章 ID 必须大于 0" }

        applyEdgeToEdge()
        setupToolbar()
        setupWebView()
        setupReaderControls()
        observeViewModel()

        viewModel.loadArticle(articleIdLong)
    }

    protected override fun onPause(): Unit {
        super.onPause()
        saveProgressImmediate()
    }

    protected override fun onDestroy(): Unit {
        super.onDestroy()
        mainHandler.removeCallbacks(saveProgressRunnable)
        binding.webViewReader.destroy()
    }

    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        EdgeToEdgeHelper.applyInsets(
            binding.toolbarReader,
            isTop = true,
            isBottom = false,
            isLeft = true,
            isRight = true,
        )
        EdgeToEdgeHelper.applyInsets(
            binding.controlsContainer,
            isTop = false,
            isBottom = false,
            isLeft = true,
            isRight = true,
        )
        EdgeToEdgeHelper.applyInsets(
            binding.webViewReader,
            isTop = false,
            isBottom = true,
            isLeft = true,
            isRight = true,
        )
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarReader)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarReader.setNavigationOnClickListener { finish() }
    }

    private fun setupWebView() {
        binding.webViewReader.settings.apply {
            javaScriptEnabled = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        binding.webViewReader.setBackgroundColor(Color.TRANSPARENT)

        val gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                public override fun onSingleTapUp(event: MotionEvent): Boolean {
                    val width = binding.webViewReader.width
                    return when {
                        event.x < width * TAP_LEFT_ZONE_RATIO -> {
                            binding.webViewReader.pageUp(true)
                            scheduleProgressSave()
                            true
                        }

                        event.x > width * TAP_RIGHT_ZONE_RATIO -> {
                            binding.webViewReader.pageDown(true)
                            scheduleProgressSave()
                            true
                        }

                        else -> false
                    }
                }
            },
        )

        binding.webViewReader.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        binding.webViewReader.setOnScrollChangeListener { _, _, _, _, _ ->
            scheduleProgressSave()
        }
    }

    private fun setupReaderControls() {
        binding.buttonThemeDay.setOnClickListener {
            viewModel.setTheme(ReaderViewModel.ReaderTheme.DAY)
            reloadHtml()
        }
        binding.buttonThemeNight.setOnClickListener {
            viewModel.setTheme(ReaderViewModel.ReaderTheme.NIGHT)
            reloadHtml()
        }
        binding.buttonThemeEye.setOnClickListener {
            viewModel.setTheme(ReaderViewModel.ReaderTheme.EYE)
            reloadHtml()
        }
        binding.buttonFontDecrease.setOnClickListener {
            viewModel.setFontSize(viewModel.fontSizeSpFlow.value - FONT_SIZE_STEP_SP)
            reloadHtml()
        }
        binding.buttonFontIncrease.setOnClickListener {
            viewModel.setFontSize(viewModel.fontSizeSpFlow.value + FONT_SIZE_STEP_SP)
            reloadHtml()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.articleResultFlow.collect { result ->
                    handleArticleResult(result)
                }
            }
        }
    }

    private fun handleArticleResult(result: Result<Pair<com.pureread.data.local.entity.ArticleEntity, String>>?) {
        when (result) {
            is Result.Success -> {
                val (article, bodyHtml) = result.data
                binding.toolbarReader.title = article.title
                currentBodyHtml = bodyHtml
                reloadHtml()
            }

            is Result.Error -> {
                PureLog.e(TAG, "handleArticleResult", "文章加载失败 | error=${result.error}")
            }

            else -> {
                // 加载中或空状态不处理
            }
        }
    }

    private fun reloadHtml() {
        if (currentBodyHtml.isBlank()) {
            return
        }
        val html = buildHtml(
            bodyHtml = currentBodyHtml,
            theme = viewModel.readerThemeFlow.value,
            fontSizeSp = viewModel.fontSizeSpFlow.value,
        )
        binding.webViewReader.loadDataWithBaseURL(null, html, MIME_TYPE, ENCODING, null)
    }

    private fun buildHtml(
        bodyHtml: String,
        theme: ReaderViewModel.ReaderTheme,
        fontSizeSp: Int,
    ): String {
        val (backgroundColor, textColor) = when (theme) {
            ReaderViewModel.ReaderTheme.DAY ->
                Pair(toHexColor(R.color.reader_background_day), toHexColor(R.color.reader_text_day))

            ReaderViewModel.ReaderTheme.NIGHT ->
                Pair(toHexColor(R.color.reader_background_night), toHexColor(R.color.reader_text_night))

            ReaderViewModel.ReaderTheme.EYE ->
                Pair(toHexColor(R.color.reader_background_eye), toHexColor(R.color.reader_text_eye))
        }

        return """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <style>
                    body {
                        background-color: $backgroundColor;
                        color: $textColor;
                        font-size: ${fontSizeSp}px;
                        line-height: 1.8;
                        padding: 16px;
                        margin: 0;
                        font-family: sans-serif;
                    }
                    img { max-width: 100%; height: auto; }
                    p { margin: 0.8em 0; }
                </style>
            </head>
            <body>$bodyHtml</body>
            </html>
        """.trimIndent()
    }

    private fun toHexColor(colorResId: Int): String {
        val colorInt = getColor(colorResId)
        return String.format("#%06X", 0xFFFFFF and colorInt)
    }

    private fun scheduleProgressSave() {
        mainHandler.removeCallbacks(saveProgressRunnable)
        mainHandler.postDelayed(saveProgressRunnable, PROGRESS_SAVE_DEBOUNCE_MS)
    }

    private fun saveProgressImmediate() {
        if (articleIdLong <= 0) {
            return
        }
        val progressPercentInt = computeProgressPercent()
        viewModel.saveProgress(articleIdLong, progressPercentInt)
        PureLog.i(TAG, "saveProgressImmediate", "progress=$progressPercentInt% | 完成")
    }

    private fun computeProgressPercent(): Int {
        val webView = binding.webViewReader
        val contentHeight = (webView.contentHeight * webView.scale).toInt()
        val maxScroll = contentHeight - webView.height
        return if (maxScroll > 0) {
            (webView.scrollY * 100 / maxScroll).coerceIn(0, 100)
        } else {
            0
        }
    }

    public companion object {

        private const val EXTRA_ARTICLE_ID = "extra_article_id"
        private const val TAG = "ReaderActivity"
        private const val INVALID_ARTICLE_ID = -1L
        private const val TAP_LEFT_ZONE_RATIO = 0.25f
        private const val TAP_RIGHT_ZONE_RATIO = 0.75f
        private const val PROGRESS_SAVE_DEBOUNCE_MS = 1500L
        private const val FONT_SIZE_STEP_SP = 2
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"

        /**
         * 启动阅读器。
         *
         * @param context 上下文
         * @param articleIdLong 文章 ID
         */
        @JvmStatic
        public fun start(context: Context, articleIdLong: Long): Unit {
            val intent = Intent(context, ReaderActivity::class.java).apply {
                putExtra(EXTRA_ARTICLE_ID, articleIdLong)
            }
            context.startActivity(intent)
        }
    }
}

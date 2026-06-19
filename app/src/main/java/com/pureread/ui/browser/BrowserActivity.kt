package com.pureread.ui.browser

import android.os.Bundle
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.pureread.R
import com.pureread.core.log.PureLog
import com.pureread.core.network.NetworkObserver
import com.pureread.core.network.NetworkState
import com.pureread.data.model.Result
import com.pureread.databinding.ActivityBrowserBinding
import com.pureread.ui.common.EdgeToEdgeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 浏览器 Activity。
 *
 * 职责：
 * - 提供 WebView 浏览与地址栏输入。
 * - 显示页面加载进度。
 * - 支持将当前页加入书架（触发正文提取）。
 *
 * 线程安全：UI 操作运行在主线程，提取请求委托给 [BrowserViewModel]。
 */
public class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private val viewModel: BrowserViewModel by viewModel
    private val networkStateFlow: Flow<NetworkState> by inject()
    private val networkObserver: NetworkObserver by inject()
    private var currentPageTitle: String = ""

    protected override fun onCreate(savedInstanceState: Bundle?): Unit {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdge()
        setupToolbar()
        setupAddressBar()
        setupWebView()
        setupFab()
        observeViewModel()
    }

    protected override fun onDestroy(): Unit {
        super.onDestroy()
        binding.webViewBrowser.destroy()
    }

    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        EdgeToEdgeHelper.applyInsets(
            binding.toolbarBrowser,
            isTop = true,
            isBottom = false,
            isLeft = true,
            isRight = true,
        )
        EdgeToEdgeHelper.applyInsets(
            binding.addressInput,
            isTop = false,
            isBottom = false,
            isLeft = true,
            isRight = true,
        )
        EdgeToEdgeHelper.applyInsets(
            binding.webViewBrowser,
            isTop = false,
            isBottom = true,
            isLeft = true,
            isRight = true,
        )
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarBrowser)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarBrowser.setNavigationOnClickListener { finish() }
        binding.toolbarBrowser.inflateMenu(R.menu.menu_browser)
        binding.toolbarBrowser.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_download_novel -> {
                    triggerNovelDownload()
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerNovelDownload() {
        val sourceUrl = viewModel.currentUrlFlow.value
            .takeIf { it.isNotBlank() }
            ?: binding.webViewBrowser.url
        sourceUrl?.let { url ->
            binding.webViewBrowser.evaluateJavascript(GET_PAGE_HTML_JS) { base64Html ->
                val htmlString = base64Html?.let { decodeBase64Html(it) }.orEmpty()
                viewModel.downloadNovel(url, htmlString, currentPageTitle)
            }
        }
    }

    private fun setupAddressBar() {
        binding.addressInput.editText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                loadUrlFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun loadUrlFromInput() {
        val inputUrl = binding.addressInput.editText?.text?.toString()?.trim().orEmpty()
        if (inputUrl.isBlank()) {
            return
        }

        val fullUrl = normalizeUrl(inputUrl)
        viewModel.onUrlChanged(fullUrl)
        if (!networkObserver.isNetworkAvailable()) {
            showOfflineErrorPage()
            showSnackbar(getString(R.string.network_offline))
            return
        }
        binding.webViewBrowser.loadUrl(fullUrl)
        binding.addressInput.editText?.clearFocus()
    }

    private fun showOfflineErrorPage() {
        binding.webViewBrowser.loadDataWithBaseURL(
            null,
            OFFLINE_ERROR_PAGE_HTML,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun normalizeUrl(inputUrl: String): String {
        return if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
            inputUrl
        } else {
            "https://$inputUrl"
        }
    }

    private fun setupWebView() {
        binding.webViewBrowser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        binding.webViewBrowser.webViewClient = object : WebViewClient() {
            public override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ): Unit {
                super.onPageStarted(view, url, favicon)
                url?.let { currentUrl ->
                    viewModel.onUrlChanged(currentUrl)
                    binding.addressInput.editText?.setText(currentUrl)
                }
                binding.progressBrowser.isVisible = true
            }

            public override fun onPageFinished(view: WebView?, url: String?): Unit {
                super.onPageFinished(view, url)
                binding.progressBrowser.isVisible = false
            }

            public override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ): Unit {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    PureLog.w(TAG, "onReceivedError", "页面加载失败 | errorCode=${error?.errorCode}")
                    showOfflineErrorPage()
                }
            }
        }

        binding.webViewBrowser.webChromeClient = object : WebChromeClient() {
            public override fun onProgressChanged(view: WebView?, newProgress: Int): Unit {
                super.onProgressChanged(view, newProgress)
                binding.progressBrowser.progress = newProgress
            }

            public override fun onReceivedTitle(view: WebView?, title: String?): Unit {
                super.onReceivedTitle(view, title)
                currentPageTitle = title ?: ""
            }
        }
    }

    private fun setupFab() {
        binding.fabAddToLibrary.setOnClickListener {
            val sourceUrl = viewModel.currentUrlFlow.value
                .takeIf { it.isNotBlank() }
                ?: binding.webViewBrowser.url
            sourceUrl?.let { url ->
                binding.webViewBrowser.evaluateJavascript(GET_PAGE_HTML_JS) { base64Html ->
                    val htmlString = base64Html?.let { decodeBase64Html(it) }.orEmpty()
                    viewModel.addToLibrary(url, htmlString)
                }
            }
        }
    }

    private fun decodeBase64Html(base64Html: String): String {
        return try {
            val normalized = base64Html.trim().removeSurrounding("\"")
            if (normalized.isBlank()) return ""
            val bytes = Base64.decode(normalized, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            PureLog.e(TAG, "decodeBase64Html", e, "HTML 解码失败")
            ""
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.extractResultFlow.collect { result ->
                        handleExtractResult(result)
                    }
                }

                launch {
                    viewModel.novelDownloadResultFlow.collect { result ->
                        handleNovelDownloadResult(result)
                    }
                }

                launch {
                    networkStateFlow.collect { state ->
                        handleNetworkState(state)
                    }
                }
            }
        }
    }

    private fun handleExtractResult(result: Result<Long>?) {
        when (result) {
            is Result.Success -> {
                val articleIdLong = result.data
                PureLog.i(TAG, "handleExtractResult", "加入书架 | articleId=$articleIdLong")
                showSnackbar(getString(R.string.browser_add_success, articleIdLong.toString()))
                viewModel.consumeExtractResult()
            }

            is Result.Error -> {
                PureLog.e(TAG, "handleExtractResult", "加入书架失败 | error=${result.error}")
                showSnackbar(getString(R.string.browser_add_failed))
                viewModel.consumeExtractResult()
            }

            else -> {
                // 空或加载状态不处理
            }
        }
    }

    private fun handleNovelDownloadResult(result: Result<Unit>?) {
        when (result) {
            is Result.Success -> {
                PureLog.i(TAG, "handleNovelDownloadResult", "小说下载已入队")
                showSnackbar(getString(R.string.download_running))
                viewModel.consumeNovelDownloadResult()
            }

            is Result.Error -> {
                PureLog.e(TAG, "handleNovelDownloadResult", "小说下载入队失败 | error=${result.error}")
                showSnackbar(getString(R.string.download_failed))
                viewModel.consumeNovelDownloadResult()
            }

            else -> {
                // 空状态不处理
            }
        }
    }

    private fun handleNetworkState(state: NetworkState) {
        when (state) {
            is NetworkState.Available -> {
                // 网络恢复时仅提示一次，避免启动时误报
            }

            is NetworkState.Unavailable -> {
                showSnackbar(getString(R.string.network_offline))
            }

            is NetworkState.Checking -> {
                // 检测中不提示
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.coordinatorBrowser, message, Snackbar.LENGTH_SHORT).show()
    }

    public companion object {
        private const val TAG = "BrowserActivity"
        private const val GET_PAGE_HTML_JS = """
            (function() {
                var html = document.documentElement.outerHTML;
                var bytes = new TextEncoder().encode(html);
                var binary = '';
                bytes.forEach(function(b) { binary += String.fromCharCode(b); });
                return btoa(binary);
            })()
        """

        private val OFFLINE_ERROR_PAGE_HTML = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; text-align: center; padding: 48px 24px; color: #757575; }
                    .icon { font-size: 64px; margin-bottom: 16px; }
                    h1 { font-size: 20px; color: #2C2C2C; margin-bottom: 8px; }
                    p { font-size: 16px; line-height: 1.6; }
                </style>
            </head>
            <body>
                <div class="icon">📡</div>
                <h1>当前无网络</h1>
                <p>请检查网络连接后重试。<br>PureRead 不生产内容，仅在你联网时帮你提取。</p>
            </body>
            </html>
        """.trimIndent()
    }
}

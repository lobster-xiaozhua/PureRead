package com.pureread.ui.browser

import android.os.Bundle
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
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
import com.pureread.data.model.Result
import com.pureread.databinding.ActivityBrowserBinding
import com.pureread.ui.common.EdgeToEdgeHelper
import kotlinx.coroutines.launch
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
    private val viewModel: BrowserViewModel by viewModel()

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
        binding.webViewBrowser.loadUrl(fullUrl)
        binding.addressInput.editText?.clearFocus()
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
        }

        binding.webViewBrowser.webChromeClient = object : WebChromeClient() {
            public override fun onProgressChanged(view: WebView?, newProgress: Int): Unit {
                super.onProgressChanged(view, newProgress)
                binding.progressBrowser.progress = newProgress
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
                viewModel.extractResultFlow.collect { result ->
                    handleExtractResult(result)
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
    }
}

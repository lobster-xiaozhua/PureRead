package com.pureread.data.remote.api

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.pureread.core.log.PureLog
import com.pureread.data.model.PureError
import com.pureread.data.model.Result
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.net.URL
import java.net.URLDecoder
import kotlin.coroutines.resume

/**
 * 通过 WebView 注入 Readability.js 并拦截 prompt() 通道提取文章。
 *
 * 职责：
 * - 加载 Readability.js 到 WebView
 * - 仅使用 prompt('PUREREAD://...') 作为结果通道
 * - 将结果解析为 [ArticleExtractResult]
 *
 * 线程安全：所有 WebView 操作必须在主线程执行；挂起函数内部切换到主线程。
 */
public class ReadabilityJSBridge(
    private val webView: WebView
) {

    private companion object {
        private const val TAG = "ReadabilityJSBridge"
        private const val PUREREAD_PROMPT_PREFIX = "PUREREAD://"
        private const val READABILITY_INJECTION_TIMEOUT_MS = 3000L
        private const val JS_ENCODING = "UTF-8"
        private const val MAX_PLAIN_TEXT_LENGTH = 5000
        private const val READABILITY_ASSET_PATH = "readability.js"
    }

    private var pendingContinuation: CancellableContinuation<Result<ArticleExtractResult>>? = null
    private var isReadabilityScriptLoaded: Boolean = false

    init {
        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        // 前置条件：webView 已实例化且处于主线程
        // 后置条件：JavaScript 已启用并设置 WebChromeClient 拦截 prompt
        // 副作用：修改 webView 设置与 ChromeClient
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = object : WebChromeClient() {
            public override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                if (message != null && message.startsWith(PUREREAD_PROMPT_PREFIX)) {
                    val payload = message.removePrefix(PUREREAD_PROMPT_PREFIX)
                    handlePromptPayload(payload)
                    result?.confirm("")
                    return true
                }
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }
        }
    }

    /**
     * 从 HTML 字符串提取文章。
     *
     * @param urlString 文章 URL
     * @param htmlString 原始 HTML
     * @return 提取结果
     */
    public suspend fun extractFromHtml(
        urlString: String,
        htmlString: String
    ): Result<ArticleExtractResult> = withContext(Dispatchers.Main) {
        val startTimeMs = System.currentTimeMillis()
        try {
            withTimeoutOrNull(READABILITY_INJECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    pendingContinuation = continuation
                    injectReadabilityScript(urlString, htmlString, startTimeMs)
                }
            } ?: buildTimeoutError(startTimeMs)
        } catch (e: Exception) {
            PureLog.e(
                TAG,
                "extractFromHtml",
                e,
                "提取失败 | costTimeMs=${System.currentTimeMillis() - startTimeMs}"
            )
            Result.Error(PureError.Fatal(e))
        } finally {
            pendingContinuation = null
        }
    }

    private fun injectReadabilityScript(
        urlString: String,
        htmlString: String,
        startTimeMs: Long
    ) {
        // 前置条件：运行在主线程
        // 后置条件：Readability 库与提取脚本已提交执行
        // 副作用：调用 webView.evaluateJavascript
        val isLibraryLoaded = ensureReadabilityScriptLoaded()
        if (!isLibraryLoaded) {
            resumeOnce(Result.Error(PureError.Extract(messageString = "Readability.js 加载失败")))
            return
        }
        val script = buildReadabilityScript(urlString, htmlString)
        // 仅通过 prompt() 通道接收结果，禁止 evaluateJavascript 回调
        webView.evaluateJavascript(script, null)
    }

    private fun ensureReadabilityScriptLoaded(): Boolean {
        if (isReadabilityScriptLoaded) return true
        return try {
            val libraryScript = loadReadabilityScriptFromAssets()
            if (libraryScript.isBlank()) {
                PureLog.w(TAG, "ensureReadabilityScriptLoaded", "readability.js 内容为空")
                return false
            }
            webView.evaluateJavascript(libraryScript, null)
            isReadabilityScriptLoaded = true
            true
        } catch (e: Exception) {
            PureLog.e(TAG, "ensureReadabilityScriptLoaded", e, "readability.js 加载失败")
            false
        }
    }

    private fun loadReadabilityScriptFromAssets(): String {
        return webView.context.assets.open(READABILITY_ASSET_PATH).use { inputStream ->
            BufferedReader(inputStream.reader()).use { reader ->
                reader.readText()
            }
        }
    }

    private fun buildReadabilityScript(urlString: String, htmlString: String): String {
        val base64Html = Base64.encodeToString(htmlString.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val siteName = extractSiteNameFromUrl(urlString)
        return """
            (function() {
                try {
                    var htmlBytes = Uint8Array.from(atob('$base64Html'), function(c) { return c.charCodeAt(0); });
                    var htmlString = new TextDecoder('$JS_ENCODING').decode(htmlBytes);
                    var doc = new DOMParser().parseFromString(htmlString, 'text/html');
                    Object.defineProperty(doc, 'baseURI', { value: '$urlString', configurable: true });
                    var article = new Readability(doc).parse();
                    if (!article) {
                        prompt('${PUREREAD_PROMPT_PREFIX}ERROR_NO_CONTENT');
                        return;
                    }
                    var payload = {
                        title: (article.title || ''),
                        bodyHtml: (article.content || ''),
                        plainText: ((article.textContent || '').substring(0, $MAX_PLAIN_TEXT_LENGTH)),
                        siteName: (article.siteName || '$siteName'),
                        coverImageUrl: '',
                        url: '$urlString'
                    };
                    var jsonString = JSON.stringify(payload);
                    var encoded = encodeURIComponent(jsonString);
                    var base64Encoded = btoa(encoded);
                    prompt('${PUREREAD_PROMPT_PREFIX}' + base64Encoded);
                } catch (err) {
                    var errorPayload = { error: (err.message || 'unknown') };
                    prompt('${PUREREAD_PROMPT_PREFIX}' + btoa(encodeURIComponent(JSON.stringify(errorPayload))));
                }
            })()
        """.trimIndent()
    }

    private fun handlePromptPayload(payload: String) {
        // 前置条件：payload 已去除 PUREREAD_PROMPT_PREFIX
        // 后置条件：pendingContinuation 被恢复
        // 副作用：解码并解析 JSON
        if (payload == "ERROR_NO_CONTENT") {
            resumeOnce(Result.Error(PureError.Extract(messageString = "Readability 未识别到正文内容")))
            return
        }
        val result: Result<ArticleExtractResult> = runCatching {
            val decodedBytes = Base64.decode(payload, Base64.DEFAULT)
            val urlEncoded = String(decodedBytes, Charsets.UTF_8)
            val jsonString = URLDecoder.decode(urlEncoded, JS_ENCODING)
            val json = JSONObject(jsonString)
            if (json.has("error")) {
                val errorMessage = json.optString("error", "Readability JS 错误")
                Result.Error(PureError.Extract(messageString = errorMessage))
            } else {
                Result.Success(
                    ArticleExtractResult(
                        title = json.optString("title", ""),
                        bodyHtml = json.optString("bodyHtml", ""),
                        plainText = json.optString("plainText", ""),
                        siteName = json.optString("siteName", ""),
                        coverImageUrl = json.optString("coverImageUrl", ""),
                        url = json.optString("url", "")
                    )
                )
            }
        }.getOrElse { e ->
            PureLog.e(TAG, "handlePromptPayload", e, "prompt 负载解析失败")
            Result.Error(PureError.Fatal(e))
        }
        resumeOnce(result)
    }

    private fun buildTimeoutError(startTimeMs: Long): Result<ArticleExtractResult> {
        val costTimeMs = System.currentTimeMillis() - startTimeMs
        PureLog.w(TAG, "extractFromHtml", "提取超时 | costTimeMs=$costTimeMs")
        return Result.Error(PureError.Extract(messageString = "Readability 提取超时"))
    }

    private fun resumeOnce(result: Result<ArticleExtractResult>) {
        pendingContinuation?.resume(result) {
            PureLog.w(TAG, "resumeOnce", "恢复时被取消")
        }
        pendingContinuation = null
    }

    private fun extractSiteNameFromUrl(urlString: String): String {
        return try {
            URL(urlString).host.removePrefix("www.")
        } catch (e: Exception) {
            PureLog.e(TAG, "extractSiteNameFromUrl", e, "URL 解析失败")
            ""
        }
    }
}

package com.pureread

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.webkit.WebView
import com.pureread.core.di.appModule
import com.pureread.core.log.PureLog
import com.pureread.core.network.NetworkObserver
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * PureRead 应用入口。
 *
 * 职责：
 * - 初始化 Koin 依赖注入
 * - 初始化 Room 数据库
 * - 延迟预热系统 WebView（满足冷启动 ≤ 1.2s 红线）
 *
 * 线程安全：所有初始化均运行在主线程。
 */
public class App : Application() {

    protected override fun attachBaseContext(base: Context?): Unit {
        super.attachBaseContext(base)
        PureLog.d("App", "attachBaseContext", "完成")
    }

    protected override fun onCreate(): Unit {
        val startTimeMs = System.currentTimeMillis()
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }

        initNetworkObserver()
        scheduleWebViewPreload()

        val costTimeMs = System.currentTimeMillis() - startTimeMs
        if (costTimeMs > COLD_START_WARNING_THRESHOLD_MS) {
            PureLog.w("App", "onCreate", "冷启动耗时超过红线 | costTimeMs=$costTimeMs")
        } else {
            PureLog.i("App", "onCreate", "完成 | costTimeMs=$costTimeMs")
        }
    }

    public override fun onTrimMemory(level: Int): Unit {
        super.onTrimMemory(level)
        PureLog.i("App", "onTrimMemory", "level=$level")
        if (level >= TRIM_MEMORY_MODERATE) {
            clearImageCache()
            clearWebViewCache()
        }
    }

    private fun initNetworkObserver() {
        try {
            val networkObserver = get<NetworkObserver>()
            PureLog.i("App", "initNetworkObserver", "网络观察者初始化完成 | available=${networkObserver.isNetworkAvailable()}")
        } catch (e: Exception) {
            PureLog.e("App", "initNetworkObserver", e, "网络观察者初始化失败")
        }
    }

    private fun scheduleWebViewPreload() {
        Looper.myQueue().addIdleHandler(object : MessageQueue.IdleHandler {
            public override fun queueIdle(): Boolean {
                preloadWebViewOnce()
                return false
            }
        })
    }

    private fun preloadWebViewOnce() {
        try {
            WebView(applicationContext)
            PureLog.i("App", "preloadWebViewOnce", "WebView 预热完成")
        } catch (e: Exception) {
            PureLog.e("App", "preloadWebViewOnce", e, "WebView 预热失败")
        }
    }

    private fun clearImageCache() {
        // Coil 缓存清理由 Coil.imageLoader(context).memoryCache?.clear() 在需要处调用
        PureLog.i("App", "clearImageCache", "已触发")
    }

    private fun clearWebViewCache() {
        try {
            WebView(applicationContext).clearCache(true)
            PureLog.i("App", "clearWebViewCache", "完成")
        } catch (e: Exception) {
            PureLog.e("App", "clearWebViewCache", e, "失败")
        }
    }

    companion object {
        private const val COLD_START_WARNING_THRESHOLD_MS = 1200L
        private const val TRIM_MEMORY_MODERATE = 60
    }
}

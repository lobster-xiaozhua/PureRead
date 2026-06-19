package com.pureread.core.di

import android.webkit.WebView
import androidx.room.Room
import androidx.work.WorkManager
import com.pureread.core.log.PureLog
import com.pureread.core.utils.FileUtils
import com.pureread.data.local.PureReadDatabase
import com.pureread.data.remote.api.ArticleExtractor
import com.pureread.data.remote.api.DefaultArticleExtractor
import com.pureread.data.remote.api.ReadabilityJSBridge
import com.pureread.data.remote.downloader.ChapterDownloader
import com.pureread.data.remote.parser.NovelCatalogParser
import com.pureread.data.repository.ArticleRepository
import com.pureread.data.repository.RoomArticleRepository
import com.pureread.domain.usecase.AddArticleUseCase
import com.pureread.domain.usecase.DeleteArticleUseCase
import com.pureread.domain.usecase.CreateNovelArticleUseCase
import com.pureread.domain.usecase.DownloadNovelUseCase
import com.pureread.domain.usecase.FetchAndAddArticleUseCase
import com.pureread.domain.usecase.GetArticleByIdUseCase
import com.pureread.domain.usecase.GetArticleUseCase
import com.pureread.domain.usecase.GetArticlesUseCase
import com.pureread.domain.usecase.RefreshArticleUseCase
import com.pureread.domain.usecase.SaveArticleProgressUseCase
import com.pureread.ui.browser.BrowserViewModel
import com.pureread.ui.library.LibraryViewModel
import com.pureread.ui.main.MainViewModel
import com.pureread.ui.reader.ReaderViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Koin 全局依赖注入模块。
 *
 * 职责：
 * - 提供网络层（OkHttp、Retrofit）
 * - 提供本地数据库及所有 DAO
 * - 提供 Repository、UseCase、ViewModel 实例
 *
 * 线程安全：Koin 保证单例作用域，single 声明的依赖全局唯一。
 */
public val appModule = module {

    // region 网络层

    /**
     * 全局 OkHttp 客户端。
     */
    single<OkHttpClient> {
        val loggingInterceptor = HttpLoggingInterceptor { messageString ->
            PureLog.d("OkHttp", "intercept", messageString)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * 全局 Retrofit 实例。
     */
    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL_STRING)
            .client(get<OkHttpClient>())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    // endregion

    // region 本地数据层

    /**
     * 全局 Room 数据库实例。
     */
    single<PureReadDatabase> {
        Room.databaseBuilder(
            androidContext(),
            PureReadDatabase::class.java,
            PureReadDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration(false)
            .build()
    }

    single { get<PureReadDatabase>().articleDao() }
    single { get<PureReadDatabase>().articleBodyDao() }
    single { get<PureReadDatabase>().chapterDao() }
    single { get<PureReadDatabase>().downloadTaskDao() }

    // endregion

    // region Repository 层

    single<ArticleRepository> {
        RoomArticleRepository(database = get())
    }

    // endregion

    // region 工具与远程层

    single { FileUtils }
    single { NovelCatalogParser() }
    single { ChapterDownloader(okHttpClient = get()) }

    /**
     * 每次注入时创建新的 WebView 与 Bridge，避免跨生命周期复用导致内存泄漏。
     * 注意：WebView 必须在主线程实例化，调用方需保证在主线程获取该 factory。
     */
    factory { ReadabilityJSBridge(WebView(androidContext())) }

    single<ArticleExtractor> { DefaultArticleExtractor(get()) }

    single { WorkManager.getInstance(androidContext()) }

    // endregion

    // region UseCase 层

    factory { AddArticleUseCase(articleDao = get(), articleBodyDao = get(), articleExtractor = get()) }
    factory { GetArticlesUseCase(articleDao = get()) }
    factory { GetArticleUseCase(articleRepository = get(), articleBodyDao = get()) }
    factory { RefreshArticleUseCase(articleRepository = get(), articleDao = get(), articleBodyDao = get(), articleExtractor = get()) }
    factory { DeleteArticleUseCase(articleRepository = get(), chapterDao = get()) }
    factory { DownloadNovelUseCase(novelCatalogParser = get(), chapterDao = get(), downloadTaskDao = get(), workManager = get()) }
    factory { SaveArticleProgressUseCase(articleRepository = get()) }
    factory { GetArticleByIdUseCase(articleRepository = get()) }
    factory { FetchAndAddArticleUseCase(okHttpClient = get(), articleExtractor = get(), articleDao = get(), articleBodyDao = get()) }
    factory { CreateNovelArticleUseCase(articleDao = get()) }

    // endregion

    // region ViewModel 层

    viewModel { MainViewModel() }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { BrowserViewModel(get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get()) }

    // endregion
}

private const val DEFAULT_BASE_URL_STRING = "https://example.com/"
private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
private const val DEFAULT_READ_TIMEOUT_MS = 30_000L
private const val DEFAULT_WRITE_TIMEOUT_MS = 30_000L

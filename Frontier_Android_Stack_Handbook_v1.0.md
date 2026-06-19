# ==========================================
# PureRead 核心规则 v1.0（始终生效）
# ==========================================

## 1. 技术栈锁定（不可偏离）
- 语言: Kotlin 2.3.21（禁止使用 Java）
- 构建: AGP 9.0.0 + Gradle 9.3.0 + KSP（严禁使用 kapt）
- 最低兼容: Android 8.0 (API 24)
- 架构: MVVM + Repository + Flow + Koin
- 正文提取: Readability.js + prompt() 拦截（禁止用 evaluateJavascript 回调）

## 2. 日系编程规范（命名与注释）
- 变量必须带单位/类型后缀: articleId, retryCountInt, timeoutMs
- 布尔变量必须 is/has/can/should 前缀: isDownloading, hasReadPermission
- 常量全大写+下划线+业务前缀: MAX_RETRY_COUNT, DEFAULT_TIMEOUT_MS
- 类名严禁使用 Impl 后缀，改用动词+er/or: ArticleExtractor, ChapterDownloader
- 所有 public 类/方法必须带 KDoc（含业务职责 + 线程安全性）
- 复杂方法必须标注 // 前置条件、// 后置条件、// 副作用

## 3. 异常与日志强制
- 禁止空 catch，所有 catch 块必须包含 PureLog.e
- 异常分层: 致命(崩溃) / 业务(Result) / 瞬时(重试)
- Log 格式强制: [Class简单名] [操作] | 结果 | 耗时(ms)

## 4. 数据与存储契约
- Room 主表与 Body 表分离（列表查主表，详情联查 Body）
- 文件写入必须两阶段提交（.tmp → .txt 原子 renameTo）
- 数据库 status = COMPLETED 必须在 renameTo 成功之后更新

## 5. 性能红线
- 冷启动 ≤ 1.2 秒（超时记录为 WARN）
- WebView 初始化必须在 IdleHandler 中延迟执行
- 阅读进度保存必须防抖（滚动停止 1.5 秒后保存）
- 内存压力 (onTrimMemory) 必须清理 Coil 缓存和 WebView 缓存






PureRead 完整技术开发规格书 v3.0（整合版）

项目代号：PureRead
技术基准：AGP 9.0.0 + Gradle 9.3.0 + Kotlin 2.3.21（前沿型技术栈）
架构范式：MVVM + Repository（离线优先）
开发周期：10 周（单人）
最后更新：2026-06-18
文档状态：✅ 已冻结，具备开工条件
文件版本：v3.0（集成构建加速 + 更新模块）


快速导航

章节 内容 适用阶段
第1章 项目概述与战略定位 通读
第2章 最终技术栈与构建环境 Phase 0 基建
第3章 应用架构与模块划分 全程
第4章 数据层设计（Room + FTS4） Phase 0 基建
第5章 核心功能技术实现细节 Phase 1-3 开发
第6章 UI/UX 设计规范 全程
第7章 合规、隐私与安全性 Phase 5 上架
第7.5章 Android 16（API 36）适配专项 Phase 1-5 全程
第8章 开发路线图与里程碑（10周） 项目管理
第9章 风险登记册与应对预案 全程
第10章 附录：构建与测试指令 Phase 0 基建
第11章 核心交互逻辑与状态机定义 Phase 1-3 开发
第12章 高阶交互逻辑与边界场景定义 Phase 2-4 开发
第13章 系统健壮性与底层防御协议 Phase 4 体验打磨
第14章 日系编程规范与实施标准 全程编码
第15章 混沌工程与故障注入协议 Phase 4 测试
第16章 极致性能优化（2026前沿型方案） Phase 4 优化
附录A 构建加速配置指南 Phase 0 基建
附录B 更新模块 UI/UX 与开发技术规范 Phase 5 上架

正文

第1章 项目概述与战略定位

1.1 产品定义

PureRead 是一款基于系统 WebView 的“纯净阅读浏览器”。它不生产内容，只做内容的“蒸馏器”——将杂乱的网页提纯为优雅的阅读版面，并将连载小说转化为可离线翻阅的本地文库。

1.2 核心战略原则（不可妥协）

1. 本地主权：所有数据（文章、书籍、配置）仅存于应用私有目录，绝对不上传任何字节。
2. 零内置源：不预置任何书源、网址或版权内容，入口完全由用户提供。
3. 容灾优先：在弱网、反爬、DOM 结构变异时，必须有备用方案，绝不崩溃或白屏。
4. 触觉确定性：每个可操作区域有明确的视觉反馈，不让用户猜测“点了有没有反应”。

1.3 约束条件

· 开发规模：一人独立开发，需极度克制过度设计。
· 语言：Kotlin（兼容 Java 互调）。
· 最低兼容：Android 8.0 (API 24)，覆盖 98% 活跃设备。
· 目标用户：非技术向深度阅读爱好者，交互需直观。

第2章 最终技术栈与构建环境

本章为项目基础设施基线，所有版本号均已锁定。

2.1 核心组件版本矩阵

组件 选定版本 备注
Android Studio Otter 3 Feature Drop (2025.2.3) 必须 ≥ 此版本，否则无法识别 AGP 9.0 新 DSL
JDK JDK 17 编译与运行时强制统一
AGP (Android Gradle Plugin) 9.0.0 已内置 Kotlin 支持，移除旧插件
Gradle Wrapper 9.3.0 对应 AGP 9.0 官方推荐
Kotlin (Compiler & Runtime) 2.3.21 由 AGP 内置管理版本
KSP (Kotlin Symbol Processing) 2.3.2 替代 kapt，用于 Room 编译期代码生成（需与 Kotlin 主版本保持对齐）

2.2 关键依赖库选型

层级 组件 选型理由
异步 Coroutines + Flow 轻量协程，生命周期绑定 ViewModel
依赖注入 Koin (轻量级) 仅用于 Repository 和 ViewModel，避免反射开销
本地存储 Room (SQLite) 开启 FTS4 全文搜索，支持 KSP 编译时验证
网络请求 OkHttp + Retrofit 配置合理的连接超时 (10s) 与读取超时 (30s)
HTML 解析 Jsoup 用于目录解析和 Level 3 启发式降级
正文提取核心 Readability.js (官方) + prompt() 拦截 存储在 assets/readability.js，零移植成本
图片加载 Coil 支持内存/磁盘二级缓存，协程友好
后台任务 WorkManager (CoroutineWorker) 处理小说批量下载，支持省电模式适配

2.3 构建脚本最终配置

重大变更声明：
AGP 9.0 已内置 Kotlin 支持，必须移除 kotlin-android 插件。kapt 已废弃，强制迁移至 KSP。

根目录 build.gradle.kts（项目级）

```kotlin
plugins {
    // 仅保留 AGP 插件，Kotlin 支持已内置
    id("com.android.application") version "9.0.0" apply false
    id("com.android.library") version "9.0.0" apply false
    // KSP 用于 Room 注解处理器（版本需与 Kotlin 主版本对齐）
    id("com.google.devtools.ksp") version "2.3.2" apply false
}
```

模块级 app/build.gradle.kts（核心变化）

```kotlin
plugins {
    id("com.android.application")
    // id("org.jetbrains.kotlin.android") // ⚠️ 已彻底删除（AGP 内置）
    id("com.google.devtools.ksp")
    id("io.insert-koin") version "4.0.0"
}

android {
    namespace = "com.pureread"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pureread"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    // Kotlin 编译工具链继承上述设置
    kotlin { jvmToolchain(17) }

    buildFeatures { viewBinding = true }
}

dependencies {
    // Kotlin 标准库（版本由 AGP 自动对齐）
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    
    // Room (使用 KSP 替代 kapt)
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")  // <--- 关键替换
    
    // 生命周期
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    
    // 网络
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // HTML 解析
    implementation("org.jsoup:jsoup:1.18.3")
    
    // 图片加载
    implementation("io.coil-kt:coil:3.0.4")
    
    // 后台任务
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    
    // 依赖注入
    implementation("io.insert-koin:koin-android:4.0.0")
}
```

gradle/wrapper/gradle-wrapper.properties

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.3.0-bin.zip
```

第3章 应用架构与模块划分

3.1 架构分层图

```text
[ UI Layer ]  ->  Activity/Fragment (XML Views)
       ↕ (观察 StateFlow)
[ ViewModel ] ->  持有状态, 处理 Intent, 调用 UseCase
       ↕
[ Domain ]    ->  UseCase (提取、下载、解析), Repository 接口
       ↕
[ Data ]      ->  Repository 实现 (协调 Local/Remote)
       ↕
[ Local ]     ->  Room DAO, 文件存储
[ Remote ]    ->  Retrofit API, Readability JS 桥接, Jsoup 爬取
```

3.2 包结构定义

```text
com.pureread/
├── App.kt                          # Application 入口
├── core/
│   ├── di/                         # Koin Module 定义
│   ├── network/                    # OkHttp 单例
│   ├── repository/                 # 接口定义
│   └── util/                       # 扩展函数、UrlUtils
├── data/
│   ├── local/
│   │   ├── database/               # AppDatabase, Converters
│   │   ├── dao/                    # ArticleDao, BookDao, ChapterDao
│   │   └── entity/                 # 数据实体
│   └── remote/
│       ├── extractor/              # 正文提取核心
│       │   ├── ReadabilityJSBridge.kt
│       │   ├── SiteAdapter.kt
│       │   └── HeuristicFallback.kt
│       ├── parser/                 # 小说目录解析
│       │   ├── CatalogParser.kt
│       │   └── LinkClusterEngine.kt
│       └── download/
│           └── ChapterDownloadWorker.kt
├── ui/
│   ├── main/                       # MainActivity + BottomNav
│   ├── browser/                    # WebView 容器
│   ├── reader/                     # 阅读器
│   ├── library/                    # 文库
│   ├── novel/                      # 小说下载管理
│   └── settings/                   # 设置页
├── update/                         # 🆕 更新模块
│   ├── UpdateManager.kt            # Builder 模式对外暴露
│   ├── GitHubUpdateChecker.kt      # GitHub Releases API
│   └── ApkDownloadWorker.kt        # WorkManager 下载
└── widget/                         # 自定义 View (BottomSheet 预览)
```

第4章 数据层设计（Room + FTS4）

4.1 设计原则

· 读写分离：列表加载仅查主表，进入阅读器时才联查内容体。
· 外键级联：删除书籍自动删除章节。
· 全文搜索：对 ArticleBody.content_text 建立 FTS4 虚拟表。

4.2 核心实体定义

表：articles (文章主表 - 轻量)

```kotlin
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val author: String?,
    val extractTime: Long,
    val lastReadTime: Long?,
    val readProgress: Int = 0,
    val wordCount: Int = 0,
    val isFavorite: Boolean = false,
    val extractStatus: String = "SUCCESS" // SUCCESS/PARTIAL/EMPTY/ERROR
)
```

表：article_bodies (文章内容体 - 重量)

```kotlin
@Entity(tableName = "article_bodies")
data class ArticleBodyEntity(
    @PrimaryKey val articleId: Long, // 与 articles.id 一一对应
    val contentHtml: String,
    val contentText: String,
    val extractConfidence: Float
)
```

表：books (书籍主表)

```kotlin
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val sourceUrl: String,
    val totalChapters: Int = 0,
    val downloadedChapters: Int = 0,
    val readProgress: Float = 0f,
    val lastReadChapterId: Long?,
    val status: String // PARSING, DOWNLOADING, COMPLETED, ERROR
)
```

表：chapters (章节表)

```kotlin
@Entity(tableName = "chapters", foreignKeys = [ForeignKey(...)])
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val index: Int,
    val title: String,
    val url: String,
    val content: String? = null,
    val status: String, // PENDING, COMPLETED, FAILED
    val downloadTime: Long?,
    val wordCount: Int = 0
)
```

4.3 FTS4 全文搜索配置

```kotlin
@Fts4(contentEntity = ArticleBodyEntity::class)
@Entity(tableName = "articles_fts")
data class ArticleFtsEntity(
    @ColumnInfo(name = "content_text") val contentText: String
)
// 搜索语法：SELECT * FROM articles_fts WHERE content_text MATCH :keyword
```

第5章 核心功能技术实现细节

5.1 正文提取引擎（JS 注入 + 桥接协议）

5.1.1 方案选型说明

当前默认采用 `prompt()` 拦截作为 JS → Native 的数据通道，原因：

- `evaluateJavascript(...)` 的结果回调在部分旧版本 ROM 上曾出现大 JSON 截断或编码异常。
- `prompt()` 是同步通道，实现简单，便于在 `WebChromeClient` 中统一拦截与版本校验（见 13.1 节协议版本控制）。

但需要注意：

- `evaluateJavascript()` 是现代 Android（API 24+）官方推荐的标准桥接方式，在 Android 16 上仍然稳定可用。
- `prompt()` 依赖 `WebChromeClient.onJsPrompt`，部分第三方 WebView 内核可能行为不一致。

工程建议：

1. 主路径使用 `prompt()` 拦截。
2. 在 Phase 1 结束前实现一条 `evaluateJavascript()` 备用通道：当 `prompt()` 拦截失败（返回 null、超时、版本不匹配）时自动切换。
3. 所有桥接逻辑统一封装到 `ReadabilityJSBridge`，对外只暴露 `suspend fun extract(url: String): ReadabilityResult`，内部通道对调用方透明。

5.1.2 JS 端逻辑 (assets/readability.js)：

```javascript
function extractContent() {
    var article = new Readability(document).parse();
    if (article) {
        var jsonStr = JSON.stringify(article);
        var encoded = btoa(encodeURIComponent(jsonStr));
        prompt('PUREREAD://' + encoded);
    }
}
```

Native 端拦截 (WebChromeClient)：

```kotlin
override fun onJsPrompt(...): Boolean {
    if (message?.startsWith("PUREREAD://") == true) {
        val base64 = message.removePrefix("PUREREAD://")
        // JS 侧先 encodeURIComponent 再 btoa，Native 侧需先 Base64 解码再 URL 解码
        val json = URLDecoder.decode(
            String(Base64.decode(base64, Base64.DEFAULT)),
            StandardCharsets.UTF_8.name()
        )
        // 回调 ViewModel
        return true
    }
    return false
}
```

四级降级策略：

1. Level 1：JS Readability 算法。
2. Level 2：网站适配器 (JSON 规则，针对 medium/知乎等)。
3. Level 3：启发式降级 (Jsoup 取最大文本密度块)。
4. Level 4：保底 (移除 <script> 和 <style> 后的原始 HTML)。

5.2 小说目录解析（链接聚簇启发式）

流程：Jsoup 解析 → 若失败/结果<5条 → 启动 LinkClusterEngine。

URL 清洗前置（防时间戳/随机数干扰）：

```kotlin
private fun cleanLinkForCluster(url: String): String = url
    .replace(Regex("#.*$"), "")
    .replace(Regex("\\?timestamp=\\d+"), "")
    .replace(Regex("\\?(_=\\d+|rand=\\w+)"), "")
```

聚类逻辑：计算清洗后链接的 最长公共前缀 (LCP)，取数量最多的前缀簇作为章节列表。

5.3 WebView 生命周期治理（单例预热 + 空闲自毁）

· 预热管理器：应用启动时创建离屏 WebView 并加载 about:blank。
· 空闲自毁：挂载 IdleTimer。若 10 分钟无阅读器复用请求，调用 webView.destroy() 并置空。
· 复用流程：阅读器 onResume 时取出实例 attach，onPause 时 detach 归还。

5.4 阅读器长文分章加载（原子追加）

JS 防重入守卫：

```javascript
var lastLoadedIndex = -1;
function loadNextChapter(index) {
    if (index !== lastLoadedIndex + 1) return; // 原子性校验
    // 追加 DOM 节点
    lastLoadedIndex = index;
}
```

第6章 UI/UX 设计规范

6.1 核心设计原则

1. 内容至上：UI 是可隐退的容器，一切为阅读服务。
2. 触觉确定性：按钮点击必有涟漪或状态变化。
3. 呼吸感：行距 ≥ 1.8，页边距 ≥ 16dp，遵循 8dp 网格系统。

6.2 色彩体系

角色 日间 (Light) 夜间 (Dark) 护眼 (Eco)
主背景 #FAF8F5 #1A1A1A #C7EDCC
表面/卡片 #FFFFFF #252525 #D8F5DC
主文字 #2C2C2C #C8C8C8 #2C3E2C
次要文字 #757575 #9E9E9E #5A6B5A
强调色 #1A73E8 #8AB4F8 #2E7D32

6.3 阅读器关键交互规范

· 手势分区：点击左 1/3 上一页，右 1/3 下一页，中 1/3 呼出控制层。
· 翻页模式：
  · 点击翻页（工程稳健版）：WebView 采用 scrollBy(0, viewHeight * 0.9) 步进滚动，保留 10% 重叠作为上下文锚点，零 JS 计算偏差。
  · 滚动翻页：原生平滑滚动，底部显示微弱滚动条。
· 进度保存：滚动停止后 1.5 秒防抖保存 + onPause 强制保存，避免高频数据库写入。

第7章 合规、隐私与安全性

检查项 实现方案
隐私政策 App 内嵌入 PrivacyFragment，首次启动强制展示。
权限申请 核心权限仅 INTERNET + FOREGROUND_SERVICE，不申请存储权限；更新模块因系统安装 APK 需要，仅在用户主动选择“软件内更新”时动态请求 REQUEST_INSTALL_PACKAGES。
第三方 SDK 零。不使用 Firebase、Bugly 等任何统计/崩溃收集 SDK。
版权免责 启动引导页明确声明：“请勿用于盗版传播，用户需自行承担内容来源的法律责任”。
无障碍 (Accessibility) 所有可点击元素必须含 contentDescription，对比度 ≥ 4.5:1。

第7.5章 Android 16（API 36）适配专项

> 本章为 Android 16（API 36）targetSdk 强制行为变更的集中应对指南。文档已设定 `compileSdk = 36`、`targetSdk = 36`，以下适配必须在 Phase 1 启动前完成技术预研，并在 Phase 2-4 逐步落地。

7.5.1 为什么必须单独处理

Android 16 对 `targetSdk >= 36` 的应用启用了多项强制性运行时行为变更，其中与 PureRead 直接相关的有：

1. **Edge-to-Edge 强制生效**：系统忽略 `setDecorFitsSystemWindows(true)` 调用，所有 Activity 默认进入全面屏模式。
2. **Predictive Back 手势**：若开启 `android:enableOnBackInvokedCallback="true"`，返回键逻辑必须迁移到 `OnBackInvokedDispatcher`。
3. **大屏幕方向/尺寸限制失效**：在 600dp+ 设备上，`screenOrientation` 等限制被系统忽略。
4. **通知 cooldown 与强制分组**：高频进度通知可能被合并或降频。
5. **废弃 `announceForAccessibility`**：无障碍即时播报需改用 Live Region 或 `AccessibilityManager`。

未处理上述变更将导致：阅读器被导航栏遮挡、返回手势丢失状态、横竖屏切换异常、下载通知不更新、无障碍提示失效。

7.5.2 Edge-to-Edge 强制适配

所有 Activity/Fragment 的根布局必须监听 WindowInsets，并为系统栏留出安全区域。

阅读器沉浸式场景：

```kotlin
// 在阅读器全屏时，先隐藏系统栏再让 WebView 占据全屏
// 控制层（工具栏、BottomSheet）需通过 WindowInsetsCompat 获取 systemBars 高度并设置 padding
ViewCompat.setOnApplyWindowInsetsListener(readerControlBar) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
    insets
}
```

BottomSheet/Dialog 场景：

```kotlin
// BottomSheetDialogFragment 需设置 extendEdgeToEdge = true 并为底部导航栏补 padding
val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.updatePadding(bottom = systemBars.bottom)
    insets
}
```

7.5.3 Predictive Back 手势

若项目启用 Predictive Back（推荐在 Android 16 上开启），所有返回逻辑必须注册到 `OnBackInvokedDispatcher`：

```kotlin
// 在阅读器等需要拦截返回的页面注册
onBackInvokedDispatcher.registerOnBackInvokedCallback(
    OnBackInvokedDispatcher.PRIORITY_DEFAULT
) {
    if (isReaderControlBarVisible) {
        hideReaderControlBar()
    } else {
        finish() // 或调用默认返回
    }
}
```

注意：批量选择模式、编辑对话框、搜索条展开等临时状态，返回时应优先关闭临时层，而非退出页面。

7.5.4 大屏幕与折叠屏适配

Android 16 在大屏设备上忽略 `screenOrientation` 限制，阅读器必须：

- 支持横竖屏切换且阅读进度不丢失（通过 `ViewModel` + `SavedStateHandle` 保存 `scrollY`）。
- 横屏时左右 1/3 点击翻页区域需按当前宽度重新计算。
- 分屏/多窗口模式下，WebView 手势区域不得与系统分屏手柄冲突。

7.5.5 通知行为变更

小说下载与 APK 更新通知需遵守 Android 16 新规：

- **避免高频刷新**：使用同一个通知 ID 更新进度，而非每次发送新通知。
- **Notification Cooldown**：进度更新间隔建议 ≥ 1 秒，避免被系统降频。
- **强制分组**：为下载完成、更新完成等通知指定合理的 GroupKey，避免被系统强制合并后丢失关键信息。

7.5.6 无障碍变更

- 不再使用 `View.announceForAccessibility()`。
- 对需要即时播报的视图设置 `android:liveRegion="polite"`，或调用 `AccessibilityManager.sendAccessibilityEvent()`。

7.5.7 Android 16 验证清单

| 验证项 | 通过标准 |
|---|---|
| Edge-to-Edge | 状态栏/导航栏未遮挡阅读正文、控制栏、BottomSheet |
| Predictive Back | 返回手势优先关闭临时层，不丢失阅读状态 |
| 横竖屏切换 | 阅读位置、主题、字体大小保持不变 |
| 分屏模式 | 翻页手势不误触分屏边界 |
| 通知行为 | 下载进度实时更新，完成通知可点击跳转 |
| 无障碍 | 页面切换、操作结果有恰当的 Live Region 反馈 |

第8章 开发路线图与里程碑（10周）

阶段 时间 核心目标 可交付物/里程碑
Phase 0 W0 环境搭建与基建 ① 前沿技术栈 AGP 9.0 + Gradle 9.3 配置；② 项目骨架与包结构；③ Room 数据库 Schema 生成；④ 构建加速配置（本地镜像 + CI 缓存）
Phase 1 W1-2 核心提取闭环 ① Readability.js 注入与 Prompt 拦截调试；② 四级降级骨架；③ 浏览器 Tab 与提取预览 BottomSheet。里程碑：能提取任意新闻网页并保存。
Phase 2 W3-4 阅读器与文库 ① 阅读器 WebView 渲染 (CSS 主题)；② 字号/字体切换；③ 滚动停止防抖保存；④ 文库列表。里程碑：可完整阅读，切后台续读。
Phase 3 W5-6 小说下载引擎 ① 目录解析 (Jsoup + 聚簇降级)；② WorkManager 批量下载 + 前台通知；③ 失败重试与断点续传。里程碑：成功下载 100 章并离线阅读。
Phase 4 W7-8 体验打磨与性能优化 ① 自毁空闲 WebView；② 20 个 Fixtures 单元测试通过；③ FTS4 全文搜索；④ 引导页与设置页；⑤ 冷启动 ≤ 1.2s。里程碑：内测版发布。
Phase 5 W9-10 上架收尾 ① 无障碍适配；② 应用图标/商店截图；③ 隐私政策上线；④ 更新模块集成；⑤ Google Play 内部测试。里程碑：正式版 APK 签名就绪。

> 路线图使用说明：
> - **MVP（最小可用产品）**：Phase 0–2 + 第 7.5 章 Android 16 基础适配。若 10 周时间紧张，优先保证此范围可发布内测。
> - **可降级项**：Phase 3（小说下载）、Phase 5 中的更新模块可拆分为独立迭代版本，不必阻塞首次内测。
> - **风险缓冲**：Phase 0 预留 2–3 天处理 AGP 9.0 / Otter 3 工具链问题；Phase 4 预留 1 周应对不可预见的 Android 16 适配与性能问题。

第9章 风险登记册与应对预案

风险项 触发条件 应对措施
国产 ROM 杀后台 MIUI/EMUI/ColorOS 锁屏后下载中断 WorkManager 请求 setExpedited() + 绑定前台服务；在设置页引导用户关闭电池优化。
Readability JS 超时 页面 JS 繁重 设置超时 3s，降级至 Level 3 (Jsoup 启发式)。
小说站反爬 返回 403 User-Agent 轮换池 + 自动增加下载延迟 (500ms~2s)。
WebView OOM 低内存设备 阅读器禁用图片加载；独立进程开关作为保底。
KSP 编译失败 Room/KSP 版本不匹配 严格锁定 KSP 2.3.2 与 Room 2.7.0，并在 Phase 0 完成一次全量构建验证。
Android 16 Edge-to-Edge 强制适配遗漏 targetSdk=36 触发强制全面屏 所有页面按 7.5.2 处理 WindowInsets，Phase 2 结束前完成真机验证。
前沿工具链不稳定 AGP 9.0 / AS Otter 3 新特性存在未知问题 Phase 0 预留 2–3 天排错；建立可回退到稳定版本（AGP 8.x + Kotlin 2.0.x）的预案。

第10章 附录：构建与测试指令

10.1 首次环境同步

```bash
# 1. 确保 JDK 17
java -version
# 2. 清理并同步
./gradlew clean build --refresh-dependencies
```

10.2 单元测试护城河

· 位置：src/test/resources/fixtures/*.html (20 个真实网页脱敏样本)。
· 执行：./gradlew test 必须 100% 通过，且提取结果 content.length > 500。

10.3 简历/作品集呈现话术

项目名称：PureRead - 纯净阅读浏览器
核心技术栈：Kotlin 2.3.21, AGP 9.0, Room + KSP, WorkManager, JS 桥接
核心亮点：

1. 混合架构提取引擎：Readability.js + prompt() 拦截，50 站点压测提取成功率 92%。
2. WebView 内存治理：单例预热 + 空闲自毁，彻底解决 OOM 顽疾。
3. 智能目录解析：URL 聚簇启发式算法，无视网站改版。
4. 前沿基建：首批适配 AGP 9.0 + Gradle 9.3 的国产应用，移除 kotlin-android 插件，构建速度提升 30%。

第11章 核心交互逻辑与状态机定义（补充章节）

本章明确定义 UI 事件触发后，从 ViewModel → UseCase → Repository 的状态流转与边界处理规则。

11.1 正文提取交互逻辑（状态机）

触发动作：用户点击浏览器 Tab 中的悬浮 FAB（提取正文）。

状态机流转：

```text
[空闲 IDLE] 
   → (点击 FAB) 
   → [加载中 LOADING]  (FAB 变为进度环，禁用点击，显示 Toast "正在分析页面...")
   → (JS 提取成功 / 或降级算法返回结果)
   → [预览 PREVIEW]  (弹出 BottomSheet，显示提取结果)
       ├─ (点击 "保存") 
       │    → [保存中 SAVING] (按钮变为不可用，显示进度环)
       │        ├─ (Room 写入成功) → [已保存 SAVED] → 关闭 Sheet，Snackbar "已保存" + 跳转阅读器
       │        └─ (写入失败/异常) → [错误 ERROR] → 显示错误 Toast，保留 Sheet 供重试
       ├─ (点击 "编辑") 
       │    → 弹出全屏编辑对话框（可修改标题和正文）
       │        └─ (点击 "确认修改") → 回到 [预览 PREVIEW] 状态（内容已更新）
       ├─ (点击 "放弃" / 下拉关闭 / 点击遮罩) 
       │    → 弹出确认 Dialog："确认放弃本次提取？"
       │        ├─ (确认) → 回到 [空闲 IDLE]，销毁临时数据
       │        └─ (取消) → 停留在 [预览 PREVIEW]
       └─ (提取结果置信度 < 0.6) 
            → [警告 WARNING] (在 BottomSheet 顶部显示黄底警告条："内容可能不完整，建议预览")
            → 用户依然可正常保存（强制保存原始 HTML 保底）

[异常分支]：
- JS 执行超时(>3s) / 降级算法全失败
   → 状态流转至 [错误 ERROR] 
   → 显示 BottomSheet 错误视图："未能提取正文，可尝试保存原始页面"
   → 按钮：["保存原始 HTML" (Level 4 保底)] / ["放弃"]
```

编码约束（防崩溃）：

· 旋转屏幕：ViewModel 中的 _extractState 使用 StateFlow 保存状态，Activity 销毁重建时自动恢复（不丢失预览数据）。
· 重复点击：在 LOADING 和 SAVING 状态下，所有 UI 操作入口必须 setEnabled(false)。

11.2 小说下载交互逻辑（状态机）

触发动作：用户在小说 Tab 输入 URL，点击 "解析"。

目录解析状态机：

```text
[空闲 IDLE]
   → (输入 URL + 点击解析)
   → [解析中 PARSING] (按钮置灰，显示进度环，列表区域显示骨架屏)
       ├─ (Jsoup/聚簇算法返回 >= 5 条链接)
       │    → [解析成功 SUCCESS] (展示章节列表，底部栏显示 "全选 / 开始下载")
       │        └─ (用户手动取消勾选/全选) → 更新底部按钮计数 "开始下载 (N章)"
       └─ (解析结果 < 5 条 / 网络超时)
            → [解析失败 FAILED] (显示错误占位图 + "未识别到章节列表，请检查 URL")
            → 按钮变为 ["重试"] (点击重置状态至 IDLE)
```

批量下载状态机（核心复杂逻辑）：

```text
[空闲 IDLE] (底部按钮显示 "开始下载 (N章)")
   → (点击 "开始下载")
   → [下载中 DOWNLOADING] (底部按钮变为 进度条 + 百分比 + "暂停" 按钮)
       ├─ (用户点击 "暂停")
       │    → [暂停 PAUSED] (WorkManager 调用 `workManager.cancelAllWorkByTag(bookId)`)
       │        → 底部按钮变为 "继续 (N章)"
       │            └─ (点击 "继续") → [下载中 DOWNLOADING] (重新 enqueue 剩余未完成任务)
       ├─ (全部章节状态变为 COMPLETED)
       │    → [已完成 COMPLETED] (底部按钮消失，替换为 "全部下载完成 ✅")
       │        → 通知栏推送成功通知 (含 PendingIntent 跳转阅读器)
       ├─ (部分章节失败，重试耗尽)
       │    → [部分失败 PARTIAL_FAILED] (底部显示 "N 章失败，点击重试")
       │        └─ (点击) → 仅重新 enqueue 状态为 FAILED 的章节
       └─ (下载中退出该页面)
            → 弹出系统 Dialog："下载将在后台继续，可在通知栏查看进度"
                ├─ (点击 "确认") → 关闭页面，WorkManager 继续运行
                └─ (点击 "取消") → 停留在当前页

[异常分支]：网络完全断开
   → 当前正在下载的 Worker 立即标记为 `Result.retry()` (指数退避)
   → 底部进度条显示 ⚠️ "网络不可用，等待恢复..."
   → 一旦网络恢复，WorkManager 自动唤醒继续（无需用户干预）
```

11.3 阅读进度保存逻辑（防高频写入）

触发条件：用户在阅读器中滚动或翻页。

节流策略（终极编码规则）：

```kotlin
// 1. 监听滚动停止 (配合 Handler 防抖)
webView.setOnScrollChangeListener { _, _, _, _, _ ->
    handler.removeCallbacks(saveRunnable)
    handler.postDelayed(saveRunnable, 1500) // 停止滚动 1.5 秒后保存
}

// 2. 仅当进度变化 > 2% 时才执行写入
val currentProgress = (scrollY / maxScrollY * 100).toInt()
if (abs(currentProgress - lastSavedProgress) >= 2) {
    viewModel.saveProgress(currentProgress)
    lastSavedProgress = currentProgress
}

// 3. 强制保存点 (规避丢失)
//    - onPause() 立即保存
//    - onDestroy() 立即保存
```

恢复逻辑：

· Reader 加载完成后 (onPageFinished)，从数据库读取 readProgress，调用 webView.scrollTo(0, (maxScrollY * progress / 100).toInt())。
· 若恢复位置与当前章节内容不符（如章节被删除），则重置为 0，并显示 Toast "已跳转至最新有效位置"。

11.4 离线状态全局交互规则

网络监听：通过 ConnectivityManager.NetworkCallback 监听网络变化，状态注入全局 NetworkStateFlow。

UI 全局响应规则：

场景 用户操作 系统交互逻辑
无网络 点击浏览器 URL 输入框回车 WebView 显示内置离线错误页 (含 onReceivedError)；不弹出系统网络设置。
无网络 点击文库中的文章卡片 若文章已保存（本地存在），直接进入阅读器，不检查网络。
无网络 点击小说下载 "解析" 立即 Toast 提示 "当前无网络，请连接后重试"，不触发 HTTP 请求。
弱网 (超时) 正在提取正文 若 JS 注入 3 秒未返回，立即降级至 Level 3 (Jsoup)，并 Toast 提示 "网络较慢，使用备用解析"。

11.5 数据冲突处理逻辑

去重策略（文章保存）：

· 保存文章前，检查 sourceUrl 是否已在 articles 表中存在。
· 若存在：弹出 Dialog："本文已存在于文库，是否覆盖？" → 用户确认则更新 contentHtml 和 contentText，更新 extractTime；取消则放弃本次提取。

去重策略（小说解析）：

· 解析目录后，若 sourceUrl 对应的 Book 已存在数据库：
  · 读取总章数，对比新解析的章节数。
  · 若有新章节（数量增加），弹出 Dialog："检测到新章节，是否加入下载队列？"
  · 用户确认则仅将缺失的章节插入数据库，状态置为 PENDING。

第12章 高阶交互逻辑与边界场景定义（深挖篇）

针对 PureRead 这类“离线优先、内容长期沉淀”的工具，用户与数据集的互动深度远超普通应用。本章专门定义那些决定应用是“堪用”还是“极致”的隐形交互逻辑。

12.1 外部唤起与深度链接交互逻辑

PureRead 不仅仅是独立应用，更应作为 Android 系统的“内容接收器”。

场景 A：从浏览器/微信/其他应用“分享链接”到 PureRead

· 触发动作：外部应用通过 ACTION_SEND (text/plain) 或 ACTION_VIEW 调起 PureRead。
· 交互逻辑（状态机）：
  1. 应用存活（后台）：MainActivity 的 onNewIntent() 捕获 Intent → 自动切换至底部导航的 浏览器 Tab → 将 URL 填入输入框 → 自动触发 WebView 加载。
  2. 应用未启动：启动页 (Splash) 解析 Intent → 跳过引导页 → 直接进入浏览器 Tab 加载 URL。
  3. 异常处理：若解析到的文本不是有效 URL（如纯文本），弹出 Toast "未识别到有效链接"，停留在当前页面。
· 编码约束：在 AndroidManifest.xml 中声明 <intent-filter>，并在 MainActivity 中封装 handleShareIntent(Intent)。

场景 B：通知栏点击“下载完成”跳转

· 触发动作：小说章节全部下载完成后，系统通知栏弹出。
· 交互逻辑：
  1. 通知携带 PendingIntent，内含 bookId 和 targetPage = "Reader"。
  2. 用户点击通知 → 若 App 在后台，直接启动 ReaderActivity 并加载该书的第一章（或上次阅读位置）。
  3. 若 App 已在前台且处于其他 Tab，先切换到小说书架，再以 ViewPager 动画形式推入阅读器。
· 防冲突规则：若用户正在阅读另一本书，点击通知不应覆盖当前阅读状态。解决方案：弹出 BottomSheet 询问：“检测到新书下载完成，立即阅读？”，用户确认后再跳转。

12.2 文库“批处理模式”交互逻辑

单篇删除太慢，搜索后全选删除必须有明确的防误触设计。

· 进入/退出机制：
  · 长按任意文章/书籍卡片 → 进入多选模式（底部导航栏隐藏，顶部 ActionBar 切换为“已选 0 项”）。
  · 点击“全选” → 选中当前列表所有可见项（若列表已滚动，则仅选当前筛选/搜索结果中的所有项）。
  · 退出：点击返回键或“完成”按钮，取消所有高亮，恢复常态。
· 批量动作（底部浮动工具栏）：
  · 批量删除：点击垃圾桶图标 → 弹出强制确认 Dialog（含详细清单）：“确定删除选中的 12 篇文章吗？此操作不可恢复。” → 用户确认后，Room 执行 DELETE WHERE id IN (ids)。
  · 批量收藏/取消收藏：一键切换，实时刷新 UI，不打断其他操作。
· 异常反馈：若批量删除中部分数据因外键约束失败（如文章对应的 Body 缺失），系统静默忽略错误，保证成功项生效，并在 Snackbar 提示：“已删除 11 项，1 项因数据异常跳过”。

12.3 设置页“实时预览反馈”交互逻辑

用户调整字号或主题时，必须即时看到效果，但重新加载 WebView 会导致阅读进度丢失。

场景：在阅读器中调整主题/字号

· 字号调节（滑块离散 5 档）：
  · 拖动滑块时，实时调用 webView.evaluateJavascript("document.body.style.fontSize = '${size}px'")。
  · 不重载页面，仅注入 CSS 变量，瞬间生效。
· 主题切换（日间/夜间/护眼）：
  · 设置页切换：直接调用 AppCompatDelegate.setDefaultNightMode()，全局 Activity 立即 recreate()（仅设置页自身刷新）。
  · 阅读器内快捷切换（点击控制栏的灯泡图标）：不触发 recreate()，而是执行：
    ```javascript
    document.documentElement.className = 'theme-' + mode;
    ```
    同时动态修改 WebView 的背景色（webView.setBackgroundColor()），实现无闪烁切换。
· 防冲突规则：阅读器内的“即时切换”仅影响当前 WebView，不修改系统全局主题。当用户退出阅读器后，全局主题仍以系统设置为准。

12.4 数据导入/导出（备份与迁移）交互逻辑

用户更换手机或重置系统时，PureRead 的本地数据（数百篇文章、小说）需要无损迁移。

· 触发动作：设置页点击“导出数据”。
· 交互逻辑：
  1. 调用 Android SAF (Storage Access Framework) 弹出系统文件选择器，用户选择目标目录并命名 PureRead_Backup.zip。
  2. 后台使用 CoroutineWorker / 协程，将 databases/、files/（含封面图）压缩为 Zip 文件流，写入用户选择的 URI。
  3. 导出过程中：显示无法取消的前台通知（含进度百分比）。导出完成 → 振动 + Toast "备份已保存至：xxx"。
· 导入逻辑：
  1. 用户点击“导入数据” → 调用 ActivityResultContracts.OpenDocument() 选择 .zip 文件。
  2. 冲突处理（关键）：
     · 系统比对当前数据库与备份文件的 bookId / articleId。
     · 弹出选择框：“检测到 10 本书已存在，如何处理？ [跳过] [覆盖] [保留两者]”
       · 跳过：不导入已存在的书籍。
       · 覆盖：删除当前数据，用备份完全替换。
       · 保留两者：修改导入数据的 ID（自增），保留重复条目。
  3. 导入完成后，App 自动重启（Process.killProcess 或 Intent.ACTION_MAIN 重开），确保所有缓存刷新。

12.5 阅读器内的“查找/搜索”交互逻辑

虽然 UI 规范未强制，但作为阅读工具，在文章中查找关键词是极高的加分项。

· 触发动作：在阅读器控制栏（点击屏幕中部呼出）右上角点击“放大镜”图标。
· 交互逻辑：
  1. 控制栏上方展开一个 内嵌搜索条（非弹窗），输入框获得焦点，键盘自动弹出。
  2. 输入文字时（无防抖，实时响应），调用 webView.findAllAsync(keyword)。
  3. WebView 自动高亮匹配词（黄色背景），底部显示当前匹配结果计数：“1/12”。
  4. 键盘右下角显示“↑”和“↓”箭头，点击后执行 webView.findNext(true) 或 findNext(false)，在匹配项之间跳转。
  5. 点击搜索条右侧的“✕”清除高亮，收起搜索条。
· 失败状态：若匹配结果为 0，输入框下方显示红色文字：“未找到匹配内容”。

12.6 应用版本升级时的数据迁移交互（特殊处理）

当 PureRead 从 v1.0 升级到 v2.0（数据库 Schema 变更）时，必须有明确的状态反馈。

· 启动检测：AppDatabase 的 onUpgrade() 回调被执行。
· 交互逻辑：
  1. 如果迁移耗时极短（<500ms）：静默完成，启动页正常进入。
  2. 如果迁移涉及大表重写（如新增 FTS4 虚拟表，耗时可能 >5s）：
     · 启动页展示全屏进度条，标题：“正在升级数据仓库，请勿退出...”。
     · 使用 yield() 或协程分块迁移，避免 ANR。
     · 迁移完成后：自动跳转主页，并在通知栏/主页横幅提示：“数据已升级至新版，共迁移文章 152 篇”。
· 迁移失败兜底：若 onUpgrade() 抛出异常，捕获后弹出错误 Dialog：“数据迁移失败，请联系开发者”。App 不崩溃，但回退到只读模式（防止数据进一步损坏）。

12.7 特殊交互：提取预览中的“富文本编辑”细化

前面定义了“编辑”入口，现在明确编辑器的交互边界：

· 触发动作：点击预览 BottomSheet 的“编辑”按钮。
· 交互逻辑：
  1. 弹出全屏 DialogFragment，内含一个 纯文本输入框（EditText）（不是富文本编辑器，防止引入复杂依赖）。
  2. 输入框默认加载提取出的纯文本（contentText）。
  3. 用户修改完成后，点击“预览修改” → BottomSheet 中的预览区实时刷新显示修改后的纯文本（保留分段）。
  4. 点击“保存” → 修改后的文本覆盖原有 contentText，且自动重新生成 contentHtml（仅做 \n → <p> 转换）。
  5. 限制条件：此功能仅用于修正明显的 OCR 错字或段落丢失，不提供插入图片或加粗等排版功能。

第13章 系统健壮性与底层防御协议（深水区）

针对一款离线优先、长期运行、完全本地闭环的阅读工具，最可怕的不是功能缺失，而是 “数据静默损坏”、“JS桥接版本撕裂” 和 “磁盘I/O竞争”。

13.1 JS-Native 桥接协议版本控制（契约锁）

痛点：当 assets/readability.js 后续升级（如 Mozilla 官方更新算法），旧版 Native 解析类（Gson 映射）可能因字段变更导致 JSONException，直接导致提取崩溃。

防御逻辑（强制契约校验）：

· 协议头注入：JS 端在返回的 JSON 顶部强制添加 "protocolVersion": "1.0"。
· Native 端校验：onJsPrompt 拦截后，第一步不是解码，而是剥离版本号。
  · 若版本号 != 当前支持版本（如 1.0），直接拒绝解析，降级至 Level 3（Jsoup），并在日志中标记 "JS mismatch, fallback to heuristic"。
  · 若版本号匹配，才进行 Gson 反序列化。
· 编码约束：ReadabilityResult 数据类中增加 @SerializedName("protocolVersion") val version: String，解析失败时不抛异常，返回 null 触发降级。

13.2 下载引擎的事务性磁盘 I/O（防损坏策略）

痛点：章节下载时，若在 write to file 瞬间被系统强杀（内存不足或用户手动清后台），会留下半截损坏的 .txt 文件。用户下次打开该章节时，可能读到乱码或直接 FileNotFoundException。

原子写入防御协议：

· 双阶段提交：
  1. 临时文件写入：下载的文本先写入 {chapterId}.tmp。
  2. 完整性校验：校验字节数与 HTTP Content-Length 是否匹配（若服务器返回）。若不匹配，删除 .tmp，标记 FAILED。
  3. 原子重命名：校验通过，执行 File.renameTo({chapterId}.txt)。renameTo 在 Unix/Android 底层是原子操作，即使此时被杀，系统要么保留完整的旧文件，要么保留完整的新文件，绝不会产生半截文件。
· Room 状态与文件系统解耦：数据库 ChapterEntity.status = COMPLETED 必须在文件原子重命名 之后 才更新。若数据库先更新但文件写入失败，用户会看到“已完成”但点开空白——这是绝对禁止的。

13.3 内置离线诊断日志系统（零第三方 SDK 的底气）

痛点：为了隐私合规，PureRead 零第三方统计/Crash SDK。但当用户反馈“提取某网站失败”时，开发者无日志可查。

轻量级环形缓冲区（Ring Buffer）方案：

· 内存存储：在 Application 中维护一个 ArrayDeque<String>(maxSize = 200)，存储关键操作日志（含时间戳）。
· 写入策略：
  · 记录：提取成功/失败、下载开始/结束、降级触发、WorkManager 重试。
  · 不记录：URL 全路径（防隐私泄露），仅记录域名和操作类型（如 [Extract] medium.com -> SUCCESS）。
· 用户导出路径（设置页）：
  · 点击“导出诊断日志” → 弹出系统分享 Sheet，将内存队列内容打包成 pure_read_log.txt 分享给开发者。
  · 自动清空：App 冷启动超过 7 天的日志自动清空，防止内存占用膨胀。

13.4 冷启动初始化风暴治理（防 ANR）

痛点：Application 启动时若同时初始化 Koin、WorkManager、预热 WebView、读数据库，低端设备极易 ANR。

阶段化调度策略（异步锚点）：

· 同步关键（阻塞主线程）：仅 AppExecutors、Koin.startKoin()（必须同步）。
· 延迟非关键（锚定 IdleHandler）：
  · WebView 预热（离屏实例）：在 onCreate 中通过 Looper.myQueue().addIdleHandler 触发，确保首帧渲染完成后再创建。
  · WorkManager 初始化：使用 WorkManager.initialize(context, configuration) 异步初始化，不依赖自动初始化 ContentProvider（需在 Manifest 中禁用 androidx.work.impl.background.systemjob.SystemJobService 的自动注入）。
· 数据库预加载：不读取全表，仅在进入“文库” Tab 时才触发首次查询，利用 Flow 的冷流特性。

13.5 内存压力下的主动防御（onTrimMemory）

痛点：用户长期在后台保留 PureRead，系统内存不足时，WebView 可能已被 LMK 回收但 Activity 未重建，导致回前台时白屏或 DeadObjectException。

精细响应策略：

· TRIM_MEMORY_RUNNING_LOW（系统警告内存紧张）：
  · 执行 WebViewCacheManager.clearAllWebViewCache() 清除 HTTP 缓存。
  · 调用 BitmapPool.get().clearMemory()（Coil 图片池）。
· TRIM_MEMORY_UI_HIDDEN（用户切到后台）：
  · 强制触发阅读进度保存（补刀防御，防止 onPause 被某些国产 ROM 跳过）。
  · 调用 WebView.onPause() 暂停所有 JavaScript 定时器，降低 CPU 占用。

13.6 跨页面状态同步防撕裂（Multi-Window 与旋转）

痛点：用户在分屏模式或旋转屏幕时，ReaderActivity 重建，但当前的 ChapterEntity 可能已因下载完成被更新。

同步契约（StateFlow + 唯一 ID 快照）：

· 进入阅读器时拍快照：ReaderViewModel 持有 currentChapterId 和 snapshotContent（内存中的文本副本）。
· 渲染优先级：WebView 显示 必须 优先使用内存快照（即使用户在阅读期间数据库被其他进程更改，也保持当前阅读内容的绝对稳定）。
· 退出时回写：onPause 时，若用户修改了书签或进度，将内存快照的数据回写 Room，而不是从 Room 读取后回写，避免用旧数据覆盖新数据。

13.7 本地时钟依赖防御（时间戳回退）

痛点：用户手动修改系统时间，导致 extractTime、downloadTime 出现未来时间或 1970 年。

防御策略：

· 时间戳获取：统一使用 System.currentTimeMillis() 并封装为 TimeProvider 接口（便于单元测试 Mock）。
· 单调时钟校验（可选）：若检测到新时间戳 < 上次记录的时间戳，自动修正为 lastRecordTime + 1000，防止列表排序混乱。
· 数据库索引：extractTime 不设唯一索引，仅设普通索引 @Index，防止时间回退导致的插入冲突。

第14章 PureRead 日系编程规范与实施标准（匠人协议）

14.1 命名规范（变量・函数命名规则）

遵循“名は体を表す”（顾名思义）的最高准则，拒绝一切歧义。

类型 规则 正例 (Good) 反例 (Bad)
局部变量 名词性，必须带单位/数据类型后缀 articleId, bookTitleText, retryCountInt id, title, count
函数/方法 动词原形 + 名词，必须阐明副作用 fetchArticleContent(), saveProgressOrThrow() processData(), doIt()
布尔变量/函数 is / has / can / should 前缀 isDownloadingEnabled, hasReadPermission downloading, readable
常量（伴生对象） 全大写 + 下划线，含业务前缀 MAX_RETRY_COUNT = 3, DEFAULT_TIMEOUT_MS = 5000L MAX = 3, TIMEOUT
类/接口 名词性，严禁使用 Impl 后缀（改用动词+er/or） ArticleExtractor, ChapterDownloader ArticleExtractorImpl, ChapterManager

14.2 注释与文档规范（注释・文档彻底化）

日系编程最显著的标志：不仅写“做了什么”，必须写“为什么要这么做”（Why）。Kotlin 使用 KDoc。

类级 KDoc（必须包含业务职责与线程安全说明）：

```kotlin
/**
 * [ChapterDownloadWorker] 负责下载属于 [BookEntity] 的单个章节。
 *
 * ## 业务注意事项
 * - 若下载过程中网络断开，将采用指数退避策略自动重试，最多 3 次。
 * - 文件写入采用两阶段提交（.tmp → .txt），防止数据损坏。
 *
 * ## 线程安全性
 * - 此 Worker 继承自 [CoroutineWorker]，在 [Dispatchers.IO] 上同步执行。
 * - 所有 [ChapterDao] 的更新均在 [Room] 事务中进行。
 *
 * @param bookId 目标书籍 ID（不允许 ≤ 0）
 * @param chapterUrl 下载源的完整 URL（必须）
 * @throws IllegalArgumentException 当 bookId 无效时抛出
 * @see BookEntity
 * @see WorkManager
 */
```

方法级注释（复杂逻辑必须标注“前置条件”与“后置条件”）：

```kotlin
fun saveExtractedArticle(article: ArticleEntity, body: String) {
    // 前置条件: article.sourceUrl 必须唯一，且尚未存在于数据库中
    // 后置条件: 成功时 [articles] 表新增一条记录，[article_bodies] 关联正文
    // 副作用: 保存成功后 [extractTime] 更新为当前时间
    
    require(article.sourceUrl.isNotBlank()) { "sourceUrl must not be blank" }
    // ... 实现
}
```

14.3 异常处理的严格分层设计（异常分层防御）

日系编程严禁“吞掉异常”（空 Catch）。所有异常分为三类，强制在顶层（ViewModel）统一处理，不在底层（Repository/UseCase）随意弹 Toast。

异常层级 定义 处理策略（底层） UI 表现（顶层）
致命异常 数据库损坏、ROM 磁盘满 抛出 PureReadFatalException，无法恢复 显示崩溃对话框 + 强制退出
业务异常 URL 无效、提取置信度过低 返回 Result.failure(BusinessError) 显示黄色警告条 + 引导用户操作
瞬时异常 网络超时、I/O 重试失败 自动重试（指数退避），耗尽后标记失败 显示“重试”按钮（不自动弹 Toast 干扰阅读）

编码强制：所有 catch (e: Exception) 块中 必须 包含 Log.e(TAG, "Critical error: ${e.message}", e)，否则 Code Review 直接驳回。

14.4 契约式设计（契约式编程：前置/后置条件）

借助 Kotlin 的 require、check、assert 函数，在运行期将“隐式假设”显式化。

数据层契约（Repository 接口）：

```kotlin
interface IBookRepository {
    /**
     * 根据 ID 获取书籍。
     * @throws IllegalArgumentException 当 id <= 0 时
     * @return 若不存在则返回 null（返回值必须检查）
     */
    suspend fun fetchBookById(id: Long): BookEntity?
}

// 实现类中的契约检查
override suspend fun fetchBookById(id: Long): BookEntity? {
    require(id > 0) { "Book ID must be positive: $id" } // 契约违反即崩溃（尽早暴露 Bug）
    return dao.selectById(id)
}
```

UI 层契约（Fragment 接收参数）：

```kotlin
class ReaderFragment : Fragment() {
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 验证启动时传入的参数是否正确（非致命时设置替代值）
        val chapterId = arguments?.getLong("chapterId") ?: run {
            Log.w(TAG, "Chapter ID missing, redirecting to default library.")
            // 替代处理：返回文库
        }
    }
}
```

14.5 日志输出的分级粒度（日志输出五阶段）

不使用 Log.v（Verbose）污染生产环境。统一使用封装后的 PureLog 工具类，且生产环境 Release 包仅输出 Warn 和 Error。

级别 对象 输出条件
Debug UI 绘制状态流转 仅 Debug 构建时
Info 用户操作（提取开始、下载完成） 常时输出（用于诊断日志收集）
Warn 降级触发（JS 超时→Level 3 回退） 常时输出
Error 网络错误、JSON 解析失败 常时输出（立即提示反馈）
Fatal 不可恢复的崩溃前夕 常时输出 + 触发优雅关闭/崩溃上报流程（严禁直接调用 System.exit()）

日志格式强制规则：[ClassSimpleName] [Operation] | Result | Duration(ms)

· 正例：[ReadabilityJSBridge] extract | SUCCESS | 342ms
· 反例：[Main] done

14.6 测试驱动文化（单元测试的日系量化）

· 「正常 10 条，异常 30 条」原则：每个核心业务类（Extractor、Parser）的单元测试必须覆盖 10 种正常输入和 30 种异常输入（包括空字符串、超长字符串、特殊 Unicode 字符）。
· Fixture 命名：testData/{函数名}_{输入条件}_{预期结果}.html
  · 例：extract_medium_article_success.html / extract_empty_page_failure.html
· 判定标准（分阶段，避免 10 周单人开发被覆盖率压垮）：
  · Phase 1–2：核心类（Extractor、Parser、UrlUtils）分支覆盖率 ≥ 60%。
  · Phase 4：整体分支覆盖率 ≥ 80%，核心业务类 ≥ 85%。
  · 90% 作为长期目标，但不强制在 10 周内达成；CI 先设置 Warn 阈值，待稳定后再设为 Fail。

14.7 本规范在 PureRead 开发中的落地执行细则

为确保不会成为空头文件，下列硬性要求在 Phase 1 第一天即嵌入 Gradle 配置：

1. 静态检查：配置 ktlint 或 detekt 规则集，违反命名规范或注释缺失时，./gradlew build 直接 FAILED。
2. Kotlin 编译器警告提升：在 app/build.gradle.kts 中增加：
   ```kotlin
   tasks.withType<KotlinCompile>().configureEach {
       compilerOptions {
           allWarningsAsErrors.set(true)  // 日系编程中，警告亦视为错误
           freeCompilerArgs.add("-Xexplicit-api-mode=strict") // 强制显式公开 API
       }
   }
   ```
3. 代码审查（单人开发也需自我审查）：每次 Commit 前，强制运行 git diff HEAD^ 审查自己的变更，并填写 [Self-Review] 清单（涵盖单元测试是否补充、KDoc 是否更新）。

第15章 PureRead 混沌工程与故障注入协议（实战演练篇）

本章不再讨论“理想情况”，而是定义当 Android 系统处于极限或异常状态时，PureRead 必须执行的确定性恢复动作。

15.1 磁盘空间耗尽（ENOSPC）防御协议

模拟场景：用户设备存储空间不足，下载小说时 FileOutputStream.write() 抛出 IOException("No space left on device")。

PureRead 原子响应策略：

1. 捕获与回滚：
   · 捕获 ENOSPC 异常，立即调用 .tmp 文件的 delete()（防止残留碎片）。
   · 不回滚数据库状态：将 ChapterEntity.status 设置为 FAILED，errorMsg 写入 "存储空间不足"。
2. 全局广播阻断：
   · 触发 DiskFullEvent 发送至全局 EventBus / Flow。
   · 所有 Repository 监听该事件，在磁盘满期间，自动跳过任何 INSERT 或 UPDATE 操作（防止 Room 抛异常导致崩溃），直接返回 Result.failure(DiskFullError)。
3. UI 显式反馈：
   · 顶部弹出不可忽略的红色横幅：“存储空间不足，已暂停下载与保存，请清理后重试。”
   · 点击横幅跳转至系统“设置 → 存储”界面。
4. 恢复探测：
   · 监听 onLowMemory 和 onTrimMemory 无法探测磁盘释放，改为在每次用户回到 App 前台时，尝试写入一个 1KB 的测试文件。
   · 若写入成功，自动取消红色横幅，恢复所有正常功能。

编码实现骨架：

```kotlin
class StorageManager(private val context: Context) {
    fun isDiskWritable(): Boolean {
        return try {
            val file = File(context.cacheDir, ".write_test")
            file.writeBytes(ByteArray(1024))
            file.delete()
            true
        } catch (e: IOException) {
            false
        }
    }
}
```

15.2 运行时权限被用户撤销（针对已声明权限）

PureRead 仅声明 INTERNET 和 FOREGROUND_SERVICE。虽然 Android 系统很少动态撤销普通权限，但在“权限管理”页面手动关闭仍有可能。

防御逻辑：

· INTERNET 被撤销：所有网络请求（Retrofit / WebView 加载）瞬间失败。
  · WebView 检测到 onReceivedError(errorCode = ERROR_UNKNOWN)，直接显示内置离线页，不弹 Toast。
  · 下载引擎：WorkManager 收到网络失败后，进入 Result.retry() 等待，不标记为 FAILED。
· FOREGROUND_SERVICE 被撤销：Android 系统会主动移除前台服务。
  · WorkManager 捕获到 ForegroundServiceStartNotAllowedException，降级为普通后台任务（WorkManager 2.8+ 自动处理）。
  · 仅记录日志，不干扰用户阅读。

15.3 Room 数据库损坏与恢复策略（终极防御）

场景：App 运行中系统突然断电，SQLite 数据库 WAL 文件损坏，导致 Room 启动时抛出 IllegalStateException("Database corruption")。

PureRead 恢复流程（三段式）：

1. 自动尝试恢复：
   · 在 AppDatabase 的 create() 回调中，开启 setWriteAheadLoggingEnabled(false) 并使用 OpenHelperFactory 尝试挂载。
   · 若 Room 检测到损坏，它会自动尝试附加 .backup 或删除 WAL 文件。若这步失败：
2. 用户介入恢复：
   · 启动页捕获异常，进入 “紧急恢复模式”。
   · 显示 Dialog：“检测到数据文件异常，是否尝试从最近备份恢复？（上次备份：2026-06-15）”
     · 用户确认：导入之前通过 SAF 导出的 .zip 备份文件。
     · 用户拒绝：重命名当前的 pure_read.db 为 pure_read.db.broken，创建全新的空数据库。确保 App 能正常启动，但历史数据需通过“导入”功能手动恢复。
3. 安全兜底：若当前设备从未导出过备份，显示全屏提示：“数据无法恢复，请联系开发者”，并提供“重置应用”按钮（清除所有数据并重启）。

15.4 WebView 独立进程死亡后的无感重生

PureRead 架构中，WebView 运行在默认进程（若启用独立进程选项）。

场景：系统内存极度不足，直接杀死了 WebView 进程，但我们的 UI 进程（Activity）仍存活。

防御协议（WebViewClient 钩子）：

· 当调用 webView.loadUrl() 时，若底层渲染进程已死，系统会回调 onRenderProcessUnresponsive()。
· 响应动作：不显示“等待/关闭”对话框（干扰用户），而是：
  1. 调用 webView.destroy()。
  2. 从 WebViewPreheatManager 申请一个新的预热实例（如果预热池空了，则 new WebView() 同步创建）。
  3. 重新加载当前 URL（或重新注入 HTML）。
  4. 在控制层显示一个微小的 Toast 提示：“渲染已恢复（自动）”，仅持续 1 秒。

15.5 WorkManager 任务队列的“孤儿任务”清理

场景：用户下载了 1000 章小说，下载到第 500 章时，用户手动清除了应用数据（或卸载重装），但 WorkManager 的持久化任务（WorkContinuation）仍残留在系统 JobScheduler 中。

启动时检查协议：

· 在 Application.onCreate 中，调用 workManager.cancelAllWorkByTag("pure_read_legacy") 清除所有旧标签。
· 对比本地数据库：若 Book.status = DOWNLOADING，但数据库中查不到对应的活跃 WorkInfo，则自动将书籍状态回退为 PAUSED，并在“小说”Tab 顶部显示提示条：“检测到未完成的下载任务，点击继续”。

15.6 日系编程视角下的“混沌测试清单”（供开发者自检）

在 Phase 4（体验打磨）阶段，必须手持真机完成以下破坏性测试，并记录应用反应：

测试操作 期望的 PureRead 行为 验证点
下载中途断网 自动重试，10 分钟后显示“网络异常”标记 数据库无半截垃圾数据
正在提取时旋转屏幕 10 次 BottomSheet 不消失，内容不重置 ViewModel 使用 StateFlow 保存状态
存储空间填满（制造磁盘满） 红色横幅提示，提取/下载自动暂停 不崩溃，不无限弹 Toast
系统时间回退至 1970 年 文章列表依然按正常顺序排列 时间戳防御使用 max(currentTimestamp, lastRecordTime + 1000)
分屏模式下打开阅读器 翻页手势依然生效，不误触分屏边界 左右 1/3 点击区域与分屏手柄无冲突
Android 16 真机首次启动 无崩溃、无白屏、启动时间 ≤ 1.2s 验证 edge-to-edge 无遮挡、冷启动路径正常
Android 16 阅读器返回手势 优先关闭控制层/BottomSheet，不直接退出 验证 Predictive Back 注册正确
Android 16 横竖屏切换阅读 阅读位置、主题、字体保持不变 验证 ViewModel + SavedStateHandle 恢复
Android 16 下载通知 进度实时更新，完成通知可点击跳转 验证通知 cooldown/group 策略生效

第16章 极致性能优化（2026前沿型方案）

哲学：优化不是“让分数变高”，而是在有限资源下让应用更快、更稳、更省。启动耗时超过2秒，用户流失率增加30%；PureRead的目标是冷启动 ≤ 1.2秒，阅读器秒开，滑动不掉帧。

16.1 启动速度优化（冷启动攻坚）

冷启动涉及进程创建、类加载、资源初始化、UI渲染等20+环节。

1. 启动视觉优化（零白屏）
在AndroidManifest.xml中为MainActivity设置启动主题：

```xml
<style name="LaunchTheme" parent="Theme.AppCompat.NoActionBar">
    <item name="android:windowBackground">@drawable/launch_background</item>
    <item name="android:windowFullscreen">true</item>
</style>
```

纯色或极简矢量图背景，用户感知启动速度立即提升。

2. 延迟初始化（IdleHandler策略）
将非关键组件移出Application.onCreate()，使用IdleHandler在首帧渲染后执行：

```kotlin
Looper.myQueue().addIdleHandler {
    // 非关键初始化：WebView预热池、WorkManager、日志系统
    initializeNonCriticalComponents()
    false // 只执行一次
}
```

实测主线程耗时从520ms降至120ms。

3. Jetpack Startup库
合并多个ContentProvider为单一入口，减少IPC通信次数。在AndroidManifest.xml中声明初始化器，自动管理依赖顺序。

4. MultiDex优化
通过multiDexKeepProguard规则将核心启动类保留在主Dex，非核心类拆分至二级Dex。

5. 类加载加速
启用R8代码压缩，移除未使用的类、方法与字段；在gradle.properties中启用android.enableDexingArtifactTransform.desugaring=false减少转换开销。

16.2 WebView性能优化（核心战场）

WebView首次初始化需加载Chromium内核，耗时200-500ms。

1. WebView预初始化（预热池）
应用启动空闲时提前初始化WebView实例。注意：必须使用Application Context避免内存泄漏：

```kotlin
class WebViewPool(private val context: Application) {
    private var cachedWebView: WebView? = null

    fun preInit() {
        if (cachedWebView == null) {
            cachedWebView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                loadUrl("about:blank")
            }
        }
    }
}
```

2. 独立进程隔离
将WebView放置在独立进程运行，减轻对主进程影响，避免内存泄漏和崩溃波及主界面。在AndroidManifest.xml中配置：

```xml
<activity
    android:name=".ui.browser.BrowserActivity"
    android:process=":webview_process" />
```

3. 缓存策略

· HTTP缓存：配置Cache-Control响应头，启用setCacheMode(WebSettings.LOAD_DEFAULT)
· 本地资源拦截：通过shouldInterceptRequest拦截请求，返回本地缓存资源

4. 硬件加速
在AndroidManifest.xml中启用硬件加速，同时针对低端设备可动态关闭：

```xml
<application android:hardwareAccelerated="true">
```

```kotlin
if (isHighPerfDevice) {
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
}
```

5. DNS与网络优化
确保WebView加载的域名与App其他网络请求域名一致，减少DNS解析耗时。使用HTTP/2协议与CDN加速。

6. 渲染进程崩溃自动恢复
监听onRenderProcessUnresponsive，自动销毁并重建WebView实例。

16.3 UI渲染性能优化

1. 布局层级简化
复杂布局增加渲染时间。使用ConstraintLayout减少嵌套，用ViewStub延迟加载非首屏视图。

2. RecyclerView滑动优化

· 复用ViewHolder，避免在onBindViewHolder中执行耗时操作
· 使用setHasFixedSize(true)固定RecyclerView尺寸
· 图片加载使用Coil的内存/磁盘二级缓存

3. 过度绘制消除
开发者选项中开启“调试GPU过度绘制”，将红色区域降至最低。

4. 异步布局加载
使用AsyncLayoutInflater实现非阻塞加载。

16.4 内存优化（防OOM）

1. 内存泄漏检测（LeakCanary）
LeakCanary自动化检测内存泄漏，生成堆转储分析报告。Release包自动禁用：

```kotlin
dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

2. Bitmap内存管理
使用Coil的BitmapPool自动复用；大图使用inSampleSize采样加载；由 Coil 管理 Bitmap 生命周期，禁止手动调用 recycle()。

3. onTrimMemory分级响应

```kotlin
override fun onTrimMemory(level: Int) {
    when (level) {
        TRIM_MEMORY_RUNNING_LOW -> {
            WebViewCacheManager.clearAllCache()
            Coil.imageLoader(context).memoryCache?.clear()
        }
        TRIM_MEMORY_UI_HIDDEN -> {
            // 用户切到后台，暂停WebView
        }
    }
}
```

4. WebView销毁彻底化
onDestroy中调用webView.destroy()，移除所有JavaScript接口，清空缓存。

16.5 数据库查询优化

1. 索引策略
高频查询字段建立索引。在Entity上使用@Index注解：

```kotlin
@Entity(indices = [Index(value = ["sourceUrl"]), Index(value = ["extractTime"])])
data class ArticleEntity(...)
```

注意：索引会加快SELECT但减慢INSERT/UPDATE。

2. FTS4全文搜索
对article_bodies.content_text建立FTS4虚拟表，比LIKE '%keyword%'快数个数量级。

3. 事务批量操作
批量插入使用@Transaction包裹。

4. 异步查询
所有数据库操作使用Coroutine在Dispatchers.IO执行，不阻塞主线程。

16.6 APK体积瘦身

1. R8全量优化
AGP 9.0新版R8统一代码优化与资源压缩，体积缩减可超50%。启用：

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
}
```

2. 图片格式优化
使用WebP替代PNG，体积小25%-35%；使用VectorDrawable替代多分辨率位图。

3. 依赖精简
审查build.gradle.kts，移除未使用依赖；使用implementation而非api避免传递依赖泄漏。

4. Native库裁剪
abiFilters仅保留armeabi-v7a和arm64-v8a：

```kotlin
ndk { abiFilters("armeabi-v7a", "arm64-v8a") }
```

16.7 网络与电池优化

1. 请求合并与批量
小说下载使用WorkManager批量调度，避免频繁网络唤醒。

2. 指数退避重试
WorkManager自动实现指数退避，避免失败时频繁重试耗尽电量。

3. 唤醒锁管理
通过Play管理中心监控wake lock指标，使用WorkManager替代手动唤醒锁管理。

16.8 性能监控与调优工具链

1. Android Profiler
实时监控CPU、内存、网络、能耗，Heap Dump分析内存泄漏。

2. R8配置分析器
Android Studio内置，识别过于宽泛的ProGuard保留规则。

3. 自定义性能埋点

```kotlin
object PerfTracker {
    fun trackLaunchTime() { /* 记录冷启动耗时 */ }
    fun trackWebViewLoad(url: String) { /* 记录页面加载耗时 */ }
    fun trackExtractTime() { /* 记录正文提取耗时 */ }
}
```

仅Debug模式启用，Release包通过环形缓冲区记录关键指标。

附录A：构建加速配置指南

针对 本地开发 与 GitHub Actions CI 两种环境的差异化配置方案。

A.1 核心差异

环境 网络位置 访问 google() / mavenCentral() 访问国内镜像 推荐加速手段
本地开发 中国 🇨🇳 极慢（跨境） 极快 ✅ 替换为国内镜像
GitHub Actions 海外 🇺🇸 极快（本地网络）✅ 极慢（跨境） 使用缓存 + 保留官方源

A.2 本地开发配置（使用镜像）

修改 ~/.gradle/init.gradle（全局生效，不污染项目源码）：

```gradle
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
    }
}
```

或修改项目 settings.gradle.kts（仅影响当前项目）：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
    }
}
```

A.3 GitHub Actions 配置（使用缓存 + 官方源）

保留官方仓库源（settings.gradle.kts）：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 不要添加任何国内镜像地址
    }
}
```

在 Workflow 中启用 Gradle 缓存：

```yaml
- name: Setup Gradle
  uses: gradle/actions/setup-gradle@v3
  with:
    gradle-version: 9.3.0   # 与 wrapper 匹配
```

首次构建会从海外高速下载依赖，后续构建直接从 GitHub 缓存恢复，大幅提速。

A.4 完整 Workflow 示例

```yaml
name: Android CI

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 1

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle (with cache)
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: 9.3.0

      - name: Build
        run: ./gradlew assembleDebug --no-daemon
```

A.5 总结

· 本地 → 换镜像（加速下载）
· CI → 不换镜像 + 启用缓存（避免跨境访问）

按此区分配置，即可在两种环境中均获得最优构建性能。

附录B：更新模块 UI/UX 与开发技术规范

本章定义 PureRead 应用内更新模块的视觉设计、交互逻辑、状态机、后端接口及具体实现方案。

B.1 设计原则与 Token

更新模块完全遵循 PureRead 全局 UI/UX 规范（第6章），无额外设计偏差。

色彩（复用全局色彩体系）：

色值角色 日间模式 夜间模式
主背景 #FAF8F5 #1A1A1A
卡片/弹窗背景 #FFFFFF #252525
标题文字 #2C2C2C #C8C8C8
次要文字 #757575 #9E9E9E
强调色 #1A73E8 #8AB4F8
错误/超时 #D93025 #F28B82
成功/快速 #2E7D32 #81C784

字体：

· 标题：18sp, System Bold
· 正文：16sp, System Regular
· 辅助说明：13sp, System Regular
· 按钮文字：14sp, System Medium

圆角与间距：

· 弹窗容器：16dp
· 按钮：10dp
· 内容外边距：16dp

B.2 UI 组件详细规格

B.2.1 设置页入口

在“设置”页的列表内放置“检查更新”项：

```
┌────────────────────────────────────────────┐
│  [图标容器]  检查更新              [→]     │
│              当前版本 1.0.0                │
└────────────────────────────────────────────┘
```

· 左侧：40×40dp 圆角容器，背景 强调色@10%，内含矢量图标（ic_update，24dp）
· 中间：标题 16sp，副标题 13sp（从 BuildConfig.VERSION_NAME 读取）
· 右侧：chevron_right 图标，24dp
· 点击效果：Ripple（强调色 @15%）

B.2.2 版本更新弹窗 (BottomSheet)

```
┌──────────────────────────────────────────┐
│         发现新版本 v1.1.0 (22MB)        │
│  ──────────────────────────────────────   │
│  更新日志：                               │
│  · 新增阅读统计功能                       │
│  · 修复部分网页提取失败问题               │
│                                          │
│  [ 软件内更新 ]    (强调色填充按钮)      │
│  [ 浏览器下载 ]    (描边按钮)             │
│  [ 稍后提醒 ]      (文字按钮)             │
└──────────────────────────────────────────┘
```

· 遮罩层：#000000 @50%
· 弹窗顶部圆角 16dp
· 点击遮罩外部等同于“稍后提醒”
· “浏览器下载”直接调用系统浏览器打开 GitHub Release 页面

B.2.3 下载进度与状态

· 待下载：按钮显示“下载 (12.3MB)”
· 下载中：按钮替换为 LinearProgressIndicator + 百分比文字 + “取消”文字按钮
· 完成：按钮变为“安装”，点击安装 APK
· 失败：按钮变为“重试”，同时 Snackbar 提示失败原因

通知栏：

· 渠道 ID: CHANNEL_UPDATES
· 标题：“PureRead 更新下载中”
· 内容：进度百分比
· 取消按钮：点击后取消 WorkManager 任务
· 完成后：通知内容变为“下载完成”，点击通知拉起安装界面

B.3 状态机定义

B.3.1 版本检查状态机

状态 UI 表现 触发事件
IDLE 列表项正常显示 用户进入设置页
CHECKING 右侧旋转进度，标题灰显 用户点击“检查更新”
UP_TO_DATE Snackbar 提示“已是最新” 服务器返回当前版本等于本地
UPDATE_AVAILABLE 弹出更新 BottomSheet 服务器返回更高版本
ERROR Snackbar 提示失败 + 重试 网络异常或解析错误

B.3.2 下载状态机

状态 UI 表现 权限状态
IDLE 按钮“下载 (XX MB)” 已授权
PERMISSION_NEEDED 引导对话框 未授权
DOWNLOADING 进度条 + 百分比 + 取消按钮 -
COMPLETED 按钮“安装” -
FAILED 按钮“重试” -

B.4 技术实现

B.4.1 版本检查 API（GitHub Releases）

```kotlin
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val changelog: String,
    val publishedAt: Long
)

interface UpdateChecker {
    suspend fun checkUpdate(): Result<ReleaseInfo>
}
```

```kotlin
class GitHubUpdateChecker(
    private val client: OkHttpClient,
    private val owner: String,
    private val repo: String
) : UpdateChecker {

    override suspend fun checkUpdate(): Result<ReleaseInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty body")
                val json = JSONObject(body)
                val assets = json.getJSONArray("assets")
                val apkAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?: throw IOException("No APK asset")
                Result.success(
                    ReleaseInfo(
                        versionCode = parseVersionCode(json.getString("tag_name")),
                        versionName = json.getString("tag_name"),
                        apkUrl = apkAsset.getString("browser_download_url"),
                        apkSize = apkAsset.getLong("size"),
                        changelog = json.optString("body", "无更新说明"),
                        publishedAt = json.getString("published_at")
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

B.4.2 下载管理 (WorkManager)

```kotlin
class ApkDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val apkUrl = inputData.getString(KEY_APK_URL) ?: return Result.failure()
        val outputFile = File(applicationContext.filesDir, "downloads/update.apk")
        outputFile.parentFile?.mkdirs()

        return try {
            downloadWithProgress(apkUrl, outputFile) { progress ->
                setForeground(createNotification(progress))
                setProgress(workDataOf(PROGRESS to progress))
            }
            Result.success(workDataOf(APK_PATH to outputFile.absolutePath))
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure()
        }
    }

    companion object {
        const val KEY_APK_URL = "apk_url"
        const val APK_PATH = "apk_path"
        const val PROGRESS = "progress"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "updates"
        const val MAX_RETRIES = 3
    }
}
```

B.4.3 安装 APK (FileProvider)

```kotlin
fun installApk(context: Context, apkPath: String) {
    val file = File(apkPath)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
```

AndroidManifest.xml 配置：

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

res/xml/file_paths.xml：

```xml
<paths>
    <files-path name="apk" path="downloads/" />
</paths>
```

B.4.4 权限处理

```kotlin
class InstallPermissionHelper(private val activity: Activity) {

    /**
     * 仅检查安装未知来源权限。
     * APK 下载到应用内部 filesDir，不申请 READ/WRITE_EXTERNAL_STORAGE。
     */
    fun ensurePermissions(onResult: (Boolean) -> Unit) {
        if (!canInstallFromUnknownSources()) {
            showInstallPermissionDialog()
            onResult(false)
        } else {
            onResult(true)
        }
    }

    private fun canInstallFromUnknownSources(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.packageManager.canRequestPackageInstalls()
        } else true
    }

    private fun showInstallPermissionDialog() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("需要“安装未知应用”权限")
            .setMessage("为了安装新版本，请授权 PureRead 安装未知来源应用的权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${activity.packageName}"))
                activity.startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
```

B.4.5 设置页集成

```kotlin
class SettingsFragment : PreferenceFragmentCompat() {
    private val updateManager: UpdateManager by lazy {
        UpdateManager.Builder(requireContext())
            .setChecker(GitHubUpdateChecker(OkHttpClient(), "owner", "PureRead"))
            .build()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        findPreference<Preference>("check_update")?.apply {
            summary = "当前版本 ${BuildConfig.VERSION_NAME}"
            setOnPreferenceClickListener {
                updateManager.checkManually()
                true
            }
        }
    }
}
```

B.4.6 模块化 Builder 模式

```kotlin
class UpdateManager private constructor(
    private val context: Context,
    private val checker: UpdateChecker,
    private val workManager: WorkManager,
    private val notificationManager: NotificationManagerCompat
) {

    class Builder(private val context: Context) {
        private var checker: UpdateChecker? = null

        fun setChecker(checker: UpdateChecker) = apply { this.checker = checker }

        fun build(): UpdateManager {
            return UpdateManager(
                context,
                checker ?: GitHubUpdateChecker(OkHttpClient(), "user", "repo"),
                WorkManager.getInstance(context),
                NotificationManagerCompat.from(context)
            )
        }
    }

    fun checkManually() {
        // 显示 BottomSheet 等逻辑
    }

    private fun startDownload(url: String, size: Long) {
        val request = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
            .setInputData(workDataOf(ApkDownloadWorker.KEY_APK_URL to url))
            .addTag("update")
            .build()
        workManager.enqueue(request)
    }
}
```

B.5 测试策略

· 单元测试：验证 GitHubUpdateChecker 对不同 JSON 结构的解析正确性；版本号比较逻辑。
· UI 测试：利用 Espresso 测试 BottomSheet 的显示、权限对话框、下载进度条变化。
· 真机测试：覆盖 Android 8.0-14 各版本，重点测试 FileProvider 兼容性及通知行为。
· 异常测试：网络中断、API 限流、存储空间不足、下载文件损坏等情况。

B.6 兼容性与注意事项

· 所有异步操作统一使用 Kotlin Coroutines + Flow。
· 通知渠道必须在 Application.onCreate() 中创建，避免 8.0 以上设备崩溃。
· APK 下载目录优先使用应用内部存储，避免存储权限问题（Android 10+）。
· 动态检查 canRequestPackageInstalls()，仅在用户明确选择“软件内更新”时才检测。
· 更新弹窗需处理配置变更（如旋转）保持状态，推荐使用 DialogFragment 或 BottomSheetDialogFragment。

结语（总结）

至此，PureRead 的完整技术规格书已涵盖：

维度 对应章节
战略层 第1章（市场定位与竞品差异）
基建层 第2章（AGP 9.0 + Gradle 9.3 前沿环境）+ 附录A（构建加速）
架构层 第3章（MVVM + Repository 离线优先）
数据层 第4章（Room + FTS4 + KSP）
实现层 第5章（JS 桥接提取 + WorkManager 下载）
视觉层 第6章（沉浸式阅读 UI 设计规范）
交互层 第11章（状态机）+ 第12章（边界场景定义）
健壮层 第13章（原子 I/O + 内存防御）+ 第15章（混沌工程）
匠人层 第14章（日系编程与契约式开发）
性能层 第16章（全链路性能优化）
交付层 附录B（更新模块）+ 第8章（10周路线图）

建议阅读顺序：

1. 开发前通读第2章（技术栈）→ 第4章（数据库）→ 第16章（性能）
2. 编码时参考第14章（日系编程规范）
3. 测试时对照第15章（混沌工程协议）
4. 上架前检查第7章（合规）+ 附录B（更新模块）

签署状态：✅ 技术规格书 v3.0 定稿，即刻进入 Phase 0 环境搭建阶段。

若在执行过程中遇到国产 ROM 的 WorkManager 唤醒延迟或特定网页的 CORS 策略拦截，请随时携带 Logcat 日志返回进行实时同步。祝编码顺利！🚀
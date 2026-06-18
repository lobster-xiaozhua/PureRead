# PureRead

一款基于系统 WebView 的纯净阅读浏览器。它不做内容生产，只做内容的“蒸馏器”——将杂乱的网页提纯为优雅的阅读版面，并将连载小说转化为可离线翻阅的本地文库。

> 核心原则：本地主权、零内置源、容灾优先、触觉确定性。

## 功能特性

- **正文提取引擎**：Readability.js 注入 + prompt() 拦截，配合四级降级策略（JS 算法 → 站点适配器 → Jsoup 启发式 → 原始 HTML 保底）。
- **离线小说下载**：链接聚簇启发式目录解析 + WorkManager 批量下载 + 断点续传。
- **沉浸式阅读器**：日间/夜间/护眼主题、点击/滚动翻页、滚动停止防抖保存进度。
- **本地文库管理**：Room + FTS4 全文搜索、批量操作、数据导入导出。
- **隐私优先**：所有数据仅存于应用私有目录，零第三方 SDK，不预置任何书源。

## 技术栈

| 层级 | 选型 | 版本 |
|---|---|---|
| 语言 | Kotlin | 2.3.21 |
| 构建 | AGP + Gradle + KSP | 9.0.0 / 9.3.0 / 2.3.2 |
| 架构 | MVVM + Repository + Flow + Koin | — |
| UI | XML Views + ViewBinding | — |
| 本地存储 | Room (SQLite) + FTS4 | 2.7.0 |
| 网络 | OkHttp + Retrofit | 4.12.0 / 2.11.0 |
| 图片 | Coil | 3.0.4 |
| 后台任务 | WorkManager | 2.10.0 |
| HTML 解析 | Jsoup | 1.18.3 |

> 需要 Android Studio Otter 3 Feature Drop (2025.2.3) 或更高版本，以及 JDK 17。

## 项目结构

```text
com.pureread/
├── App.kt
├── core/           # Koin 模块、网络、仓库接口、工具类
├── data/           # Room 实体/DAO、远程提取与下载
├── ui/             # Activity/Fragment/ViewModel
├── update/         # 应用内更新
└── widget/         # 自定义 View
```

## 开发文档

| 文档 | 说明 |
|---|---|
| [Frontier_Android_Stack_Handbook_v1.0.md](./Frontier_Android_Stack_Handbook_v1.0.md) | 完整技术规格书（架构、数据层、UI/UX、规范、性能、测试等） |

## 构建与测试

```bash
# 环境要求：JDK 17
java -version

# 清理并构建 Debug APK
./gradlew clean assembleDebug

# 运行单元测试
./gradlew test
```

## 许可证

Apache-2.0（待补充 LICENSE 文件）

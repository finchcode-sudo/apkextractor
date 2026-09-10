# 牧歌App工具箱 (APKExtractor) 项目文档

> 本文档随代码同步维护。如果你改了架构、依赖或版本号，请顺手更新这里，
> 避免像上一版文档那样和实际代码脱节（写着 Activity/Fragment，实际早就全是 Compose 了）。

## 项目概述

**牧歌App工具箱**（原 APKExtractor）是一个 Android 应用工具箱，用于查询、导出（提取）本地已安装的应用，并提供签名查看、清单查看、应用变更记录、静默卸载等能力。全部 UI 基于 **Jetpack Compose** 构建。

| 项目属性 | 值 |
|---------|-----|
| 应用ID（正式） | `info.muge.appshare` |
| 应用ID（当前构建配置） | `info.muge.appshare.selfbuild`（debug 额外加 `.kit` 后缀） |
| 当前版本 | 5.0.2 (versionCode: 370) |
| 最低SDK | API 24 (Android 7.0) |
| 目标SDK / 编译SDK | API 36 |
| Kotlin 版本 | 2.3.10 |
| AGP 版本 | 9.2.0-alpha02（尚在 alpha，注意跟进后续升级） |
| Gradle 版本 | 9.7.1 |

---

## 项目结构（实际目录，2026-09 校对）

```
apkextractor/
├── app/
│   ├── src/main/
│   │   ├── java/info/muge/appshare/
│   │   │   ├── ComposeMainActivity.kt   # 唯一 Activity，托管 Compose Navigation3 导航
│   │   │   ├── Global.kt                # 全局工具方法（导出/安装/分享等）
│   │   │   ├── Constants.kt             # 常量定义
│   │   │   ├── DisplayItem.kt           # 列表展示用的数据封装
│   │   │   ├── MyApplication.kt         # Application 类
│   │   │   ├── data/                    # 轻量数据仓库（见下方“数据存储”）
│   │   │   │   ├── AppChangeRepository.kt   # 应用变更记录
│   │   │   │   ├── AppGroup.kt / AppGroupRepository.kt  # 分组
│   │   │   │   └── ExportStatsManager.kt    # 导出统计
│   │   │   ├── items/                   # AppItem / FileItem / ImportItem 等数据模型
│   │   │   ├── tasks/                   # 导出/导入/搜索/哈希等异步任务
│   │   │   ├── ui/
│   │   │   │   ├── screens/             # 各个 Compose 页面（详见下方“页面与导航”）
│   │   │   │   ├── components/          # 可复用 Compose 组件（图表、索引条等）
│   │   │   │   ├── dialogs/             # 各类 Compose 弹窗
│   │   │   │   └── theme/               # 主题、配色（含 ThemeState）
│   │   │   └── utils/                   # 工具类（签名解析、AXML 解析、Shizuku 卸载等）
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/gradle-wrapper.properties
```

**没有** `activities/`、`fragments/`、`adapters/` 目录——项目早已全面迁移到 Compose，如果你在别的地方（issue、旧文档、AI 生成的总结）看到这类描述，那是过时信息。

---

## 页面与导航

导航基于 **Navigation3**（`androidx.navigation3:navigation3-runtime` / `navigation3-ui`），入口在 `ComposeMainActivity.kt`。主要路由：

- `LaunchRoute` — 启动/权限检查页
- `MainRoute` — 应用列表主页（`AppListScreen.kt` + `AppListViewModel.kt`）
- `DetailRoute` / `DetailUriRoute` / `ExternalDetailRoute` — 已安装应用 / URI 打开 / 外部 APK 的详情页（`AppDetailScreen.kt`，含签名、权限、组件、SO 库、Manifest 等子页签，位于 `ui/screens/appdetail/`）
- `AppChangeRoute` — 应用变更记录（`AppChangeScreen.kt`）
- `ThemeSettingsRoute` — 主题设置（`ThemeSettingsScreen.kt`）
- 其余：`SettingsScreen.kt`、`StatisticsScreen.kt`（+`StatisticsViewModel.kt`，图表相关组件在 `ui/screens/statistics/`）

主题状态由 `ui/theme` 下的 `ThemeState`（`StateFlow`）统一管理暗色/动态取色/种子色/AMOLED 黑等设置。

---

## 技术栈

### 核心依赖（摘自 `app/build.gradle.kts`，如有变动请同步）

| 库 | 版本 | 用途 |
|---|---|---|
| androidx.appcompat | 1.7.1 | 兼容支持 |
| com.google.android.material | 1.13.0 | Material 组件（少量非 Compose 场景） |
| androidx.core-ktx | 1.17.0 | Kotlin 扩展 |
| androidx.emoji2 | 1.6.0 | Emoji 兼容 |
| com.belerweb:pinyin4j | 2.5.1 | 拼音排序/搜索 |
| com.github.getActivity:XXPermissions | 28.0 | 运行时权限申请 |
| androidx.documentfile | 1.1.0 | SAF 文件操作 |

### Compose 相关

| 库 | 版本 |
|---|---|
| androidx.compose (BOM) | 2026.02.00 |
| androidx.compose.material3 | 随 BOM |
| androidx.activity:activity-compose | 1.12.4 |
| androidx.navigation3 (runtime / ui) | 1.0.1 |
| androidx.lifecycle (runtime-compose / viewmodel-compose) | 2.10.0 |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.10.0 |
| io.coil-kt.coil3:coil-compose | 3.0.0 |
| com.materialkolor:material-kolor | 4.1.1 |

### 静默卸载（Shizuku）

| 库 | 版本 |
|---|---|
| dev.rikka.shizuku:api | 13.1.5 |
| dev.rikka.shizuku:provider | 13.1.5 |
| org.lsposed.hiddenapibypass:hiddenapibypass | 6.1 |

实现见 `utils/ShizukuUninstaller.kt`：用户需要另外安装并激活 Shizuku（ADB 配对或 Root）。`android.content.pm.IPackageInstaller` / `IPackageManager` 属于隐藏 framework 类，不在标准 SDK 编译用的 `android.jar` 里，因此该文件**全程用 `Class.forName` + 反射**访问它们，没有在代码里直接声明这两个类型（否则编译期会报 `Unresolved reference`）。没有 Shizuku 权限时会自动回退到系统的 `ACTION_DELETE` 卸载确认框。

---

## 数据存储现状（已知技术债）

`AppChangeRepository` 和 `ExportStatsManager` 目前都是 **SharedPreferences + JSON 全量序列化**：

- 应用变更记录上限约 5000 条，导出统计上限约 200 条；
- 每次读写都要把整个 JSON 数组反序列化/重新序列化一遍；
- 数据量大时会有明显的读写开销，未来应迁移到 Room/SQLite。

这是已知问题，暂未处理，不要误以为是 bug——只是还没排上优先级。

---

## 已知问题 / TODO

按目前评估的优先级排列（欢迎认领）：

1. **AXMLPrinter 的资源 ID 名称未解析**（`utils/AXMLPrinter.kt` 的 `getResourceIdName`）：目前只返回空字符串，Manifest 查看里的资源引用（`@0x7f...`）没有名字。要修好需要实现一个 `resources.arsc` 解析器（package/type/key 字符串池 + resource map），工作量较大，尚未排期。
2. **数据存储迁移到 Room**：见上一节。
3. **i18n 不完整**：Compose 代码里散落着不少硬编码中文字符串（比如"应用变更记录"、"正在导出..."之类），没有走 `strings.xml`，建议逐步收拢。
4. **`AppListViewModel` 职责过重**：冷启动 / 秒开缓存 / 后台刷新 / 变更回填 / 节流等逻辑集中在一个方法里，建议拆分成多个更小的用例（use case）类。
5. **AGP 处于 alpha 版本**（9.2.0-alpha02）：功能验证没问题，但 alpha 版本的行为可能在后续正式版中变化，升级时留意 release notes。

---

## 变更记录

- 2026-09：文档整体重写，与实际 Compose 架构、依赖版本对齐；修复 `ChartCaptureUtil` 中 FileProvider authority 与 Manifest 不一致的问题（原来用 `${packageName}.fileprovider`，实际声明的是固定字符串 `info.muge.appshare.FileProvider`）；Gradle wrapper 从 RC 版（9.4.0-rc-1）切换到稳定版 9.7.1。

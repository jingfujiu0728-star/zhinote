# ZhiNote · 水滴空间

在原有 V1 项目上升级的 Kotlin / Jetpack Compose Android 应用。当前版本 2.0.1（versionCode 3），applicationId 和签名保持不变。业务数据保存在 Room，设置保存在 DataStore，水滴物理与数据库分别管理。最新修复见 [AI 空内容修复记录](docs/v2/ai-fix/修复报告.md)，V2 功能与桌面说明见 [V2 升级报告](docs/v2/升级报告.md)。

## 运行与构建

用 Android Studio 打开本目录，使用 JDK 17 和 Android SDK 36。最低 Android 8.0（API 26）；本次连接设备报告 Android 17 / API 37 / HyperOS OS4.0。

```powershell
cd D:\AI\Projects\ZhiNote
.\tools\build.ps1
# 只构建 APK
.\tools\build.ps1 -Tasks assembleDebug
# 真机业务与界面测试
.\tools\device-tests.ps1 -Serial 185502e1 -Class 'com.example.zhinote.AppDeviceTest,com.example.zhinote.RepositoryDeviceTest'
# 真机短时性能采样
.\tools\device-tests.ps1 -Serial 185502e1 -Class com.example.zhinote.PerformanceDeviceTest
# 真实强制关闭后持久化验证
.\tools\v2-persistence.ps1 -Serial 185502e1
# 对已经构建的 APK 和源码进行交付打包
.\tools\package.ps1
```

标准环境也可以使用 `gradlew.bat assembleDebug testDebugUnitTest lintDebug`（macOS/Linux 使用 `./gradlew`）。`tools/build.ps1` 优先使用本项目 `.toolchain` 下的独立 JDK/SDK/Gradle，并把 Gradle 缓存和 Android 用户数据留在工作目录。网络受限时，请配置可用的 Maven/Gradle 代理；本机代理只写入被忽略的 `.toolchain/gradle-home/gradle.properties`。

APK：`app/build/outputs/apk/debug/app-debug.apk`，开发调试签名，非商店发布版本。首次构建下载依赖，后续可使用缓存。Debug 构建包体和帧性能不能代表优化后的 release 版本。

依赖锁定：AGP 8.10.1、Gradle 8.11.1、Kotlin / Compose Compiler 2.1.21、KSP 2.1.21-2.0.1、Compose BOM 2025.12.01、Room 2.7.1、DataStore 1.1.7、Navigation 2.9.0、WorkManager 2.10.1。测试使用 AndroidX Test 1.7.0 / Espresso 3.7.0，避免旧 Espresso 在 API 37 上调用已移除的 InputManager 方法。Gradle 发行包配置了 SHA-256 校验。

SDK 采用已验证的 API 36 编译与目标级别，在 API 37 真机运行验证。编译/目标级别可通过 `-Pzhinote.compileSdk=... -Pzhinote.targetSdk=...` 修改；升级 SDK 时应同时核对 AGP 支持范围。本机仓库中的 API 37 SDK 使用 `android-37.0` 等带小版本的包名，不能直接使用旧命名 `platforms;android-37`。

当前包名为 `com.example.zhinote`，覆盖升级必须保持该 applicationId 和原有签名。本机原签名保留在被忽略的 `.toolchain/android-user/debug.keystore`，共享源码包不含私钥。在另一台机器直接构建产生的 debug 签名可能不同，不能据此覆盖已安装版本。

## 功能

- 首页水滴自由漂浮、碰撞避让、触摸涟漪与扩散、详情展开、拖动保存独立归一化布局；笔记和待办也提供列表。
- 笔记创建、编辑、逐次输入自动保存、长文本、分类、标签、收藏、搜索、软删除与恢复。
- 待办优先级、截止日期、提醒、日/周/月重复、完成历史、撤销完成；双击待办直接完成，事务提交后播放 240ms 消散，并提供约 4 秒撤销。
- 黄金角星群布局、椭圆软边界与自然碰撞避让；各水滴独立点击和拖动。
- OpenAI-compatible API 配置、Keystore 加密、连接测试、模型切换、AI 笔记/待办/自动识别与确认预览。
- 标准 Android 桌面小部件：今日概览、快捷添加、待办清单、今日环形进度。
- 全局搜索包含标题、正文、描述、标签及分类；笔记按创建/更新时间排序，待办按今日、即将到期、逾期、高优先级、已完成筛选。
- 日历月/周/日视图与当天记录；浅色、深色、跟随系统主题；独立动画开关、减少动态效果、低性能模式。
- JSON 备份导出、结构和版本校验、确认替换、事务恢复；10 MB 文件上限。当前备份包含内容、回收站、分类和布局，不包含主题设置。

创建内容后自动进入水滴空间；点击笔记只展开正文。待办的消散不删除内容；在待办“已完成”中撤销。删除进入回收站，永久删除必须再次确认。新内容的每次编辑交给串行持久化队列；未命名内容作为可恢复草稿显示。

普通模式默认显示 40 滴，可在设置选择 20/40/60 滴；低性能模式最多 20 滴、约 30Hz 更新且减少粒子，列表保持完整。Android 10+ 系统报告中度或更高热压力时暂时进入低性能模式，温度恢复后恢复用户选择。水滴标题在 Canvas 绘制，同时提供单独的按钮语义与完整标题，触摸目标至少 48dp。系统关闭动画或应用启用减少动态效果时暂停漂浮；页面后台暂停模拟。物理计算在 Default 调度器运行，Room 不在主线程操作。

## 提醒与数据

申请网络、通知、开机接收与按需精确闹钟权限，无需全盘存储权限。备份使用 Android 文件选择器。API 33+ 必须允许通知；API 31+ 精确闹钟不可用时使用 WorkManager **可延迟**提醒，设置页会说明降级。设置提醒时间后才安排提醒，未设置提醒的截止日期不隐式生成通知。启动、系统重启或安装更新后重新调度；完成、回收站删除与修改时间会取消旧调度。

数据库版本 1 和 2 的 Room schema 在 `app/schemas`。V2 使用显式 Migration 1→2，只增加默认空字符串的笔记摘要列，禁止 destructive migration。JSON 备份格式升级到 2，继续接受版本 1 的备份；备份不包含 AI 密钥。恢复前完成全部解析与引用/范围/重复检查，写事务失败会回滚，不能以半份备份覆盖现有内容。

## 目录

`data/local`：Room 实体、DAO、数据库；`data/repository`：业务事务与设置；`data/backup`：JSON 编解码与文件导入导出；`domain`：重复日期规则；`drop/engine`：独立物理引擎；`drop/drawing`：Canvas、涟漪、粒子与触摸；`ui`：ViewModel、导航、页面、编辑与详情；`reminders`：通知、闹钟、WorkManager 与开机恢复。

V2 测试结果见 [测试报告](docs/v2/测试报告.md)，性能数据见 [V2 性能记录](docs/v2/性能记录.md)，使用说明与兼容性边界见 [升级报告](docs/v2/升级报告.md)。`docs/测试记录.md` 和 `docs/性能记录.md` 保留 V1 历史记录。实际执行结果以记录和原始输出为准，120Hz 为优化目标，不保证所有场景 120fps。

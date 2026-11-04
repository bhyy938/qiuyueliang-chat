# 邱岳靓 — Android 聊天 App

一个基于 DeepSeek API 的单人文字聊天 App。人设是"嘴贫但走心"的内蒙高中女生 **邱岳靓**，采用 SSE 流式输出，带全局长期记忆、会话摘要、主动消息和上下文超限告警。

> 交付物为 **Android Studio 工程源码**（本机无 JDK / Android SDK / Gradle，无法替你打包 APK）。用 Android Studio 打开即可编译运行。

## 技术栈
- Kotlin **2.0.21** + Jetpack Compose（BOM 2024.09.03，Material3）
- OkHttp 4.12.0（手写 SSE 解析）、kotlinx-serialization-json 1.7.3
- kotlinx-coroutines-android 1.9.0、navigation-compose 2.8.1、lifecycle-viewmodel-compose 2.8.6
- Gradle 8.7 + AGP 8.6.1，compileSdk/targetSdk 35，minSdk 26

## 运行步骤
1. 安装 **Android Studio**（自带 JDK 17），以及 SDK Platform 35。
2. 打开本目录 LiuruyiChat（File → Open）。
3. 首次同步会自动下载 Gradle 并补全 gradle/wrapper/gradle-wrapper.jar。
   - 若用命令行：先执行 gradle wrapper --gradle-version 8.7 生成 wrapper jar，再 gradlew.bat assembleDebug（Windows）。
   - Android Studio 会自动生成 local.properties（内含 sdk.dir），无需手写。
4. 连接设备或启动模拟器，点 Run。

## 配置 API Key
1. 首次进入后到 **设置** 页，粘贴你的 DeepSeek API Key（sk-...）。
2. Key 用 **Android Keystore（AES/GCM）加密**后只存在本机 SharedPreferences，不写进聊天文件、不进日志。
3. 模型名默认 deepseek-flash，在 Config.kt 里改为你账号实际可用的模型名即可。

## 已实现功能
- SSE 流式输出（打字机效果），支持"停止"按钮，停止后保留半截并标记"已停止"。
- **上下文预算**：软阈值 4500 token（触发摘要）、硬阈值 7000 token（弹窗告警）。
  - 告警弹窗：继续 = 带全部旧原文（全原文模式，上限 60000 token）；取消 = 不发送、保留输入。
  - 摘要只发生于"未摘要消息 ≥ 600 token"时，摘要目标 ≤ 800 token；已摘要消息由 coveredUntilMessageId 覆盖。
- **全局长期记忆**：跨会话，新会话自动加载并计入上下文预算。
- **主动消息**：每天最多 5 条、每次打开最多 1 条，支持 SKIP；命中锚点词（考试/放假/约定等）或 30% 概率触发，跨天重置计数。
- 会话列表 + 多会话切换 + 原子本地存储（files/liuruyi/，损坏自动备份为 .bak 重建）。

## 提示词
三段提示词（附录 A 系统人设 / B 摘要 / C 主动消息）全部原样写在 Prompts.kt，未做任何改写。

## 项目结构
（缩进表示层级）

    app/src/main/java/com/example/liuruyi/
      MainActivity.kt / Config.kt / Prompts.kt
      ui/              ChatScreen, SessionListScreen, SettingsScreen, MainViewModel, theme/
      ui/components/   MessageBubble, InputBar, TokenBar, HardLimitDialog
      data/            Models, LocalStore, SessionRepository, MemoryRepository, KeystoreHelper, JsonUtil
      network/         DeepSeekClient, SseParser
      domain/          ContextBuilder, Summarizer, ProactiveManager, ChatOrchestrator
      util/            TokenEstimator, TimeProvider, Logger, Ids
    app/src/test/      ContextBuilderTest, StorageTest, TokenEstimatorTest

## 测试

    gradlew.bat testDebugUnitTest      (Windows)
    ./gradlew testDebugUnitTest        (Linux/macOS)

## 说明
- 该 App 为**单用户、文字聊天**，无登录、无本地模型、无图片等媒体。
- 需要网络访问 https://api.deepseek.com

## 如何拿到能装的 APK（本环境无法直接替你打包）

这台机器没有 JDK / Android SDK，且网络受限，无法下载编译工具链，所以**这里不能直接产出 APK**。任选其一：

- **A. 本地一键打包**：在装了 JDK 17 + Android SDK 的 Windows 电脑上，双击根目录的 build_apk.bat，生成的 APK 在 app/build/outputs/apk/debug/app-debug.apk。
- **B. 云端打包（推荐，电脑什么都不用装）**：把整个文件夹推到 GitHub 仓库（已内置 .github/workflows/build-apk.yml），仓库的 Actions 会自动编译，产物在 Artifacts 里下载。
- **C. Android Studio**：打开项目后菜单 Build → Build Bundle(s)/APK(s) → Build APK(s)。

拿到 APK 后：传到手机 → 点开 → 允许"安装未知来源应用" → 安装完成。

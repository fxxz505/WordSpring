# WordSpring

WordSpring 是一款本地优先的 Android 背单词应用。它将单词学习、间隔复习、
词书管理和学习统计放在一台设备上完成，不需要账号、云端服务或网络同步。

当前版本：`1.0.0`

包名：`com.wordspring.app`
系统要求：Android 8.0（API 26）及以上

## 功能一览

- **滑动词卡学习**：卡片正面展示英文，点击翻面查看中文释义、词性和例句；
  向右代表“不记得”、向上代表“模糊”、向左代表“记得”。
- **发音与翻卡**：翻开卡片时会朗读一次；背面提供发音入口，可使用系统英语
  Text-to-Speech 引擎重复播放。
- **按遗忘曲线复习**：新词量按词书完成日期自动安排，复习量由学习状态和到期
  记录自动计算；首页分别展示当天应学新词和应复习词数。
- **内置词书与导入**：包含 WordSpring Complete、CET-4、CET-6、高考英语等
  离线词书；也支持导入 UTF-8 CSV 自定义词集。词库来源与授权说明见
  [ECDICT 导入说明](docs/ecdict-import.md)。
- **本地数据工具**：提供学习统计、错词本、搜索、学习提醒，以及学习数据的备份
  与恢复。

## 下载 APK

当前工作区的可安装 Debug APK：

- [下载 WordSpring v1.0.0 Debug APK](https://github.com/fxxz505/WordSpring/releases/download/v1.0.0/WordSpring-v1.0.0-debug.apk)

该文件是本地构建产物，按约定不提交到 Git；因此从 Git 仓库克隆项目后，请先按
下面的“生成 APK”步骤重新生成。发布到 GitHub Releases、网盘或应用分发平台时，
应上传 `dist/` 中的 APK，并在发布页提供下载链接。

> 安装前请在手机的系统设置中允许本次文件来源安装未知应用。首次安装 Debug 包
> 时 Android 可能显示安全提示；这是本地调试签名的正常行为。

## 更新 APK

更新版本时请使用同一套签名证书构建，并保持 `applicationId`
`com.wordspring.app` 不变。Android 只有在新 APK 的 `versionCode` 更高、签名相同
时才会覆盖安装并保留学习数据。

1. 在 `app/build.gradle.kts` 提升 `versionCode` 和 `versionName`。
2. 运行下方的打包脚本，生成带版本号的 APK。
3. 将新 APK 上传到已选择的发布渠道，并在发布说明中列出版本号、更新内容和
   SHA-256 校验值。
4. 用户下载新包后直接安装；不要先卸载旧版，以免清除本地学习数据。

调试包使用机器上的默认 Debug 签名，适合模拟器和本机测试。面向他人持续更新时，
请配置并妥善备份一个独立的 Release 签名证书；如果更换证书，Android 无法将新包
覆盖安装到旧包上。

## 生成 APK

### 一键生成当前 Debug APK

在项目根目录的 PowerShell 中执行：

```powershell
.\tools\build-debug-apk.ps1
```

脚本会执行 `:app:assembleDebug`，然后将输出复制到：

```text
dist/WordSpring-v<versionName>-debug.apk
```

也可以直接运行 Gradle：

```powershell
.\gradlew.bat :app:assembleDebug
```

原始构建输出位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 开发与验证

项目基于 Kotlin、Jetpack Compose、Room、DataStore、WorkManager 和 Hilt 构建。
推荐使用 Android Studio 打开项目；Gradle 使用 Java 17 字节码目标，并在项目配置中
指定 Android Studio JBR。

```powershell
# 运行本地单元测试
.\gradlew.bat :app:testDebugUnitTest

# 构建可安装 Debug APK
.\gradlew.bat :app:assembleDebug
```

主要目录：

```text
app/src/main/java/       Kotlin 应用代码
app/src/main/assets/     内置词书与授权文件
app/src/test/            单元测试
app/src/androidTest/     设备与模拟器测试
docs/                    词库与项目说明
tools/                   词库转换和 APK 打包工具
```

## 数据与隐私

- 学习记录保存在设备本地的 Room 数据库中。
- 系统发音使用设备安装的 Text-to-Speech 引擎。
- 备份与恢复由用户主动选择，不会自动上传数据。
- 卸载应用会删除本地学习数据；更新安装通常会保留数据。


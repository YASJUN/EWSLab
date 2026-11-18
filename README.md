# EWSLab

**简体中文** | [日本語](README.ja.md) | [English](README.en.md)

EWSLab 是用于接收和分析日本模拟音频 EWS（緊急警報放送）信号的 Android 应用，附带离线 JMA 海啸报文音频生成器。支持 Android 10（API 29）及以上版本。

当前版本为 **0.1.0 实验版**。App 界面为简体中文，可与原版 EWSParserApp 同时安装。

## 免责声明

- 本项目用于协议研究、软件开发和离线测试，不是官方警报服务。防灾和避难信息请以气象厅及当地政府的正式发布为准。
- 生成器产生的音频可能触发兼容的 EWS 接收设备。请在可控环境中测试，避免使他人误认为正在发生真实警报；不要在未经授权的情况下接入广播、无线发射设备或公共扩音系统。
- 使用者应遵守所在地关于无线电、广播及警报发布的规定。MIT 许可证授予的软件使用权不包含相关行政许可。
- 目前已完成软件解码测试，尚未完成 Android 真机运行、扬声器播放到手机麦克风的接收测试，或实体 EWS 接收器起动测试；不保证特定设备的接收或起动效果。
- 本项目为独立项目，不代表 NHK、总务省、JMA 或上游作者的立场。软件以“现状”提供，担保与责任限制详见 [MIT 许可证](LICENSE)。

## 下载与安装

- [v0.1.0 预发布版：APK、离线生成器和 SHA-256 校验文件](https://github.com/YASJUN/EWSLab/releases/tag/v0.1.0)
- Android 安装包：`EWSLab-0.1.0.apk`，应用 ID 为 `xyz.ckkpd.ewsparserapp.lab`。
- Release 中的 APK 使用专用发布密钥签名。安装时按 Android 提示允许浏览器或文件管理器安装应用。
- 使用麦克风接收时需要录音权限；导入 WAV 文件不需要录音权限。
- 下载后可用附件 `SHA256SUMS.txt` 核对 APK 和生成器。签名、构建与发布步骤见[发布说明](docs/releasing.md)。

## 使用

1. 从上述 Release 下载并安装 `EWSLab-0.1.0.apk`。
2. 点击“导入 WAV 文件”，选择生成器导出的 PCM16 WAV；或点击“开始接收”，授予麦克风权限并播放测试音频。
3. 查看单块候选／连续确认、地域、编码时间、两个保护位和六个原始 16 位字。
4. 点击停止或切换到其他应用后，接收会话结束。历史记录只保存在内存中；旋转屏幕也会结束会话并重建界面。

时间按 JST 显示为“年份末位 · 月 · 日 · 小时”。EWS 不传完整年份、分钟或秒。整点附近的奇偶保护块可能相差一小时；跨午夜时，日期也会相应变化。

在浏览器中打开 `generator/ews_tsunami_player.html` 即可离线使用生成器，无需安装依赖或启动服务器。默认使用发送时的当前 JST 时间；重现历史报文时，可将时间来源切换为“电文 ReportDateTime（历史重现）”，并核对页面上的“实际编码时间”。

## 目录

- `core/`：无 Android 依赖的 Java 接收核心，严格帧解析、连续确认、多相位解调、流式 WAV 读取。
- `app/`：原生 Android 界面、前台麦克风、系统文件选择器、输入电平和诊断。
- `generator/`：单文件 HTML 音频生成器。
- `scripts/`：构建脚本和生成器／接收器联合测试。
- [协议说明](docs/protocol.md)：协议、算法及来源。
- [验证记录](docs/verification.md)：测试结果、已知限制和设备验收方法。
- [隐私说明](docs/privacy.md)：权限和数据处理。
- [发布说明](docs/releasing.md)：发布构建、签名和校验步骤。

## 构建和测试

需要 JDK 17 或 21、Android SDK 平台 `android-37.0` 和 Build Tools 36.0.0；生成器联合测试另需 Node.js 22。项目使用 Gradle Wrapper 8.14.3、AGP 8.13.0 和 JUnit 4.13.2，Wrapper 配置包含分发包的 SHA-256 校验值。

在 Android Studio 中打开项目并设置 SDK，或设置 `ANDROID_HOME`，或在不提交的 `local.properties` 中填写 `sdk.dir`。

Windows 下运行：

```powershell
.\scripts\build.ps1
```

脚本执行核心测试、生成器联合测试、lint 和 APK 构建，默认生成本地调试包 `verification/local/EWSLab-debug.apk`。源码路径含非 ASCII 字符时，中间文件会写入临时目录；也可用 `-BuildRoot` 指定输出位置。输出目录不能设为源码目录。

Linux、macOS 或 Windows 的 ASCII 路径下可直接使用 Wrapper：

```sh
./gradlew :core:test :core:verifyGenerator :app:lintDebug :app:assembleDebug
```

仅运行核心测试，无需 Node.js：

```sh
./gradlew :core:test
```

联合测试通过 `node scripts/generator-check.cjs` 生成 26 份 WAV，并由接收核心逐字段检查其中的 242 个主体。生成文件位于 `verification/local/`，不纳入 Git。

当前构建和 lint 已通过，lint 启用 `warningsAsErrors`，无 baseline。AGP 8.13.0 与 SDK 37.0 的组合会产生兼容性警告；Windows 非 ASCII 路径使用的 `android.overridePathCheck` 也会产生提示。完整测试结果和工具链限制见[验证记录](docs/verification.md)。

## 接收功能

- 麦克风输入按每次实际读取的采样数处理。
- 八相位搜索，使用 640/1024 Hz Goertzel 和相对能量判定；支持小数采样点位时钟及坏块后重新同步。
- 三个固定码、字段标志、53 个法定地域码、日期／年时码字均须有效。
- 同一物理块的多个相位结果先聚合去重，再做连续确认；坏块／长间隔／类型地域变化／不合理时间跳变会重置确认。
- 单块结果标为候选，连续收到符合条件的主体后标为确认。EWS 没有 CRC，部分合法码字之间的误码无法检出。
- 结束块的 92 位静音继续推进时钟；麦克风与 WAV 共用同一核心。
- App 不自动外放警报，不联网，不后台常驻，不保存麦克风录音。

## Git 与来源

EWSLab 基于 [private-yusuke/EWSParserApp](https://github.com/private-yusuke/EWSParserApp) 重写，保留了上游的 15 个原始提交。原始基线 `e3473c48feb8cd6e94665232fb8f5c448d0ff2a5` 可通过 `upstream-reference` 分支和 `upstream-reference-e3473c4` 标签查看。

项目仓库：[YASJUN/EWSLab](https://github.com/YASJUN/EWSLab)。GitHub Actions 提供手动触发的构建验证，不自动发布版本。

项目采用 MIT 许可证，保留上游版权声明。分发时请保留 [LICENSE](LICENSE) 和 [NOTICE.md](NOTICE.md)。

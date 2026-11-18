# EWSLab

独立的 Android 模拟音频 EWS（緊急警報放送）接收与调试项目，附离线 JMA 海啸报文音频生成器。适用 Android 10（API 29）及以上。

这是保留上游历史的重写版，应用名和安装 ID 已分离，可与旧 EWSParserApp 并装。当前为 **0.1.0 实验版**；离线解码通过不等于手机声学链路或实体 EWS 接收器已经实测。

## 使用

1. 安装自行构建的 `EWSLab-debug.apk`。调试签名仅用于本地试用。
2. 点击“导入 WAV 文件”，选择生成器导出的 PCM16 WAV；或点击“开始接收”，授予麦克风权限并播放测试音频。
3. 查看单块候选／连续确认、地域、编码时间、两个保护位和六个原始 16 位字。
4. 停止或离开应用前台会停止输入。历史记录只在内存中保留；旋转屏幕会结束当前会话并重建界面。

时间显示为“年末位 · 月 · 日 · 整点（JST）”。EWS 不传分钟／秒，也不传完整年份，不能将此显示当作系统时间错误。奇偶保护块在整点附近可能合法地相差一小时，跨午夜时日期也会变化。

打开 `generator/ews_tsunami_player.html` 即可离线使用生成器，无需 npm 安装或 Web 服务。默认时间来源以页面显示为准；需要重现实例时切换为电文时间并核对“实际编码时间（JST）”。

## 目录

- `core/`：无 Android 依赖的 Java 接收核心，严格帧解析、连续确认、多相位解调、流式 WAV 读取。
- `app/`：原生 Android 界面、前台麦克风、系统文件选择器、输入电平和诊断。
- `generator/`：已核对单文件 HTML 生成器的副本；未改动外部原文件。
- `scripts/`：可重复构建和跨语言联合测试。
- `docs/protocol.md`：协议、算法及来源。
- `docs/verification.md`：实际验证结果、限制和设备验收方法。
- `docs/privacy.md`：权限与隐私说明。

## 构建和测试

需要 JDK 17 或 21、Android SDK 平台 `android-37.0`、Build Tools 36.0.0；生成器联合测试另需 Node.js 22。Gradle Wrapper 8.14.3 已纳入并固定官方分发包 SHA-256；AGP 8.13.0 和 JUnit 4.13.2 为固定版本。

在 Android Studio 中打开项目并设置 SDK，或设置 `ANDROID_HOME`，或在不提交的 `local.properties` 中填写 `sdk.dir`。

Windows 推荐入口（可处理本项目遇到的中文检出路径问题）：

```powershell
.\scripts\build.ps1
```

该入口执行全部测试、lint 和 APK 构建。中文路径下默认将中间输出写到临时目录中由项目路径哈希区分的 ASCII 目录；也可显式指定 `-BuildRoot`。最终 APK 复制到 `verification/local/EWSLab-debug.apk`。请勿将该构建输出目录设为源码目录。

普通路径、Linux 或 macOS 可使用标准 Wrapper：

```sh
./gradlew :core:test :core:verifyGenerator :app:lintDebug :app:assembleDebug
```

仅运行独立 JVM 回归，不需要 Node.js 或生成 WAV：

```sh
./gradlew :core:test
```

联合测试会调用 `node scripts/generator-check.cjs` 生成 26 份 WAV，再由同一接收核心逐字段检查 242 个主体。夹具写入被忽略的 `verification/local/`，无需提交大量二进制文件。失败不会被跳过或降级为成功。

已保留 `warningsAsErrors` 的 lint 门禁，无 baseline。构建仍会报告两类已知工具提示：AGP 8.13.0 的官方测试上限为 SDK 36.1，而本机安装及当前目标为 SDK 37.0；Windows 中文路径需要 `android.overridePathCheck`。本次构建及 lint 实际通过，但不以此声称完成全部 Android 17 行为适配，发布前应升级并验证匹配的 AGP／SDK 工具链。没有隐藏这些提示。

## 与原实现的差异

- 不再假设每次录音恰好返回完整缓冲区，也不再把未知校验码视为可接受的通配符。
- 持续搜索八个位相位，使用精确 640/1024 Hz Goertzel 和相对能量判定；支持小数采样点位时钟及坏块后重新同步。
- 三个固定码、字段标志、53 个法定地域码、日期／年时码字均须有效。
- 同一物理块的多个相位结果先聚合去重，再做连续确认；坏块／长间隔／类型地域变化／不合理时间跳变会重置确认。
- 单块始终仅为候选；连续确认是接收证据，EWS 本身没有 CRC，不能检出所有合法码字之间的误码。
- 结束块的 92 位静音继续推进时钟；麦克风与 WAV 共用同一核心。
- 不自动外放警报，不联网，不后台常驻，不保存麦克风录音。

## Git 与来源

本项目目录是独立副本。保留了上游 `private-yusuke/EWSParserApp` 的 15 个原始提交；原始基线为 `e3473c48feb8cd6e94665232fb8f5c448d0ff2a5`，保存在 `upstream-reference` 分支和 `upstream-reference-e3473c4` 标签中。新增工作在 `main` 上按模块提交。

公开仓库：[YASJUN/EWSLab](https://github.com/YASJUN/EWSLab)。`origin` 指向这个独立仓库，继续分发时应保留 LICENSE 和 NOTICE。旧版自动创建 GitHub Release 的工作流已替换为仅手动触发、只读权限的验证工作流；此工作流尚未在 GitHub 上执行过。

上游 MIT 版权声明继续保留，新增代码同样按 MIT 许可。参见 `LICENSE`、`NOTICE.md`。本项目不代表上游作者、NHK、总务省或 JMA 的认可。

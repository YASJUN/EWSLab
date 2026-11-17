# 验证记录

日期：2026-11-17。以下是本次实际执行结果；未将代码检查写成设备实测。

## 已执行

| 检查 | 结果 |
|---|---|
| 独立 Java 核心 JUnit 回归 | 52 项通过，0失败、0错误、0跳过 |
| 生成器真实 PCM16 WAV 联合解码 | 26 份 WAV、242 个主体逐字段一致，无多相位重复事件 |
| Android 调试 APK | assembleDebug 成功，应用 ID 为 xyz.ckkpd.ewsparserapp.lab，版本0.1.0 |
| Android lintDebug | 通过，XML报告无issue，保留warningsAsErrors，无baseline |
| APK静态元数据 | minSdk29、targetSdk37、唯一申请权限RECORD_AUDIO、存在启动Activity |
| APK签名 | apksigner verify通过，v2调试签名 |
| 原生成器与纳入副本 | SHA-256一致：b6c6e1d00ae461ea558cfb026c45fbcf5d8d5c071c14dcd9bd8ecb48f673a116 |
| Git来源 | 上游15个原始提交仍保留；原始基线分支和标签可回溯；未配置远程 |

测试组合包含第一种、第二种及结束，三个固定码48个位逐位损坏、全部字段边框、非法码字／未知地域／不可能日期、全部月／小时／年份末位码表、跨年保护块、任意分块和多个采样起点、44.1/48kHz、低幅度、确定种子噪声、±1000ppm时钟偏差、坏块后恢复、92位结束静音和连续确认重置。

WAV测试包含PCM16单/双声道、符号与偏移、短读、奇数chunk填充、空data、采样率边界、压缩/浮点/位深/声道拒绝、错误对齐与不完整帧、截断、区块顺序、256MiB与10分钟上限及流关闭。

测试按RED→GREEN执行：初始独立测试因生产类缺失而失败；新增奇数年份末位2月29日断言也先观察到失败，再补校验后通过。JUnit核心测试不依赖生产生成器；另有真实生成器跨语言联合测试。

## 可复现入口

```powershell
.\scripts\build.ps1
```

其对应Gradle任务：

```sh
./gradlew :core:test :core:verifyGenerator :app:assembleDebug :app:lintDebug
```

核心和联合测试的XML报告分别在 `core/build/test-results/test/` 与 `core/build/test-results/verifyGenerator/`；lint报告在 `app/build/reports/lint-results-debug.xml`。指定buildRoot时，路径相对该输出根目录，源码不受影响。Windows脚本会将APK复制回 `verification/local/EWSLab-debug.apk`。

本次环境实际为Windows、JDK21、Node22.17.0、Gradle8.14.3、AGP8.13.0、平台SDK37.0（非预览）和Build Tools36.0.0。中文路径下已验证相对Wrapper类路径及ASCII中间输出路径的构建入口。

## 已知限制及未执行

- 本次没有连接Android设备，也没有已安装的模拟器系统镜像。因此**未执行新版App的Activity运行、真实权限弹窗、旋转、录音生命周期或扬声器→手机麦克风验收**，没有伪造界面截图。静态lint和核心流式测试不能代替这些项目。
- **未用实体EWS接收器测试起动效果**。软件参考解码器和本核心的结果，只证明所检查的位流/PCM符合所用规则，不能给出某台硬件设备必定起动的保证。
- AGP8.13.0明确报告其官方测试上限36.1，本项目使用已安装SDK37.0。APK构建和lint已通过，但仍保留该兼容性提示；发布前应换成明确支持37.0的AGP/Gradle组合并重跑验收。没有通过隐藏lint规则让测试变绿。
- Gradle另有9.0迁移相关弃用提示。本项目Wrapper固定8.14.3；未宣称兼容Gradle9。
- GitHub工作流已改为仅手动验证，尚未实际推送或运行。
- 双声道做算术平均；反相双声道可能相消。未实现任意频偏、强混响或削顶恢复。

## 后续手机和硬件验收

1. 将APK安装为独立应用，确认不覆盖旧EWSParserApp。先导入生成器的普通／跨年／结束WAV，确认全部块和时间保护位。
2. 分别检查首次允许、拒绝、永久拒绝麦克风权限；WAV导入应始终可用。
3. 播放测试音频后，从任意中途开始接收，改变音量及距离，记录手机型号、系统、输入源、收到块数和错误；不要把一台手机的结果推广到所有设备。
4. 接收时停止、切换文件、进入后台、旋转屏幕、快速重复启动；确认麦克风指示关闭，旧任务不覆盖新任务。
5. 仅在隔离测试环境连接实体EWS接收器，记录型号、地域设置、第一/二种配置、音频电平和接线方式，逐项测试开始与结束。此项完成前不标记“硬件实测通过”。

# APK 发布

## v0.1.0

首个公开 APK 对应 `v0.1.0` 标签，版本名 `0.1.0`、版本号 `2`，应用 ID 为 `xyz.ckkpd.ewsparserapp.lab`，最低 Android 10（API 29）。GitHub Release 标为实验性预发布。

发布附件为专用发布密钥签名的 release 构建，不使用 Android debug 密钥。已安装同应用 ID 的 debug 版时，需要先卸载调试版。原始 EWSParserApp 的应用 ID 不同，不受影响。

v0.1.0 发布证书的 SHA-256 指纹（不是 APK 文件哈希）：

```text
BF:BF:E2:D9:DD:0F:5B:EA:A2:17:26:CB:75:8E:3C:E0:5A:2C:71:AC:73:0A:67:8F:69:06:BD:87:A6:6D:A0:C0
```

私钥和密码不得纳入 Git、Release 附件或构建日志。维护者需要安全备份签名密钥及其密码；后续 APK 更新必须沿用兼容的签名。仓库和源码压缩包本身不包含发布密钥。自行构建者应使用自己的密钥，其构建不能直接覆盖本项目发布的 APK。

## 构建

源码标签必须与实际构建提交一致。JDK、SDK 和 Node.js 版本见 [README](../README.md)。Windows 示例：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = 'F:\Android\SDK'
.\scripts\build.ps1 -Tasks @(':core:test', ':core:verifyGenerator', ':app:assembleRelease', ':app:lintRelease') -BuildRoot 'D:/EWSLab-build'
```

普通路径、Linux 或 macOS：

```sh
./gradlew :core:test :core:verifyGenerator :app:assembleRelease :app:lintRelease
```

未配置签名的 release 构建输出 `app-release-unsigned.apk`。默认路径为 `app/build/outputs/apk/release/`；指定 `buildRoot` 后为该输出根目录下的 `app/outputs/apk/release/`。

## 对齐和签名

使用 SDK Build Tools 36.0.0 中的工具。先设置 `JAVA_HOME` 和指向私有密钥库的 `EWSLAB_KEYSTORE`；通过安全方式向当前进程的 `EWSLAB_STORE_PASSWORD` 环境变量提供密码，不把密码直接写在命令行或提交到脚本。`EWSLAB_KEY_ALIAS` 指向实际别名。以下示例假定 PKCS12 密钥库和私钥使用同一密码。

```powershell
$tools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
& "$tools/zipalign.exe" -P 16 -f -v 4 'app-release-unsigned.apk' 'app-release-aligned.apk'
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& "$tools/apksigner.bat" sign --ks $env:EWSLAB_KEYSTORE --ks-key-alias $env:EWSLAB_KEY_ALIAS --ks-pass env:EWSLAB_STORE_PASSWORD --key-pass env:EWSLAB_STORE_PASSWORD --out 'EWSLab-0.1.0.apk' 'app-release-aligned.apk'
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& "$tools/apksigner.bat" verify --verbose --print-certs 'EWSLab-0.1.0.apk'
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
& "$tools/zipalign.exe" -c -P 16 -v 4 'EWSLab-0.1.0.apk'
if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }
Remove-Item Env:EWSLAB_STORE_PASSWORD
```

签名后不得再修改 APK。用 `aapt2 dump badging` 核对包名、版本、最低系统和启动 Activity，并检查发布 APK 没有 `application-debuggable` 标记。校验文件应使用最终已签名 APK 的 SHA-256。

Android 官方参考：[应用签名](https://developer.android.com/studio/publish/app-signing)、[apksigner](https://developer.android.com/tools/apksigner)、[zipalign](https://developer.android.com/tools/zipalign)。

## 发布核对

1. 核心回归、生成器联合测试、release lint 和 APK 构建通过；保留真实验证报告与已知工具提示。
2. 验证 APK 签名、对齐、元数据和 SHA-256。不能以离线测试代替手机或硬件实测。
3. README 三种语言包含互相跳转、相同版本下载入口、安装与签名限制。
4. 提交文档，创建不可移动的 `v0.1.0` 标签并推送。构建后若改动源码或构建配置，应重建并验证 APK。
5. 建立 GitHub 预发布，上传 `EWSLab-0.1.0.apk`、离线生成器 `ews_tsunami_player.html` 和 `SHA256SUMS.txt`。核对公开下载回来的文件与本地 SHA-256 相同。

如果发布后发现问题，应保留版本追溯记录、在 Release 说明问题，并为修复创建新版本；不要静默替换同版本 APK 或移动标签。是否撤下附件应根据实际问题单独决定。

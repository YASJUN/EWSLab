# EWSLab

[简体中文](README.md) | [日本語](README.ja.md) | **English**

EWSLab is an Android app for receiving and analyzing Japanese analog audio EWS (緊急警報放送) signals. It includes an offline audio signal generator for JMA tsunami messages. Requires Android 10 (API 29) or later.

**Version 0.1.0 is experimental.** The app UI is in Simplified Chinese. EWSLab can be installed alongside the original EWSParserApp.

## Disclaimer

- This project is intended for protocol research, software development, and offline testing. It is not an official warning service. Follow official announcements from JMA and local authorities for disaster and evacuation information.
- Generated audio may activate compatible EWS receivers. Test in a controlled environment and avoid creating the impression of a real warning. Do not connect it to broadcast equipment, radio transmitters, or public address systems without authorization.
- Users must comply with applicable local rules on radio transmission, broadcasting, and issuing warnings. The MIT software license does not grant any required regulatory permission.
- Software decoding tests have passed. The app has not yet been tested on an Android device, with speaker audio recorded through a phone's microphone, or with a physical EWS receiver. Reception or activation on a particular device is not guaranteed.
- This is an independent project and does not represent NHK, Japan's Ministry of Internal Affairs and Communications, JMA, or the upstream author. The software is provided "as is" under the [MIT license](LICENSE); its warranty and liability terms apply.

## Download and installation

- [v0.1.0 prerelease: APK, offline generator, and SHA-256 checksums](https://github.com/YASJUN/EWSLab/releases/tag/v0.1.0)
- Android package: `EWSLab-0.1.0.apk`. Application ID: `xyz.ckkpd.ewsparserapp.lab`.
- Release APKs are signed with a dedicated release key. Follow Android's prompts to allow installation from your browser or file manager.
- Microphone reception requires recording permission. Importing a WAV file does not.
- Use the attached `SHA256SUMS.txt` to verify the APK and generator. See the [release instructions (Chinese)](docs/releasing.md) for signing, building, and publishing.

## Usage

1. Download and install `EWSLab-0.1.0.apk` from the release above.
2. Tap “导入 WAV 文件” to select a PCM16 WAV exported by the generator. For microphone reception, tap “开始接收”, grant microphone permission, and play the test audio.
3. Inspect single-block candidates / consecutive confirmation, area, encoded time, two protection bits, and six raw 16-bit words.
4. Tap stop or switch to another app to end reception. History is held in memory only. Rotating the screen also ends the session and recreates the UI.

Time is displayed in JST as the final year digit, month, day, and hour. EWS does not transmit the full year, minutes, or seconds. Near hour boundaries, odd and even protection blocks may differ by one hour, with a corresponding date change across midnight.

Open `generator/ews_tsunami_player.html` in a browser to use the generator offline. No dependency installation or server is required. By default, it uses the current JST at transmission time. To replay a historical message, select “电文 ReportDateTime（历史重现）” as the time source and check “实际编码时间” (actual encoded time) on the page.

## Layout

- `core/`: Android-independent Java receiver core with strict frame parsing, consecutive confirmation, multiphase demodulation, and streaming WAV input.
- `app/`: Native Android UI, foreground microphone capture, system file selection, input levels, and diagnostics.
- `generator/`: Single-file HTML audio signal generator.
- `scripts/`: Build scripts and generator / receiver integration tests.
- [Protocol](docs/protocol.md): Protocol, algorithms, and sources (Chinese).
- [Verification](docs/verification.md): Test results, known limitations, and device testing steps (Chinese).
- [Privacy](docs/privacy.md): Permissions and data handling (Chinese).
- [Releasing](docs/releasing.md): Release build, signing, and checksum steps (Chinese).

## Build and test

Requires JDK 17 or 21, Android SDK platform `android-37.0`, and Build Tools 36.0.0. Generator integration tests also require Node.js 22. The project uses Gradle Wrapper 8.14.3, AGP 8.13.0, and JUnit 4.13.2. The wrapper configuration includes the distribution SHA-256 checksum.

Open the project in Android Studio and configure the SDK, set `ANDROID_HOME`, or set `sdk.dir` in an untracked `local.properties` file.

On Windows, run:

```powershell
.\scripts\build.ps1
```

The script runs the core tests, integration tests, lint, and APK build, producing a local debug APK at `verification/local/EWSLab-debug.apk`. When the source path contains non-ASCII characters, intermediate files are written to a temporary directory. Use `-BuildRoot` to choose another output location. Do not use the source directory as the output directory.

On Linux, macOS, or Windows with an ASCII checkout path, use the wrapper directly:

```sh
./gradlew :core:test :core:verifyGenerator :app:lintDebug :app:assembleDebug
```

To run only the core tests, without Node.js:

```sh
./gradlew :core:test
```

The integration test invokes `node scripts/generator-check.cjs` to generate 26 WAV files and checks all fields of their 242 bodies using the receiver core. Generated files are stored in `verification/local/` and excluded from Git.

Builds and lint have passed. Lint uses `warningsAsErrors` with no baseline. The AGP 8.13.0 / SDK 37.0 combination produces a compatibility warning; `android.overridePathCheck`, used for non-ASCII Windows paths, also produces a warning. See the [verification record](docs/verification.md) for test results and toolchain limitations.

## Receiver features

- Processes microphone input using the number of samples returned by each read.
- Searches eight bit phases using 640/1024 Hz Goertzel analysis and relative energy decisions; supports fractional-sample bit timing and resynchronization after damaged blocks.
- Validates all three fixed codes, field markers, 53 defined area codes, and date / year-hour codewords.
- Deduplicates phase results for the same physical block before consecutive confirmation. Damage, long gaps, type / area changes, or implausible time jumps reset confirmation.
- Marks a single block as a candidate and confirms it after receiving consecutive bodies that meet the validation rules. EWS has no CRC, so some errors between valid codewords cannot be detected.
- Advances the clock through the 92 silent bit slots of an end block. WAV and microphone input share the same core.
- The Android app does not play alarms automatically, access the network, receive persistently in the background, or save microphone recordings.

## Git history and provenance

EWSLab is a rewrite based on [private-yusuke/EWSParserApp](https://github.com/private-yusuke/EWSParserApp), retaining all 15 upstream commits. The original baseline, `e3473c48feb8cd6e94665232fb8f5c448d0ff2a5`, is available through the `upstream-reference` branch and `upstream-reference-e3473c4` tag.

Repository: [YASJUN/EWSLab](https://github.com/YASJUN/EWSLab). GitHub Actions provides manually triggered build verification and does not publish releases automatically.

The project is licensed under MIT and retains the upstream copyright notice. Include [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) when redistributing.

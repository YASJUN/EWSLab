# EWSLab

[简体中文](README.md) | [日本語](README.ja.md) | **English**

An independent Android receiver and diagnostic tool for Japanese analog audio EWS (Emergency Warning System / 緊急警報放送), with an offline signal generator for JMA tsunami messages. Requires Android 10 (API 29) or later.

This rewrite preserves the upstream Git history. Its application name and ID are separate, so it can be installed alongside the original EWSParserApp. **Version 0.1.0 is experimental.** Successful offline decoding does not establish that phone acoustic reception or a physical EWS receiver has been tested.

## Download and installation

- [v0.1.0 prerelease: APK, offline generator, and SHA-256 checksums](https://github.com/YASJUN/EWSLab/releases/tag/v0.1.0)
- Android package: `EWSLab-0.1.0.apk`. Application ID: `xyz.ckkpd.ewsparserapp.lab`.
- The APK is a release build signed with a dedicated release key. If you installed a debug build with the same application ID, uninstall it first. The original EWSParserApp has a different application ID and can remain installed.
- Follow Android's prompts to allow APK installation from your browser or file manager. Microphone permission is needed only for microphone reception. The app UI is currently in Simplified Chinese; the three language editions apply to this README.
- Use the attached `SHA256SUMS.txt` to verify the APK and generator. See the [release instructions (Chinese)](docs/releasing.md) for signing, building, and publishing.

## Usage

1. Download and install `EWSLab-0.1.0.apk` from the release above, or build `EWSLab-debug.apk` for local debugging.
2. Tap “导入 WAV 文件” to select a PCM16 WAV exported by the generator. For microphone reception, tap “开始接收”, grant microphone permission, and play the test audio.
3. Inspect single-block candidates / consecutive confirmation, area, encoded time, two protection bits, and six raw 16-bit words.
4. Stopping reception or leaving the foreground stops input. History is held in memory only. Rotating the screen ends the current session and recreates the UI.

Time is displayed as the final year digit, month, day, and hour in JST. EWS does not transmit minutes, seconds, or the full year; this display is not evidence of an incorrect device clock. Near hour boundaries, odd and even protection blocks may legitimately differ by one hour, with a corresponding date change across midnight.

Open `generator/ews_tsunami_player.html` to use the generator offline. No npm installation or web server is required. Check the page for the selected time source. To reproduce a message example, select the message timestamp and check “实际编码时间（JST）” (actual encoded time in JST).

## Layout

- `core/`: Android-independent Java receiver core with strict frame parsing, consecutive confirmation, multiphase demodulation, and streaming WAV input.
- `app/`: Native Android UI, foreground microphone capture, system file selection, input levels, and diagnostics.
- `generator/`: A copy of the verified single-file HTML generator. The original external file is unchanged.
- `scripts/`: Reproducible build entry point and cross-language generator / receiver integration tests.
- `docs/protocol.md`: Protocol, algorithms, and sources (Chinese).
- `docs/verification.md`: Actual verification results, limitations, and device acceptance steps (Chinese).
- `docs/privacy.md`: Permissions and privacy (Chinese).
- `docs/releasing.md`: Release build, signing, and checksum steps (Chinese).

## Build and test

Requires JDK 17 or 21, Android SDK platform `android-37.0`, and Build Tools 36.0.0. Generator integration tests also require Node.js 22. Gradle Wrapper 8.14.3 is included with its official distribution SHA-256 pinned. AGP is pinned to 8.13.0 and JUnit to 4.13.2.

Open the project in Android Studio and configure the SDK, set `ANDROID_HOME`, or set `sdk.dir` in an untracked `local.properties` file.

On Windows, use this entry point to handle checkout paths containing Chinese characters:

```powershell
.\scripts\build.ps1
```

It runs all tests, lint, and the APK build. For paths containing non-ASCII characters, intermediate outputs default to an ASCII temporary directory distinguished by a hash of the project path. You can also set `-BuildRoot`. The resulting debug APK is copied to `verification/local/EWSLab-debug.apk`. Do not use a source directory as the build output directory.

For ordinary paths, Linux, or macOS, use the standard wrapper:

```sh
./gradlew :core:test :core:verifyGenerator :app:lintDebug :app:assembleDebug
```

To run only the independent JVM regressions, without Node.js or generated WAV files:

```sh
./gradlew :core:test
```

The integration test invokes `node scripts/generator-check.cjs` to generate 26 WAV files, then checks all fields of 242 bodies using the same receiver core. Fixtures are written to the ignored `verification/local/` directory. Failures are not skipped or treated as successes.

Lint retains `warningsAsErrors` with no baseline. Known tool warnings remain visible: AGP 8.13.0 reports an officially tested SDK ceiling of 36.1 while this project uses SDK 37.0, and Chinese checkout paths on Windows require `android.overridePathCheck`. Builds and lint have passed, but this is not a claim of full Android 17 behavior compatibility. A matching AGP / SDK toolchain must be validated before a later stable release.

## Changes from the original implementation

- Does not assume each audio read fills the buffer, or accept unknown check codes as wildcards.
- Continuously searches eight bit phases using exact 640/1024 Hz Goertzel analysis and relative energy decisions; supports fractional-sample bit timing and resynchronization after damaged blocks.
- Validates all three fixed codes, field markers, 53 defined area codes, and date / year-hour codewords.
- Deduplicates phase results for the same physical block before consecutive confirmation. Damage, long gaps, type / area changes, or implausible time jumps reset confirmation.
- Treats a single block as a candidate. EWS has no CRC, so not every error between valid codewords can be detected.
- Advances the clock through the 92 silent bit slots of an end block. WAV and microphone input share the same core.
- No automatic alarm playback, network access, persistent background reception, or microphone recording storage.

## Git history and provenance

This is an independent copy. It retains all 15 original commits from `private-yusuke/EWSParserApp`. The baseline `e3473c48feb8cd6e94665232fb8f5c448d0ff2a5` is available through the `upstream-reference` branch and `upstream-reference-e3473c4` tag. New work is committed by module on `main`.

Public repository: [YASJUN/EWSLab](https://github.com/YASJUN/EWSLab). `origin` points to this independent repository. Preserve LICENSE and NOTICE when redistributing. The old automatic GitHub Release workflow has been replaced with a manually triggered, read-only verification workflow. This workflow has not yet been run on GitHub.

The upstream MIT copyright notice is retained; new code is also licensed under MIT. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). This project is not endorsed by the upstream author, NHK, Japan's Ministry of Internal Affairs and Communications, or JMA.

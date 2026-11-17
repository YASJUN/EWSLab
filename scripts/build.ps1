param(
    [string[]]$Tasks = @(':core:test', ':core:verifyGenerator', ':app:assembleDebug', ':app:lintDebug'),
    [string]$BuildRoot = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin\java.exe' }
else { $java = (Get-Command java -ErrorAction Stop).Source }
if (-not $BuildRoot -and $projectRoot -match '[^\x00-\x7F]') {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($projectRoot))).Replace('-', '').Substring(0, 12) }
    finally { $sha.Dispose() }
    $BuildRoot = Join-Path ([IO.Path]::GetTempPath()) "EWSLab-build-$hash"
    if ($BuildRoot -match '[^\x00-\x7F]') { throw 'Please set -BuildRoot to a writable ASCII-only path.' }
}
Push-Location $projectRoot
try {
    # A relative wrapper classpath avoids legacy Windows JVM Unicode-argument corruption.
    $arguments = @('-Dfile.encoding=UTF-8', '-classpath', 'gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain')
    $arguments += $Tasks
    if ($BuildRoot) { $arguments += "-PbuildRoot=$BuildRoot" }
    $arguments += '--console=plain'
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    $apkRoot = if ($BuildRoot) { Join-Path $BuildRoot 'app' } else { Join-Path $projectRoot 'app/build' }
    $apk = Join-Path $apkRoot 'outputs/apk/debug/app-debug.apk'
    if ((Test-Path $apk) -and ($Tasks -contains ':app:assembleDebug')) {
        $out = Join-Path $projectRoot 'verification/local'
        New-Item -ItemType Directory -Force $out | Out-Null
        Copy-Item -LiteralPath $apk -Destination (Join-Path $out 'EWSLab-debug.apk')
        Write-Output "APK: $(Join-Path $out 'EWSLab-debug.apk')"
    }
} finally { Pop-Location }

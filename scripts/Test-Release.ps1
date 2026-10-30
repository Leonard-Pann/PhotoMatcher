<#
Smoke-test the actual signed, optimized APK on an isolated emulator.
No instrumentation APK is involved: instrumentation can change R8's keep rules.
The emulator must contain only test data. This script installs over the existing
app without clearing its preferences, then force-stops it for three cold starts.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $ApkPath,
    [Parameter(Mandatory = $true)] [string] $Serial,
    [string] $AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = 'Stop'
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$packageName = 'com.bobby.vaudoushorter' # Retained so existing installations can update.
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDirectory = Join-Path $projectRoot "app\build\reports\release-smoke\$Serial"
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null

function Invoke-Adb {
    param([string[]] $Arguments)
    $output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed ($($Arguments -join ' ')): $($output -join [Environment]::NewLine)" }
    return ($output -join [Environment]::NewLine)
}

if ($Serial -notmatch '^emulator-\d+$' -or (Invoke-Adb -Arguments @('shell', 'getprop', 'ro.kernel.qemu')).Trim() -ne '1') {
    throw 'Use an isolated Android emulator, not a personal phone.'
}

Invoke-Adb -Arguments @('install', '-r', $apk) | Write-Output
$packageInfo = Invoke-Adb -Arguments @('shell', 'dumpsys', 'package', $packageName)
if ($packageInfo -notmatch "Package \[$([regex]::Escape($packageName))\]" -or
    $packageInfo -match '(?m)^\s*(?:pkgFlags|flags)=\[[^\]]*\bDEBUGGABLE\b') {
    throw 'The APK must be a release build (not debuggable) of PhotoMatcher.'
}
Invoke-Adb -Arguments @('logcat', '-b', 'crash', '-c') | Out-Null

try {
    foreach ($attempt in 1..3) {
        Invoke-Adb -Arguments @('shell', 'am', 'force-stop', $packageName) | Out-Null
        $launch = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', "$packageName/.MainActivity")
        $launch | Set-Content -LiteralPath (Join-Path $reportDirectory "launch-$attempt.txt")
        Start-Sleep -Seconds 3
        $process = Invoke-Adb -Arguments @('shell', 'pidof', $packageName)
        if ([string]::IsNullOrWhiteSpace($process)) { throw "The app died on cold start $attempt." }
        $activities = Invoke-Adb -Arguments @('shell', 'dumpsys', 'activity', 'activities')
        if ($activities -notmatch "(?:mResumedActivity:|topResumedActivity=).*$([regex]::Escape($packageName))/.MainActivity") {
            throw "PhotoMatcher is not foreground on cold start $attempt."
        }
    }
    Invoke-Adb -Arguments @('shell', 'uiautomator', 'dump', '/sdcard/photomatcher-release-smoke.xml') | Out-Null
    $uiFile = Join-Path $reportDirectory 'screen.xml'
    Invoke-Adb -Arguments @('pull', '/sdcard/photomatcher-release-smoke.xml', $uiFile) | Out-Null
    [xml] $ui = Get-Content -LiteralPath $uiFile -Raw -Encoding UTF8
    foreach ($label in @('PhotoMatcher', 'Photos')) {
        if (-not $ui.SelectSingleNode("//node[@text='$label']")) { throw "Missing UI label: $label" }
    }
    if (-not $ui.SelectSingleNode("//node[@text='Trash' or @text='Corbeille']")) {
        throw 'Missing main-screen trash button.'
    }
    $crashes = Invoke-Adb -Arguments @('logcat', '-b', 'crash', '-d')
    $crashes | Set-Content -LiteralPath (Join-Path $reportDirectory 'crash-log.txt')
    if ($crashes -match [regex]::Escape($packageName)) { throw 'A PhotoMatcher crash was recorded.' }
    Write-Output "PASS: three cold starts of the signed release; PhotoMatcher UI visible. Reports: $reportDirectory"
} catch {
    Invoke-Adb -Arguments @('logcat', '-d', '-t', '1500') | Set-Content -LiteralPath (Join-Path $reportDirectory 'failure-logcat.txt')
    throw
}

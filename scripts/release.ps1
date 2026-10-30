# 正式发布打包（规则与 MusicOrganizer 一致）
#
# 产物（dist/）：
#   OvertimeLog-<版本>-android-arm64-v8a.apk
#   OvertimeLog-<版本>-android-armeabi-v7a.apk
#   OvertimeLog-<版本>-android-x86.apk
#   OvertimeLog-<版本>-android-x86_64.apk
#   OvertimeLog-<版本>-android-universal.apk
#   OvertimeLog-<版本>.aab
#   SHA256SUMS.txt / latest.txt / README.txt
#
# 前提：项目根目录有 signing.properties 和正式 keystore（见 SECRETS-SETUP.md）。
# 没有正式签名时脚本直接失败，绝不产出 debug 签名的"正式包"。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1 -SkipTests
#   powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1 -NoBundle

[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$NoBundle,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\env\Sdk' })
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $repo 'dist'
$signingProps = Join-Path $repo 'signing.properties'

function Write-Step([string]$text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Android\env\jdk21' }

Write-Step '检查版本号'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'set-version.ps1') -Check
if ($LASTEXITCODE -ne 0) { Fail '版本号校验不通过' }
$version = (Get-Content (Join-Path $repo 'VERSION') -Raw).Trim()
$build = (Get-Content (Join-Path $repo 'BUILD_NUMBER') -Raw).Trim()
Write-Host "    VERSION=$version  BUILD=$build"

Write-Step '检查正式签名'
if (-not (Test-Path $signingProps)) {
    Fail "缺少 signing.properties。正式发布必须用正式 keystore 签名，配置方法见 SECRETS-SETUP.md"
}
$props = @{}
Get-Content $signingProps | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2] }
}
if (-not $props['storeFile'] -or -not (Test-Path $props['storeFile'])) {
    Fail "signing.properties 里的 storeFile 不存在：$($props['storeFile'])"
}
Write-Host "    keystore：$($props['storeFile'])  alias：$($props['keyAlias'])"

if (-not $SkipTests) {
    Write-Step '单元测试'
    Push-Location $repo
    try {
        & .\gradlew.bat :app:testDebugUnitTest :core:calculation:test --console=plain | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail '单元测试失败' }
    } finally { Pop-Location }
    Write-Host '    测试通过'
}

Write-Step '构建 release（ABI 拆分 + R8）'
Push-Location $repo
try {
    & .\gradlew.bat :app:assembleRelease -PabiSplits=true --console=plain | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'assembleRelease 失败' }

    if (-not $NoBundle) {
        & .\gradlew.bat :app:bundleRelease --console=plain | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail 'bundleRelease 失败' }
    }
} finally { Pop-Location }

$apkDir = Join-Path $repo 'app\build\outputs\apk\release'
$aabDir = Join-Path $repo 'app\build\outputs\bundle\release'

Write-Step '收集产物并重命名'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Get-ChildItem $dist -Filter "OvertimeLog-$version-*" -File -ErrorAction SilentlyContinue | Remove-Item -Force

$abiFiles = @{
    'arm64-v8a'     = 'app-arm64-v8a-release.apk'
    'armeabi-v7a'   = 'app-armeabi-v7a-release.apk'
    'x86'           = 'app-x86-release.apk'
    'x86_64'        = 'app-x86_64-release.apk'
    'universal'     = 'app-universal-release.apk'
}

$artifacts = @()
foreach ($abi in $abiFiles.Keys | Sort-Object) {
    $src = Join-Path $apkDir $abiFiles[$abi]
    if (-not (Test-Path $src)) { Fail "缺少 APK：$($abiFiles[$abi])（检查 splits 配置）" }
    $target = Join-Path $dist "OvertimeLog-$version-android-$abi.apk"
    Copy-Item $src $target -Force
    $artifacts += Get-Item $target
}

if (-not $NoBundle) {
    $aabSrc = Join-Path $aabDir 'app-release.aab'
    if (-not (Test-Path $aabSrc)) { Fail '缺少 app-release.aab' }
    $aabTarget = Join-Path $dist "OvertimeLog-$version.aab"
    Copy-Item $aabSrc $aabTarget -Force
    $artifacts += Get-Item $aabTarget
}

# R8 混淆映射：崩溃堆栈还原要用，跟版本一起归档（不随 APK 分发）
$mapping = Join-Path $repo 'app\build\outputs\mapping\release\mapping.txt'
$mappingTarget = Join-Path $dist "OvertimeLog-$version-mapping.txt"
if (Test-Path $mapping) {
    Copy-Item $mapping $mappingTarget -Force
    Write-Host "    已归档混淆映射：$(Split-Path $mappingTarget -Leaf)"
} else {
    Write-Host '    WARN: 没找到 mapping.txt（R8 关闭时会没有）' -ForegroundColor Yellow
}

Write-Step '校验签名'
$buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Sort-Object { [version](($_.Name -replace '[^0-9.]', '')) } -Descending |
    Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$jarsigner = Join-Path $env:JAVA_HOME 'bin\jarsigner.exe'
$certDigest = $null

foreach ($apk in ($artifacts | Where-Object { $_.Extension -eq '.apk' })) {
    $verify = & $apksigner verify --print-certs $apk.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "签名校验失败：$($apk.Name)" }
    $line = $verify | Select-String 'SHA-256 digest' | Select-Object -First 1
    if ($line -and $line.Line -match 'digest:\s*([0-9a-fA-F]+)') { $certDigest = $Matches[1] }
    Write-Host "    OK $($apk.Name)"
}
foreach ($aab in ($artifacts | Where-Object { $_.Extension -eq '.aab' })) {
    $verify = & $jarsigner -verify $aab.FullName 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "AAB 签名校验失败：$($aab.Name)" }
    Write-Host "    OK $($aab.Name)"
}

Write-Step '生成校验清单与说明'
$sums = foreach ($a in $artifacts | Sort-Object Name) {
    $hash = (Get-FileHash $a.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash *$($a.Name)"
}
Set-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt') -Value $sums -Encoding UTF8

$apkArm64 = $artifacts | Where-Object { $_.Name -like '*-arm64-v8a.apk' } | Select-Object -First 1
$apkSize = if ($apkArm64) { $apkArm64.Length } else { 0 }
$apkHash = if ($apkArm64) { (Get-FileHash $apkArm64.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } else { '' }
$buildDate = Get-Date -Format 'yyyy-MM-dd'

$latest = @"
version=$version
tag=v$version
build=$build
built=$buildDate
apk=OvertimeLog-$version-android-arm64-v8a.apk
size=$apkSize
sha256=$apkHash
"@
Set-Content -LiteralPath (Join-Path $dist 'latest.txt') -Value $latest -Encoding UTF8

$fileList = ($artifacts | Sort-Object Name | ForEach-Object {
    "  {0,-52} {1,8:N2} MB" -f $_.Name, ($_.Length / 1MB)
}) -join "`n"

$readme = @"
加班记 OvertimeLog v$version 分发包
====================================

版本：$version（build $build）
构建日期：$buildDate
签名证书 SHA-256：$certDigest
说明：官方发布以 Releases 页面为准；本目录用于本地校验与分发。

文件
----
$fileList
  SHA256SUMS.txt                                        以上文件的 SHA-256 校验和

安装
----
Android 8.0（API 26）及以上。允许「未知来源」后安装对应 ABI 的 APK：
  - 不确定机型：装 android-arm64-v8a（绝大多数现代手机）
  - 老旧 32 位机：android-armeabi-v7a
  - 模拟器 / Intel 平板：android-x86 或 android-x86_64
  - 不想挑：android-universal（体积略大）
  - 上架应用商店：使用 .aab

本包使用正式签名，后续版本可直接覆盖升级（同包名 + 同签名）。
若之前装过 debug 签名的测试包，需要先卸载一次（签名不同）。

校验（可选）
------------
Windows PowerShell：
  Get-FileHash .\OvertimeLog-$version-android-arm64-v8a.apk -Algorithm SHA256
Linux/macOS：
  sha256sum -c SHA256SUMS.txt
"@
Set-Content -LiteralPath (Join-Path $dist 'README.txt') -Value $readme -Encoding UTF8

Write-Step '完成'
$artifacts | Sort-Object Name | ForEach-Object {
    Write-Host ("    {0,-52} {1,8:N2} MB" -f $_.Name, ($_.Length / 1MB))
}
Write-Host ''
Write-Host "PASS: 正式包已生成到 $dist" -ForegroundColor Green
Write-Host "版本 $version（build $build），签名证书 SHA-256：$certDigest"
exit 0

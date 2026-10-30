# 版本号维护（规则与 MusicOrganizer 一致）
#
# 唯一真源：
#   VERSION       X.Y.Z       第二、三段必须在 0-99，满 100 必须进位
#   BUILD_NUMBER  整数        单调递增，对应 Android versionCode
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-version.ps1                 # 只把 build 号 +1
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-version.ps1 1.0.1           # 改版本并 +1
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-version.ps1 1.0.1 -Build 7  # 指定 build 号
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-version.ps1 -Check          # 只校验（CI 用）
#
# 允许回退的逃生门：$env:OVERTIMELOG_ALLOW_ANY_VERSION=1

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Version,
    [int]$Build = 0,
    [switch]$Check
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$versionFile = Join-Path $repo 'VERSION'
$buildFile = Join-Path $repo 'BUILD_NUMBER'

function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $versionFile) -or -not (Test-Path $buildFile)) {
    Fail '缺少 VERSION 或 BUILD_NUMBER'
}

$currentVersion = (Get-Content $versionFile -Raw).Trim()
$currentBuild = [int](Get-Content $buildFile -Raw).Trim()

# ---------- 校验现有版本 ----------
if ($currentVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    Fail "VERSION 格式应为 X.Y.Z，当前：$currentVersion"
}
$parts = $currentVersion.Split('.')
if ([int]$parts[1] -gt 99 -or [int]$parts[2] -gt 99) {
    Fail "版本号越界：第二段/第三段必须在 0-99（满 100 进位），当前：$currentVersion"
}
if ($Check) {
    Write-Host "OK  VERSION=$currentVersion  BUILD=$currentBuild"
    exit 0
}

# ---------- 新版本 ----------
if ($Version) {
    if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
        Fail "版本格式应为 X.Y.Z，当前：$Version"
    }
    $newParts = $Version.Split('.')
    if ([int]$newParts[1] -gt 99 -or [int]$newParts[2] -gt 99) {
        $suggest = "$($newParts[0]).$([int]$newParts[1] + 1).0"
        Fail "版本号越界：第二段/第三段必须在 0-99，建议 $suggest"
    }
    $allowAny = $env:OVERTIMELOG_ALLOW_ANY_VERSION -eq '1'
    if (-not $allowAny -and $Version -ne $currentVersion) {
        $old = $currentVersion.Split('.')
        if ($newParts[0] -eq $old[0] -and [int]$newParts[1] -lt [int]$old[1]) {
            Fail "版本号回退：$currentVersion -> $Version（确需回退请设置 OVERTIMELOG_ALLOW_ANY_VERSION=1）"
        }
        if ($newParts[0] -eq $old[0] -and $newParts[1] -eq $old[1] -and [int]$newParts[2] -lt [int]$old[2]) {
            Fail "版本号回退：$currentVersion -> $Version（确需回退请设置 OVERTIMELOG_ALLOW_ANY_VERSION=1）"
        }
    }
    $currentVersion = $Version
}

# ---------- 新 build 号 ----------
$newBuild = if ($Build -gt 0) { $Build } else { $currentBuild + 1 }
if ($newBuild -le $currentBuild -and $Build -gt 0) {
    Fail "build 号必须递增：当前 $currentBuild，指定 $newBuild"
}

Set-Content -LiteralPath $versionFile -Value $currentVersion -Encoding ASCII -NoNewline
Set-Content -LiteralPath $buildFile -Value $newBuild -Encoding ASCII -NoNewline

Write-Host "OK  VERSION=$currentVersion  BUILD=$newBuild（versionCode=$newBuild）" -ForegroundColor Green
exit 0

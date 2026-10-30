# 打包出一个"能直接装"的 APK
#
# 背景：`./gradlew assembleRelease` 产出的是 app-release-unsigned.apk，
# **没有签名**，手机安装时会报"解析软件包时出现问题 / packageinfo is null"。
# 这个脚本把 release 包用本机 debug 证书签好，放到 dist/ 下。
#
# 注意：这里签的是测试证书，只能自己装、自己测，不能上架、不能给别人当正式版。
# 正式发布必须用你自己的发布证书（见 README 的签名说明）。
#
# 用法（项目根目录）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-installable.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-installable.ps1 -Variant debug

[CmdletBinding()]
param(
    [ValidateSet('release', 'debug')]
    [string]$Variant = 'release',
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\env\Sdk' })
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $repo 'dist'
$buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Sort-Object { [version](($_.Name -replace '[^0-9.]', '')) } -Descending |
    Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt2 = Join-Path $buildTools.FullName 'aapt2.exe'
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'

function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Android\env\jdk21' }

Write-Host "==> 构建 $Variant" -ForegroundColor Cyan
Push-Location $repo
try {
    & .\gradlew.bat ":app:assemble$($Variant.Substring(0,1).ToUpper() + $Variant.Substring(1))" --console=plain | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail '构建失败' }
} finally {
    Pop-Location
}

$source = if ($Variant -eq 'release') {
    Join-Path $repo 'app\build\outputs\apk\release\app-release-unsigned.apk'
} else {
    Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
}
if (-not (Test-Path $source)) { Fail "找不到产物：$source" }

New-Item -ItemType Directory -Force -Path $dist | Out-Null

if ($Variant -eq 'debug') {
    # debug 包本来就带调试证书，直接复制
    $target = Join-Path $dist 'OvertimeLog-debug.apk'
    Copy-Item $source $target -Force
} else {
    $badging = (& $aapt2 dump badging $source) -join "`n"
    # 注意：platformBuildVersionName 里也含 versionName，直接匹配会抓到 "17"，这里用 \b 限定
    $m = [regex]::Match($badging, "\bversionName='([^']+)'")
    $versionName = if ($m.Success) { $m.Groups[1].Value } else { 'dev' }
    $target = Join-Path $dist "OvertimeLog-$versionName-test.apk"

    Write-Host '==> 用 debug 证书签名（仅供安装测试）' -ForegroundColor Cyan
    if (-not (Test-Path $keystore)) { Fail "找不到 debug 证书：$keystore（随便构建一个 debug 包就会生成）" }
    & $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
        --ks-key-alias androiddebugkey --out $target $source | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail '签名失败' }
}

Write-Host '==> 校验签名' -ForegroundColor Cyan
# 注意不要接 Select-Object -First：提前截断管道会让上游退出码不可信
$verify = & $apksigner verify --print-certs $target 2>&1
if ($LASTEXITCODE -ne 0) { Fail '签名校验失败' }
$verify | Select-Object -First 2 | ForEach-Object { Write-Host "    $_" }

$item = Get-Item $target
$hash = (Get-FileHash $target -Algorithm SHA256).Hash
Write-Host ''
Write-Host "可安装包：$target" -ForegroundColor Green
Write-Host ("大小：{0:N2} MB" -f ($item.Length / 1MB))
Write-Host "SHA-256：$hash"
if ($Variant -eq 'release') {
    Write-Host '提醒：这是 debug 证书签的测试包，不要上架、不要对外分发。' -ForegroundColor Yellow
}
exit 0

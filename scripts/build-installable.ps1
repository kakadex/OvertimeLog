# 打包出一个"能直接装"的 APK
#
# 背景：
#   `./gradlew assembleRelease` 默认产出 app-release-unsigned.apk（没有证书），
#   手机安装会报"解析软件包时出现问题 / packageinfo is null"。
#
# 做法：让 Gradle 用本机 debug 证书给 release 签名（签名方式与 debug 包完全一致，
# 已知能装），产物是 app-release.apk，再复制到 dist/。
#
# 注意：这里签的是测试证书，只能自己装、自己测，不能上架、不能对外分发。
# 正式发布必须换成你自己的发布证书（见 README 的签名说明）。
#
# 用法（项目根目录）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-installable.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-installable.ps1 -Variant debug
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-installable.ps1 -NoR8

[CmdletBinding()]
param(
    [ValidateSet('release', 'debug')]
    [string]$Variant = 'release',
    # 关闭 R8（排障用：如果签名包装得上但一开 R8 就出问题，用这个对比）
    [switch]$NoR8,
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

function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Android\env\jdk21' }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$gradleArgs = @()
if ($Variant -eq 'release') {
    # 关键：让 AGP 直接签名，而不是事后用 apksigner 补签
    $gradleArgs += '-PuseDebugSigning=true'
    if ($NoR8) { $gradleArgs += '-PdisableR8=true' }
}

$task = if ($Variant -eq 'release') { ':app:assembleRelease' } else { ':app:assembleDebug' }
Write-Host "==> 构建 $Variant $(if ($NoR8) { '（R8 关闭）' })" -ForegroundColor Cyan
Push-Location $repo
try {
    & .\gradlew.bat $task @gradleArgs --console=plain | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail '构建失败' }
} finally {
    Pop-Location
}

$source = if ($Variant -eq 'release') {
    Join-Path $repo 'app\build\outputs\apk\release\app-release.apk'
} else {
    Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
}
if (-not (Test-Path $source)) {
    Fail "找不到已签名产物：$source（release 必须带 -PuseDebugSigning=true 才会生成 app-release.apk）"
}

$badging = (& $aapt2 dump badging $source) -join "`n"
# platformBuildVersionName 里也含 versionName，用 \b 限定，避免抓到 "17"
$m = [regex]::Match($badging, "\bversionName='([^']+)'")
$versionName = if ($m.Success) { $m.Groups[1].Value } else { 'dev' }
$suffix = if ($Variant -eq 'debug') { 'debug' } elseif ($NoR8) { 'release-noR8' } else { 'release' }
$target = Join-Path $dist "OvertimeLog-$versionName-$suffix.apk"
Copy-Item $source $target -Force

Write-Host '==> 校验签名' -ForegroundColor Cyan
$verify = & $apksigner verify --print-certs $target 2>&1
if ($LASTEXITCODE -ne 0) { Fail "签名校验失败：$verify" }
$verify | Select-Object -First 1 | ForEach-Object { Write-Host "    $_" }

$item = Get-Item $target
Write-Host ''
Write-Host "可安装包：$target" -ForegroundColor Green
Write-Host ("大小：{0:N2} MB" -f ($item.Length / 1MB))
Write-Host "SHA-256：$((Get-FileHash $target -Algorithm SHA256).Hash)"
if ($Variant -eq 'release') {
    Write-Host '提醒：这是 debug 证书签的测试包，不要上架、不要对外分发。' -ForegroundColor Yellow
}
exit 0

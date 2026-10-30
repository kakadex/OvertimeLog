# 发布包冒烟测试（release + R8）
#
# 目的：确认"压缩过的包"功能仍然正常，而不是只在模拟器上手点一次。
# 每次改 R8 规则、加依赖、动数据库之前/之后都可以跑一遍。
#
# 它做的事：
#   1. 构建 release（R8 开启）
#   2. 用本机 debug 证书签一个"仅供测试"的包（不要用它发布）
#   3. 装进模拟器并从干净数据开始
#   4. 自动走：隐私说明 -> 设置薪资规则 -> 记一笔 -> 保存
#   5. 界面断言：记录条数、时长、金额（这些值都来自数据库，等于验证了落库）
#   6. 日志里没有 FATAL
#
# 用法（PowerShell，项目根目录）：
#   .\scripts\smoke-release.ps1
#   .\scripts\smoke-release.ps1 -SkipBuild      # 用已有 APK 重跑
#
# 退出码：0 通过，1 失败。

[CmdletBinding()]
param(
    [string]$Avd = 'pixel34',
    [switch]$SkipBuild,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android\env\Sdk' })
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$emulator = Join-Path $Sdk 'emulator\emulator.exe'
$signed = Join-Path $repo 'app\build\outputs\apk\release\app-release.apk'
$dbFile = Join-Path $env:TEMP 'overtimelog-smoke.db'
$dbWal = Join-Path $env:TEMP 'overtimelog-smoke.db-wal'

function Write-Step([string]$text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

# ---------- 1. 构建 ----------
if (-not $SkipBuild) {
    Write-Step '构建 release（R8 开启）'
    if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Android\env\jdk21' }
    Push-Location $repo
    try {
        & .\gradlew.bat :app:assembleRelease -PuseDebugSigning=true --console=plain | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail 'release 构建失败' }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path $signed)) { Fail "找不到已签名的 release 产物：$signed（构建要带 -PuseDebugSigning=true）" }

# ---------- 2. 签名校验 ----------
# 用 Gradle 自带的 debug 签名（-PuseDebugSigning=true），和发给用户的可安装包完全一致，
# 不再用 apksigner 事后补签——那样签出来的包在部分国产 ROM 上装不上。
Write-Step '签名校验'
$apksigner = Join-Path (Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Sort-Object { [version](($_.Name -replace '[^0-9.]', '')) } -Descending |
    Select-Object -First 1).FullName 'apksigner.bat'
$verify = & $apksigner verify $signed 2>&1
if ($LASTEXITCODE -ne 0) { Fail "签名校验失败：$verify" }
Write-Host ("    包体积：{0:N2} MB" -f ((Get-Item $signed).Length / 1MB))
# ---------- 3. 设备 ----------
Write-Step '检查模拟器'
$running = (& $adb devices) -match 'emulator-\d+\s+device'
if (-not $running) {
    Write-Host "    启动 $Avd ..."
    $env:ANDROID_SDK_ROOT = $Sdk
    Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $Avd, '-no-snapshot-load', '-no-boot-anim', '-no-window', '-gpu', 'swiftshader_indirect', '-no-audio'
    ) -WindowStyle Hidden | Out-Null
    & $adb wait-for-device
    $deadline = (Get-Date).AddMinutes(4)
    do {
        Start-Sleep -Seconds 3
        $boot = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
    } while ($boot -ne '1' -and (Get-Date) -lt $deadline)
    if ($boot -ne '1') { Fail '模拟器启动超时' }
}
Start-Sleep -Seconds 1

# ---------- 4. 安装 ----------
Write-Step '安装并从干净数据启动'
& $adb uninstall com.overtimelog 2>$null | Out-Null
& $adb install $signed | Out-Null
if ($LASTEXITCODE -ne 0) { Fail '安装失败' }
& $adb logcat -c
& $adb shell am start -n com.overtimelog/.MainActivity | Out-Null
Start-Sleep -Seconds 4

# ---------- 5. 界面驱动 ----------
function Get-Ui {
    & $adb shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
    return ((& $adb shell cat /sdcard/ui.xml) -join "`n")
}

function Find-Point([string]$raw, [string]$text) {
    # 找"文字最近的可点击祖先"的中心点。
    # 不能点文字自己的中心：底部导航的文字中心落在屏幕最下方一段触摸死区（y 约 >2250），
    # 点它没有任何反应；而导航项可点击区域的中心在 2230 左右，能点到。
    $doc = New-Object System.Xml.XmlDocument
    try { $doc.LoadXml($raw) } catch { return $null }

    $target = $null
    foreach ($n in $doc.SelectNodes('//*[@text]')) {
        if ($n.GetAttribute('text') -eq $text) { $target = $n; break }
    }
    if (-not $target) {
        # 有些 Compose 控件（比如悬浮按钮）只在 content-desc 里暴露文字
        foreach ($n in $doc.SelectNodes('//*[@content-desc]')) {
            if ($n.GetAttribute('content-desc') -eq $text) { $target = $n; break }
        }
    }
    if (-not $target) { return $null }

    $cur = $target
    while ($cur -and $cur.NodeType -eq 'Element') {
        if ($cur.GetAttribute('clickable') -eq 'true') {
            if ($cur.GetAttribute('bounds') -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
                $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                return @($x, $y)
            }
        }
        $cur = $cur.ParentNode
    }

    if ($target.GetAttribute('bounds') -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        return @($x, $y)
    }
    return $null
}

function Wait-Text([string]$text, [int]$timeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $raw = Get-Ui
        # 包含匹配：界面标签常带后缀，比如"休息扣除（小时）"
        if ($raw -match ('text="[^"]*' + [regex]::Escape($text) + '[^"]*"')) { return $raw }
        Start-Sleep -Milliseconds 600
    }
    return $null
}

function Tap-Text([string]$text, [int]$timeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $raw = Get-Ui
        $point = Find-Point $raw $text
        if ($point) {
            Write-Host "    点击「$text」@ $($point[0]),$($point[1])"
            & $adb shell input tap $point[0] $point[1] | Out-Null
            Start-Sleep -Milliseconds 900
            return
        }
        Start-Sleep -Milliseconds 600
    }
    Fail "界面上找不到「$text」"
}

Write-Step '隐私说明 -> 设置'
Tap-Text '知道了'
Tap-Text '设置'
if (-not (Wait-Text '录入默认值')) { Fail '没有进入设置页' }
# 新设置项（默认上下班时间、休息扣除单位）也要在发布包里能正常渲染
if (-not (Wait-Text '默认上班时间')) { Fail '设置页缺少「默认上班时间」' }
if (-not (Wait-Text '休息扣除单位')) { Fail '设置页缺少「休息扣除单位」' }

Write-Step '切换休息单位（验证 DataStore 写入在 R8 下正常）'
Tap-Text '分钟'
if (-not (Wait-Text '按分钟填写')) { Fail '休息单位切换没有生效' }

Write-Step '配置薪资规则（月薪 10000 / 21.75 天 / 8 小时）'
Tap-Text '去配置'
if (-not (Wait-Text '保存规则')) { Fail '没有进入薪资规则页' }
Tap-Text '保存规则'
if (-not (Wait-Text '修改薪资规则')) { Fail '规则没有保存成功' }

Write-Step '新增一条 18:00-21:00 的工作日加班'
Tap-Text '概览'
if (-not (Wait-Text '本月概览')) { Fail '没有回到概览页' }
Tap-Text '记一笔'
if (-not (Wait-Text '休息扣除')) { Fail '没有进入录入页' }
Tap-Text '保存'
if (-not (Wait-Text '共 1 条记录')) { Fail '记录没有保存成功' }

# ---------- 6. 界面断言 ----------
Write-Step '界面断言（记录数 / 时长 / 金额）'
$ui = Get-Ui
foreach ($expected in @('共 1 条记录', '3 小时', '¥258.62')) {
    if ($ui -notmatch [regex]::Escape($expected)) { Fail "概览页上没有出现「$expected」" }
    Write-Host "    找到「$expected」"
}

Write-Step '统计页结算状态筛选'
Tap-Text '统计'
if (-not (Wait-Text '全部状态')) { Fail '统计页没有结算状态筛选项' }
Tap-Text '概览'
if (-not (Wait-Text '本月概览')) { Fail '从统计页返回概览失败' }

# ---------- 7. 数据库直连（可选） ----------
# release 包不可 run-as，拿不到私有目录；能拿到时再查一遍，拿不到就以界面断言为准。
Write-Step '数据库直连校验（可选）'
Start-Sleep -Seconds 1
cmd /c "`"$adb`" exec-out run-as com.overtimelog cat databases/overtimelog.db > `"$dbFile`""
cmd /c "`"$adb`" exec-out run-as com.overtimelog cat databases/overtimelog.db-wal > `"$dbWal`""
$dbSize = if (Test-Path $dbFile) { (Get-Item $dbFile).Length } else { 0 }

if ($dbSize -lt 100) {
    Write-Host '    提示：release 包不可 run-as，跳过数据库直连校验（界面断言已覆盖）。' -ForegroundColor Yellow
} elseif (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Host '    提示：本机没有 python，跳过数据库直连校验。' -ForegroundColor Yellow
} else {
    $check = @'
import sqlite3, sys
db = sys.argv[1]
c = sqlite3.connect(db)
rules = c.execute("select count(*) from salary_rules where active = 1").fetchone()[0]
rows = c.execute("select duration_minutes, amount_cents, comp_time_minutes, type from overtime_records").fetchall()
errors = []
if rules != 1:
    errors.append(f"active salary rules = {rules}, expected 1")
if len(rows) != 1:
    errors.append(f"records = {len(rows)}, expected 1")
else:
    duration, amount, comp, otype = rows[0]
    if duration != 180:
        errors.append(f"duration = {duration}, expected 180")
    if amount != 25862:
        errors.append(f"amount = {amount}, expected 25862")
    if comp != 0:
        errors.append(f"comp minutes = {comp}, expected 0")
    if otype != "WORKDAY":
        errors.append(f"type = {otype}, expected WORKDAY")
print("DB_OK" if not errors else "DB_FAIL: " + "; ".join(errors))
sys.exit(0 if not errors else 1)
'@
    $checkPath = Join-Path $env:TEMP 'overtimelog-smoke-check.py'
    [System.IO.File]::WriteAllText($checkPath, $check, (New-Object System.Text.UTF8Encoding($false)))
    $result = & python $checkPath $dbFile
    Write-Host "    $result"
    if ($LASTEXITCODE -ne 0) { Fail '数据库校验不通过' }
}

# ---------- 8. 稳定性 ----------
Write-Step '检查运行期崩溃'
$crash = (& $adb logcat -d) | Select-String -Pattern 'FATAL EXCEPTION'
if ($crash) {
    $crash | Select-Object -First 5 | ForEach-Object { Write-Host "    $_" }
    Fail '日志里发现崩溃'
}

Write-Host ''
Write-Host 'PASS: release（R8）冒烟通过 —— 规则保存、记录落库、金额计算均正确' -ForegroundColor Green
Write-Host "提示：$signed 是 debug 证书签的测试包，不要用于发布。" -ForegroundColor Yellow
exit 0

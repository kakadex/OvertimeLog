# 把 dist/ 里的正式产物发布到 Gitee / GitHub Releases
#
# 前置：
#   1. 已跑过 scripts/release.ps1，dist/ 里有 OvertimeLog-<版本>-*.apk / .aab / SHA256SUMS.txt
#   2. 本地已打 tag（脚本会帮你建，但推送到远程需要你自己 git push）
#   3. 有对应平台的 token（只放环境变量，不要写进仓库）：
#        Gitee  ：GITEE_TOKEN
#        GitHub ：GITHUB_TOKEN 或 GH_TOKEN
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider gitee  -Repo <owner>/<repo>
#   powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider github -Repo <owner>/<repo>
#   powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider gitee -Repo x/y -DryRun
#
# 说明：
#   - 已存在的 Release 会复用，不会重复创建；同名附件已存在则跳过。
#   - latest.txt 会在上传完 APK 后补上真实下载地址，再作为附件传上去。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('gitee', 'github')]
    [string]$Provider,

    # 仓库坐标：owner/repo。不填则尝试从 git remote 推断。
    [string]$Repo,

    # token。不填则读环境变量（推荐）。
    [string]$Token,

    # tag，默认 v<VERSION>
    [string]$Tag,

    # 发布说明文件，默认 RELEASE_NOTES.md
    [string]$NotesFile,

    # 只做参数与文件检查，不发网络请求
    [switch]$DryRun,

    # git 可执行文件路径（本机 git 不在 PATH 时用）
    [string]$GitPath
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$repoRoot = Split-Path $PSScriptRoot -Parent

# git 解析：PATH -> 参数 -> 本机便携版
if (-not $GitPath) {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { $GitPath = $cmd.Source }
    elseif (Test-Path 'C:\DSHX\tools\git\cmd\git.exe') { $GitPath = 'C:\DSHX\tools\git\cmd\git.exe' }
}
if (-not $GitPath -or -not (Test-Path $GitPath)) { Fail '找不到 git，可用 -GitPath 指定（例如 C:\tools\git\cmd\git.exe）' }
$dist = Join-Path $repoRoot 'dist'
$version = (Get-Content (Join-Path $repoRoot 'VERSION') -Raw).Trim()
if (-not $Tag) { $Tag = "v$version" }
if (-not $NotesFile) { $NotesFile = Join-Path $repoRoot 'RELEASE_NOTES.md' }

function Write-Step([string]$text) { Write-Host "==> $text" -ForegroundColor Cyan }
function Fail([string]$text) {
    Write-Host "FAIL: $text" -ForegroundColor Red
    exit 1
}

# ---------- 仓库坐标 ----------
if (-not $Repo) {
    $remote = & $GitPath -C $repoRoot remote get-url origin 2>$null
    if ($remote -and $remote -match '[:/]([^/:]+)/([^/]+?)(\.git)?$') {
        $Repo = "$($Matches[1])/$($Matches[2])"
    }
}
if (-not $Repo) {
    Fail '缺少 -Repo 参数（例如 -Repo kakadex/OvertimeLog），且无法从 git remote 推断'
}
if ($Repo -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
    Fail "仓库坐标格式应为 owner/repo，当前：$Repo"
}

# ---------- token ----------
# 先读当前进程环境变量；读不到再读用户级变量（兼容 setx 之后本进程没重启的情况）。
function Get-EnvValue([string]$name) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $value) { $value = [Environment]::GetEnvironmentVariable($name, 'User') }
    return $value
}
if (-not $Token) {
    $Token = if ($Provider -eq 'gitee') {
        Get-EnvValue 'GITEE_TOKEN'
    } else {
        $gh = Get-EnvValue 'GITHUB_TOKEN'
        if (-not $gh) { $gh = Get-EnvValue 'GH_TOKEN' }
        $gh
    }
}
if (-not $Token -and -not $DryRun) {
    $var = if ($Provider -eq 'gitee') { 'GITEE_TOKEN' } else { 'GITHUB_TOKEN' }
    Fail "缺少 token：请设置环境变量 $var 后再运行（不要把 token 写进仓库或聊天记录）"
}

# ---------- 产物 ----------
$assets = @()
$assets += Get-ChildItem $dist -Filter "OvertimeLog-$version-android-*.apk" -File -ErrorAction SilentlyContinue
$assets += Get-ChildItem $dist -Filter "OvertimeLog-$version.aab" -File -ErrorAction SilentlyContinue
foreach ($extra in @('SHA256SUMS.txt', 'README.txt')) {
    $p = Join-Path $dist $extra
    if (Test-Path $p) { $assets += Get-Item $p }
}
if ($assets.Count -eq 0) { Fail "dist/ 里没有 $version 的产物，先跑 scripts/release.ps1" }
$assets = $assets | Sort-Object Name

Write-Step "发布 $Repo @ $Tag（$Provider）"
$assets | ForEach-Object { Write-Host ("    {0,-52} {1,8:N2} MB" -f $_.Name, ($_.Length / 1MB)) }

# ---------- 本地 tag ----------
$tagExists = (& $GitPath -C $repoRoot rev-parse -q --verify "refs/tags/$Tag") -ne $null
if (-not $tagExists) {
    Write-Step "本地没有 tag $Tag，创建它"
    & $GitPath -C $repoRoot -c user.name='OvertimeLog Release' -c user.email='release@localhost' tag $Tag
    if ($LASTEXITCODE -ne 0) { Fail '创建 tag 失败' }
    Write-Host "    提示：还需要 git push origin $Tag 才能让远程 tag 生效"
}

$notes = if (Test-Path $NotesFile) { Get-Content $NotesFile -Raw -Encoding UTF8 } else { "加班记 $Tag" }
$apiBase = if ($Provider -eq 'gitee') { 'https://gitee.com/api/v5' } else { 'https://api.github.com' }
$pageUrl = if ($Provider -eq 'gitee') { "https://gitee.com/$Repo/releases/tag/$Tag" } else { "https://github.com/$Repo/releases/tag/$Tag" }
$downloadBase = if ($Provider -eq 'gitee') { "https://gitee.com/$Repo/releases/download/$Tag" } else { "https://github.com/$Repo/releases/download/$Tag" }

# ---------- DryRun ----------
if ($DryRun) {
    Write-Step 'DryRun：以下请求不会真正发送'
    Write-Host "    创建/复用 Release：$apiBase/repos/$Repo/releases"
    Write-Host "    发布页：$pageUrl"
    Write-Host "    下载前缀：$downloadBase"
    Write-Host "    待上传：$($assets.Count) 个文件"
    foreach ($a in $assets) { Write-Host "      - $($a.Name)" }
    exit 0
}

# ---------- 公共请求辅助 ----------
$headers = @{}
if ($Provider -eq 'gitee') {
    $headers['Content-Type'] = 'application/json;charset=UTF-8'
} else {
    $headers['Authorization'] = "Bearer $Token"
    $headers['Accept'] = 'application/vnd.github+json'
    $headers['X-GitHub-Api-Version'] = '2022-11-28'
}

# Gitee 会把 UTF-8 请求体按 Latin-1 解析，导致中文变乱码；
# 把非 ASCII 字符转成 \uXXXX 后，任何 JSON 解析器都会还原成正确字符。
function Convert-ToAsciiJson([string]$json) {
    $sb = New-Object System.Text.StringBuilder
    foreach ($ch in $json.ToCharArray()) {
        if ([int]$ch -le 127) { [void]$sb.Append($ch) }
        else { [void]$sb.Append(('\u{0:x4}' -f [int]$ch)) }
    }
    return $sb.ToString()
}

function Invoke-Api {
    param([string]$Method, [string]$Url, $Body)
    $params = @{ Method = $Method; Uri = $Url; Headers = $headers; ErrorAction = 'Stop' }
    if ($Body) {
        $json = Convert-ToAsciiJson ($Body | ConvertTo-Json -Depth 6 -Compress)
        # 必须发 UTF-8 字节，否则中文说明在 Gitee / GitHub 上会乱码
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes($json)
        $params['ContentType'] = 'application/json; charset=utf-8'
    }
    return Invoke-RestMethod @params
}

function Get-ExistingRelease {
    try {
        $found = if ($Provider -eq 'gitee') {
            Invoke-Api -Method 'Get' -Url "$apiBase/repos/$Repo/releases/tags/${Tag}?access_token=$Token"
        } else {
            Invoke-Api -Method 'Get' -Url "$apiBase/repos/$Repo/releases/tags/$Tag"
        }
    } catch {
        return $null
    }
    # Gitee 在 tag 没有对应 Release 时返回字符串 "null"，不能当对象用
    if ($found -and $found -isnot [string] -and $found.id) { return $found }
    return $null
}

function New-Release {
    $payload = @{
        tag_name         = $Tag
        name             = "加班记 $Tag"
        body             = $notes
        draft            = $false
        prerelease       = $false
        target_commitish = 'main'
    }
    if ($Provider -eq 'gitee') {
        $payload['access_token'] = $Token
        return Invoke-Api -Method 'Post' -Url "$apiBase/repos/$Repo/releases" -Body $payload
    } else {
        return Invoke-Api -Method 'Post' -Url "$apiBase/repos/$Repo/releases" -Body $payload
    }
}

function Update-Release($release, [string]$notes) {
    # Gitee 的 PATCH 校验要求带上 tag_name
    $payload = @{ name = "加班记 $Tag"; body = $notes; tag_name = $Tag }
    if ($Provider -eq 'gitee') { $payload['access_token'] = $Token }
    Invoke-Api -Method 'Patch' -Url "$apiBase/repos/$Repo/releases/$($release.id)" -Body $payload | Out-Null
}

function Get-ExistingAssetNames($release) {
    if ($Provider -eq 'gitee') {
        $detail = Invoke-Api -Method 'Get' -Url "$apiBase/repos/$Repo/releases/$($release.id)?access_token=$Token"
        if ($detail.assets) { return @($detail.assets | ForEach-Object { $_.name }) }
        return @()
    } else {
        $list = Invoke-Api -Method 'Get' -Url "$apiBase/repos/$Repo/releases/$($release.id)/assets"
        return @($list | ForEach-Object { $_.name })
    }
}

function Send-Asset([string]$path, $release) {
    $name = Split-Path $path -Leaf
    if ($Provider -eq 'gitee') {
        # Gitee 是 multipart 上传，PowerShell 5.1 没有 -Form，用 curl
        $url = "$apiBase/repos/$Repo/releases/$($release.id)/attach_files"
        $out = & curl.exe -s -X POST $url -F "access_token=$Token" -F "file=@$path"
        if ($LASTEXITCODE -ne 0) { Fail "上传失败：$name" }
        if ($out -match '"message"\s*:\s*"([^"]+)"') { Fail "上传失败：$name — $($Matches[1])" }
    } else {
        # GitHub 用 REST + -InFile（本机 curl 访问 api.github.com 不通，但 PowerShell 可以）
        $url = "https://uploads.github.com/repos/$Repo/releases/$($release.id)/assets?name=$name"
        $uploadHeaders = @{
            Authorization = "Bearer $Token"
            Accept        = 'application/vnd.github+json'
            'User-Agent'  = 'OvertimeLog-Release'
        }
        try {
            Invoke-RestMethod -Uri $url -Method Post -Headers $uploadHeaders -InFile $path `
                -ContentType 'application/octet-stream' -TimeoutSec 300 | Out-Null
        } catch {
            Fail "上传失败：$name — $($_.Exception.Message)"
        }
    }
    Write-Host "    OK $name"
}

# ---------- 创建或复用 Release ----------
Write-Step '创建 / 复用 Release'
$release = Get-ExistingRelease
if ($release) {
    Write-Host "    已存在，复用 id=$($release.id)，并同步发布说明"
    Update-Release $release $notes
} else {
    $release = New-Release
    Write-Host "    已创建 id=$($release.id)"
}
if (-not $release -or -not $release.id) { Fail '没有拿到 Release id（检查 tag 是否存在、token 权限）' }

$existing = Get-ExistingAssetNames $release
Write-Host "    已有附件 $($existing.Count) 个"

# ---------- 上传 APK / AAB / 校验清单 ----------
Write-Step '上传产物'
foreach ($asset in $assets) {
    if ($existing -contains $asset.Name) {
        Write-Host "    跳过（已存在） $($asset.Name)"
        continue
    }
    Send-Asset $asset.FullName $release
}

# ---------- 生成带下载地址的 latest.txt ----------
Write-Step '生成 latest.txt（含下载地址）并上传'
$arm64 = $assets | Where-Object { $_.Name -like '*-android-arm64-v8a.apk' } | Select-Object -First 1
$sha = if ($arm64) { (Get-FileHash $arm64.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } else { '' }
$latestLines = @(
    "version=$version",
    "tag=$Tag",
    "page=$pageUrl",
    "apk=$downloadBase/$($arm64.Name)",
    "size=$($arm64.Length)",
    "sha256=$sha"
)
if ($Provider -eq 'gitee') {
    $latestLines += 'prefer=gitee'
} else {
    $latestLines += 'prefer=github'
}
$latestPath = Join-Path $dist 'latest.txt'
Set-Content -LiteralPath $latestPath -Value $latestLines -Encoding UTF8

if ($existing -contains 'latest.txt') {
    Write-Host '    latest.txt 已存在，跳过上传（如内容有变请先在网页端删除旧附件）'
} else {
    Send-Asset $latestPath $release
}

Write-Host ''
Write-Host "PASS: 已发布到 $pageUrl" -ForegroundColor Green
Write-Host '提示：Gitee 附件有大小/数量限制；若某个文件上传失败，可在发布页手动补传。'
exit 0

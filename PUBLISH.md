# 发布到 Gitee / GitHub Releases

两条通道，产物和命名完全一样（规则见 [RELEASE.md](RELEASE.md)）：

| 通道 | 方式 | 适用 |
|---|---|---|
| Gitee Releases | 本地脚本 `scripts/publish.ps1 -Provider gitee` | 国内下载快，作为主分发 |
| GitHub Releases | 本地脚本 或 Actions 工作流 | 开源主页、海外用户 |

## 一、先建仓库

两个平台都需要先有一个**空仓库**（不要勾选初始化 README，避免首推冲突）：

- Gitee：新建仓库 → 仓库名 `OvertimeLog` → 公开 / 私有自选 → 不要初始化
- GitHub：New repository → `OvertimeLog` → 不要 Add README / .gitignore / license

建好后把本地仓库关联上去：

```powershell
git remote add origin https://gitee.com/<你的用户名>/OvertimeLog.git
git push -u origin main
git push origin v1.0.0
```

> 仓库里已有 `LICENSE`（MIT）、`README.md`、`PRIVACY.md`、`SECURITY.md`，
> 建仓时不要再让平台生成一份，避免重复。

## 二、准备 token（只放环境变量）

| 平台 | Token 类型 | 权限 |
|---|---|---|
| Gitee | 私人令牌（设置 → 私人令牌） | `projects`（仓库读写） |
| GitHub | Fine-grained token | 目标仓库的 `Contents: Read and write` |

设置方式（当前 PowerShell 窗口有效，不写入仓库）：

```powershell
$env:GITEE_TOKEN  = '<你的 Gitee 令牌>'
$env:GITHUB_TOKEN = '<你的 GitHub 令牌>'
```

> 不要把 token 写进脚本、文档、聊天记录或 `.env` 提交。

## 三、发布

先出正式包（若还没出）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1
```

Gitee：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider gitee -Repo <用户名>/OvertimeLog
```

GitHub（本地）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider github -Repo <用户名>/OvertimeLog
```

脚本会：
1. 校验 `VERSION` 与产物；
2. 本地没有 tag 就创建 `v<版本>`（**仍需要你 `git push origin v1.0.0`**）；
3. 创建或复用 Release，发布说明取 `RELEASE_NOTES.md`；
4. 上传 APK / AAB / `SHA256SUMS.txt` / `README.txt`；
5. 生成带下载地址的 `latest.txt` 并上传。

先演练不发请求：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\publish.ps1 -Provider gitee -Repo <用户名>/OvertimeLog -DryRun
```

## 四、GitHub Actions（可选）

`.github/workflows/release.yml` 支持手动触发：
Actions → release → Run workflow → 填已存在的 tag（如 `v1.0.0`）。

需要提前配 4 个 repository secret（见 [SECRETS-SETUP.md](SECRETS-SETUP.md)）：
`ANDROID_KEYSTORE_BASE64`、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_PASSWORD`，
别名固定 `overtimelog`。

工作流会校验 tag 与 `VERSION` 一致、跑单测、出 R8 + ABI 拆分包并创建 GitHub Release。
**没有配签名 secrets 时直接失败**，不会产出 debug 签名的包。

Gitee 暂无对应官方 Actions，走本地 `publish.ps1`。

## 五、注意事项

- **Gitee 附件限制**：单文件 ≈100 MB、单仓库发布附件总量有限；本项目单包 1.8 MB，远低于限制。
- **AAB 不上架就没有用**：它是给应用商店的，个人分发用 APK。
- **latest.txt 的用途**：应用内「检查更新」或下载页可以直接读它拿到版本、大小、SHA-256、下载地址。
- **同名附件**：脚本会跳过已存在的附件；如果某个文件传坏了，先在网页端删除旧附件再重跑。
- **tag 与 VERSION 不一致**会导致 GitHub Actions 直接失败；本地脚本以上传的 tag 为准，发版前先跑
  `scripts/set-version.ps1 -Check`。

## 六、发布后检查

- [ ] 发布页能打开，说明来自 `RELEASE_NOTES.md`
- [ ] 每个 APK 的 SHA-256 与 `SHA256SUMS.txt` 一致
- [ ] 手机浏览器点 `android-arm64-v8a.apk` 能下载并安装
- [ ] `latest.txt` 里的 `apk` 地址可访问
- [ ] 已安装旧版的手机覆盖升级成功、数据保留

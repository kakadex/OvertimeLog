# 正式发布清单

这份文档回答一件事：**要把「加班记」发出去，需要准备什么、按什么顺序做。**

## 一、发版前必须准备好

### 1. 正式签名（一次性，最重要）

| 项 | 状态 | 说明 |
|---|---|---|
| 正式 keystore | 已生成本机一份，**请立即离线备份** | 放在项目外，`.gitignore` 已排除 |
| `signing.properties` | 已配置（不入库） | 指向 keystore，含口令 |
| 备份 | **待你完成** | 丢钥匙 = 以后无法出升级包 |

没有正式签名时，`scripts/release.ps1` 会直接失败，不会产出 debug 签名的"正式包"。

### 2. 版本号

- `VERSION`：X.Y.Z，第二、三段 0–99，满 100 进位（与 MusicOrganizer 同规则）
- `BUILD_NUMBER`：单调递增，对应 Android `versionCode`
- 改版本：`scripts/set-version.ps1`
- 发版 tag：`vX.Y.Z`

### 3. 质量门禁

```powershell
./gradlew :app:testDebugUnitTest :core:calculation:test   # 单测
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-release.ps1   # R8 release 冒烟
```

冒烟会验证：装包、配薪资规则、记一笔、金额正确、统计页筛选存在、无崩溃。

### 4. 真机验收（模拟器通过不代表手机通过）

- [ ] Android 8 / 10 / 13 / 15 各一台（或至少 8 和 13）
- [ ] 全新安装 → 首次启动隐私说明 → 配规则 → 记一笔 → 金额正确
- [ ] 覆盖安装：旧版直接升级，**数据保留**
- [ ] 导出 CSV：系统文件选择器能保存，Excel 打开中文不乱码
- [ ] 清除全部数据后，重新进入无历史记录
- [ ] 深色模式、系统字体放大后关键页面不截断
- [ ] 通知权限未申请（V1 无提醒），确认不影响核心功能

### 5. 商店素材与合规

| 项 | 说明 |
|---|---|
| 应用图标 | 512×512 PNG（商店要求） |
| 截图 | 至少 4 张：概览、记录、日历、统计 |
| 应用描述 | 中文简介、功能列表、免责声明（金额仅供参考） |
| 隐私政策 | 需要可公开访问的 URL；内容可直接用仓库里的 `PRIVACY.md` |
| 数据安全表单 | 声明：不收集数据、不联网、本地存储 |
| 开源声明 | MIT，`LICENSE` + 应用内「设置 → 开源许可」 |
| 包名 | `com.overtimelog` |

### 6. 归档（崩溃排查要用）

每次发布保留：

- `dist/` 下的 APK / AAB / `SHA256SUMS.txt` / `latest.txt`
- R8 混淆映射 `app/build/outputs/mapping/release/mapping.txt`（`release.ps1` 会复制到 dist）
- 对应的 git tag 与 `RELEASE_NOTES.md`

## 二、每次发版的命令顺序

```powershell
# 1. 改版本（自动 build +1）
powershell -ExecutionPolicy Bypass -File .\scripts\set-version.ps1 1.0.1

# 2. 跑测试
./gradlew :app:testDebugUnitTest :core:calculation:test

# 3. 出正式包（自动校验签名、ABI 拆分、SHA256、README/latest）
powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1

# 4. 真机验收后打 tag 并推送
git tag v1.0.1
git push origin v1.0.1

# 5. 上传 dist/ 产物到 Releases，正文用 RELEASE_NOTES.md
```

## 三、产物与命名规则（与 MusicOrganizer 一致）

| 产物 | 命名 |
|---|---|
| APK（按 CPU 架构） | `OvertimeLog-<版本>-android-<abi>.apk` |
| 通用 APK | `OvertimeLog-<版本>-android-universal.apk` |
| 应用商店包 | `OvertimeLog-<版本>.aab` |
| 校验清单 | `SHA256SUMS.txt` |
| 版本元数据 | `latest.txt` |
| 分发说明 | `README.txt` |
| 混淆映射 | `OvertimeLog-<版本>-mapping.txt` |

ABI 取值：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`。

## 四、回滚与热修

- 版本号不允许回退（`set-version.ps1` 会拦），需要补发时**递增**新版本号
- 紧急回退：把上一版 APK 重新上传，但 `versionCode` 必须更大，所以仍要走新版本号（如 `1.0.2`）
- 换签名 = 用户必须卸载重装，不到万不得已不要做

## 五、当前还缺什么（发出去之前）

- [ ] **备份 keystore**（用户操作，最关键）
- [ ] 隐私政策的公网 URL（可放 GitHub Pages / Gitee Pages，内容用 `PRIVACY.md`）
- [ ] 商店截图、512 图标、应用描述
- [ ] 真机验收（尤其 CSV 导出，模拟器上系统文件选择器点不稳）
- [ ] 建 Gitee / GitHub 空仓库并关联 remote（步骤见 [PUBLISH.md](PUBLISH.md)）
- [ ] 配 `GITEE_TOKEN` / `GITHUB_TOKEN` 后跑 `scripts/publish.ps1`

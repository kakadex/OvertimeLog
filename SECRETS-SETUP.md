# 发布签名配置

本应用的安装包**必须用固定的一把正式 keystore 签名**。原因不是洁癖，而是直接影响用户：

- 签名不一致时，Android 会把它当成另一个应用，覆盖安装会被拒绝；
- 用户只能卸载重装，**本地数据会一起丢失**（加班记录、调休、薪资规则都在应用私有目录）。

所以发布流程里：没有正式签名就**不出包**，`scripts/release.ps1` 会直接报错，
`app/build.gradle.kts` 也不会偷偷回退到调试签名。

仓库里不含任何签名材料：`signing.properties`、`*.jks` / `*.keystore` 都在 `.gitignore` 里。

---

## 1. 本地构建

项目根目录放 `signing.properties`（不入库）：

```properties
storeFile=<keystore 文件的绝对路径>
storePassword=<密钥库口令>
keyAlias=<别名>
keyPassword=<密钥口令>
```

然后：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\release.ps1
```

只想在本机临时跑一个 release 包、不在意签名一致时，可以显式开后门：

```powershell
./gradlew :app:assembleRelease -PallowDebugSign=true
```

> 用这个参数打出来的包签名与正式版不同，**不要发给别人、不要上架**。

## 2. 换一台机器

**把原来的 keystore 整份复制过去**，再按上面格式写 `signing.properties` 即可。
必须是同一把钥匙；重新生成等于换签名，后果见第 4 节。

## 3. 只有新项目 / 钥匙确实丢了才重新生成

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore overtimelog-release.jks -alias overtimelog `
  -keyalg RSA -keysize 2048 -validity 10000
```

把生成的 keystore 放到项目外的安全目录（不要放进仓库），再写 `signing.properties`。

## 4. 持续集成（可选）

如果要走 GitHub Actions / Gitee Go，需要 4 个 secret：

| Secret | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 base64（`base64 -w0 release.jks`） |
| `ANDROID_STORE_PASSWORD` | 密钥库口令 |
| `ANDROID_KEY_PASSWORD` | 密钥口令 |
| `ANDROID_KEY_ALIAS` | 别名（本项目为 `overtimelog`） |

没配 `ANDROID_KEYSTORE_BASE64` 时，CI 应该直接失败，不要产出调试签名的包。

## 5. 换钥匙或丢钥匙的后果

| 情况 | 后果 |
|---|---|
| 换了 keystore | 所有已安装用户升级失败，只能卸载重装，本地数据丢失 |
| keystore 丢失 | 再也无法为已发布版本出升级包，只能换包名重新开始 |

**务必把 keystore 与 `signing.properties` 一起离线备份**（加密 U 盘、私有网盘），
不要放进任何代码仓库，也不要放进会同步到公开位置的目录。

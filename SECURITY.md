# 安全说明

## 数据存储

- 所有数据（加班记录、薪资规则、调休）保存在应用私有目录的 SQLite 数据库与 DataStore 中
- 网络权限只用于检查更新（读取 Gitee / GitHub 的公开版本号），不上传任何数据，不接入统计 / 广告 SDK
- CSV 导出通过系统文件选择器（SAF）完成，不申请存储权限

## 敏感信息

- 仓库不含任何签名材料：`signing.properties`、`*.jks`、`*.keystore` 均在 `.gitignore` 中
- 发布签名配置见 [SECRETS-SETUP.md](SECRETS-SETUP.md)
- 示例配置只保留 `.example` 形式的占位符

## 依赖

- 依赖全部来自 Maven Central / Google Maven（通过国内镜像加速）
- 版本统一在 `gradle/libs.versions.toml` 管理，升级后需重跑发布冒烟

## 报告问题

发现安全问题时，请通过项目仓库的 Issue 或维护者公开邮箱私下联系，不要在公开 Issue 里贴可利用细节。

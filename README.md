# 加班记（OvertimeLog）

一个 Android 端的个人加班记录与加班费估算工具：离线优先、数据只存本机、计算规则可配置。

本项目按《加班记项目需求文档 V1.0》与《加班记 Android 架构设计文档 V1.0》落地，当前版本 **1.0.7**。以 [MIT 协议](LICENSE) 开源。

## 下载

| 渠道 | 地址 |
| --- | --- |
| Gitee Releases（国内推荐） | https://gitee.com/kakadex/overtimelog/releases/tag/v1.0.7 |
| GitHub Releases | https://github.com/kakadex/OvertimeLog/releases/tag/v1.0.7 |

不确定机型就下载 `OvertimeLog-1.0.7-android-arm64-v8a.apk`（绝大多数手机）；
校验值见发布页的 `SHA256SUMS.txt`，`latest.txt` 提供机器可读的最新版本信息。
应用内「设置 → 更新」也能检查新版本，并直接下载安装对应版本。

## 现在能做什么

- 记录加班：日期、起止时间（支持跨天）、休息扣除、加班类型、备注
- 自动计算：时长、小时工资、按倍率算加班费、按 1:1 / 1:1.5 / 手动折算调休
- 录入页实时预览，保存与预览共用同一套计算引擎，看到多少就存多少
- 记录列表：按月 / 类型 / 结算状态筛选，备注搜索，删除二次确认 + 撤销，单条切换结算状态
- 首页概览：本月总时长、预计加班费、已结算 / 未结算、加班天数、调休余额、最近记录
- 日历：月历按加班类型打点，点某天看明细或补录；格子标「休 / 班」区分休息日、法定节假日与调休补班，并显示节日名；内置 2025–2026 年放假安排，可长按某天手动标记；右上角「今」按钮一键回到今天
- 更新：设置 → 更新里一键检查新版本（优先 Gitee、连不上用 GitHub，启动时可静默检查），可在应用内直接下载对应架构的 APK 并安装；同一区块还能更新某一年的放假安排，并标明数据来源
- 统计：月度合计与按类型拆分、年度 12 个月柱状图与逐月明细，支持按结算状态（全部 / 未结算 / 已结算）筛选，并可一键把整月记录统一标记为已结算 / 未结算
- 调休：余额、使用记录、新增 / 删除（余额不足会先提示再确认）
- 设置：薪资规则（月薪 / 时薪、计薪天数、日工时、各类型倍率）、录入默认值（默认上下班时间、默认休息扣除、休息扣除单位「小时 / 分钟」）、主题、导出 CSV、清除数据、更新、隐私说明
- 规则快照：每条记录保存当时的规则与算出的时薪 / 倍率，改规则不会悄悄改历史金额

## 还没有做（按 PRD 优先级）

- 提醒（WorkManager）、桌面小组件
- 备份与恢复（ZIP / 加密）
- 图表库版统计（当前是 Compose 自绘条形图）、Excel 导出
- 应用锁（PIN / 生物识别）
- 多工作 / 项目维度、云同步
- 首次使用引导、多语言

## 技术栈

| 关注点 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.4 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + 单向数据流 + Repository + UseCase |
| 数据库 | Room 2.8（KSP 生成代码） |
| 偏好 | DataStore Preferences |
| 导航 | Navigation Compose |
| 异步 | Coroutines + Flow |
| 构建 | Gradle 9.3.1 + AGP 9.1 + Version Catalog，minSdk 26 / targetSdk 37 |
| 测试 | JUnit 4（引擎与用例）、kotlinx-coroutines-test |
| 协议 | MIT |

## 项目结构

```
app/                      Android 应用：组装、导航、页面、数据实现
core/model/               领域模型（纯 Kotlin/JVM）
core/calculation/         计算引擎（纯 Kotlin/JVM，不依赖 Android）
scripts/                  图标生成、本地测试打包等开发脚本
docs/                     实现说明、数据库设计
```

app 内部按包分层：`domain/`（仓库接口、用例）、`data/`（Room、DataStore、导出、更新检查）、`ui/`（主题、组件、导航）、`feature/`（概览、记录、日历、统计、调休、设置）。

## 构建与运行

前置：JDK 17+、Android SDK（compileSdk 37）、`local.properties` 指向本机 SDK。

```bash
./gradlew :app:assembleDebug          # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 应用层单测（用例、CSV 导出、休息单位换算）
./gradlew :core:calculation:test      # 计算引擎单测
```

安装到已连接的设备或模拟器：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```


## 包体积

同一份代码，三种打包方式实测（Pixel 34 模拟器，AGP 9.1 / R8）：

| 产物 | 大小 | 说明 |
| --- | --- | --- |
| debug（`assembleDebug`） | 13.3 MB | 不裁剪、不压缩，还带了仅调试用的 Compose 预览工具 |
| release 未压缩（`isMinifyEnabled = false`） | 9.3 MB | 只是去掉了调试工具与调试信息 |
| release + R8 + 资源裁剪（当前配置） | **1.8 MB** | 删除未引用代码与资源，已装模拟器验证功能正常 |

体积几乎全在代码（dex）上：debug 包 12.5 MB 代码对 0.45 MB 资源、0.06 MB 原生库。原因是 Compose、Material 3、Navigation、Room、DataStore、Lifecycle 这些库的类在 debug 下全部保留，加上方法数超限被拆成 20 个 dex。

发布建议：上架用 AAB（`bundleRelease`），Google Play 会按屏幕密度、ABI、语言拆分，用户实际下载量通常比 APK 再小一半以上。

## 测试现状

- 计算引擎：17 个用例，覆盖跨天、休息扣除、三种倍率、月薪 / 时薪、四舍五入、24 小时边界、非法输入
- 用例层：10 个用例，覆盖校验、规则快照、调休余额、更新与删除、撤销删除
- 休息单位换算：6 个用例，覆盖小时小数、分钟、非法与越界输入
- CSV：2 个用例，覆盖 BOM、表头、RFC 4180 转义、空结果
- 更新检查：15 个用例，覆盖 tag 解析、版本大小比较、新版本 / 同版本 / 旧版本判定、静默失败标记、按 CPU 架构选包
- 日历性质：10 个用例，覆盖工作日 / 周末 / 法定节假日 / 调休补班识别，以及「手动 > 远程 > 内置 > 星期几」的优先级
- 发布包冒烟：自动验证 R8 压缩后的包能完成"配规则 → 记一笔 → 金额正确"

## 数据与隐私

所有记录、薪资规则、调休数据都只保存在本机应用私有目录，不上传、不同步；CSV 导出通过系统文件选择器（SAF）完成，不申请存储权限。
应用只用网络做两件事：检查更新时读取 Gitee / GitHub 的公开版本号，以及下载你主动点的新版本 APK（Gitee 优先，连不上用 GitHub）。启动时自动检查可以在设置里关掉，关掉后启动不联网。安装包下在应用私有目录，经系统安装界面确认安装，不申请存储权限。详见 [PRIVACY.md](PRIVACY.md)。

加班费仅为个人参考估算，不代表公司实际结算，也不构成劳动法依据。



## 开源协议

[MIT](LICENSE) © 2026 kakadex。

应用内「设置 → 开源许可」也可以在手机上查看协议正文。
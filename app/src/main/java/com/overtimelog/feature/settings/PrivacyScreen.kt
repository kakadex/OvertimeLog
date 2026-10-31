package com.overtimelog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.overtimelog.ui.components.SectionCard

/** 隐私说明。内容与 PRD 7.3 / FR-022 对齐：本地存储、无网络、权限最小化。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私说明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "数据存在哪里") {
                    Text(
                        text = "加班记录、薪资规则、调休记录全部保存在本机的应用私有目录（SQLite 数据库）里，"
                            + "不经过任何服务器。卸载应用或清除数据后，这些内容会一起删除。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(title = "不会收集什么") {
                    Text(
                        text = "不采集姓名、手机号、位置、通讯录、短信；不接入统计分析 SDK，不上传崩溃日志；"
                            + "检查更新时只向 Gitee / GitHub 读取公开的版本号，不发送任何记录或个人数据。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(title = "检查更新") {
                    Text(
                        text = "应用只用网络做一件事：连接 Gitee 或 GitHub 的公开接口，读取最新版本号。"
                            + "优先读 Gitee，连不上时改用 GitHub；不登录、不携带任何标识。"
                            + "可以在设置里关掉「启动时自动检查更新」，关掉后只有你主动点「检查更新」才会联网。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(title = "权限") {
                    Text(
                        text = "导出 CSV 时使用系统文件选择器，只获得你主动选中的那一个文件的写入权限，"
                            + "不申请读写存储的广泛权限。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(title = "提醒（后续版本）") {
                    Text(
                        text = "如果开启加班提醒，会申请通知权限；拒绝通知不影响记录、统计、导出等核心功能。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard(title = "金额仅供参考") {
                    Text(
                        text = "加班费是按你填写的小时工资和倍率估算的结果，不代表公司实际结算金额，"
                            + "也不构成劳动法或薪酬结算依据。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

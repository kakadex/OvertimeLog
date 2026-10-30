package com.overtimelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overtimelog.data.settings.AppSettings
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.navigation.OvertimeNavHost
import com.overtimelog.ui.theme.OvertimeTheme
import kotlinx.coroutines.launch

/**
 * 应用唯一 Activity：读设置里的主题、处理首次启动的隐私说明，然后把导航交给 Compose。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OvertimeApp).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            OvertimeTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    OvertimeNavHost(container = container)
                }
                if (!settings.hasSeenPrivacyNotice) {
                    FirstLaunchPrivacyDialog(container = container)
                }
            }
        }
    }
}

/** 首次启动的隐私说明（PRD FR-022）。读完点"知道了"就写进 DataStore，之后不再弹。 */
@Composable
private fun FirstLaunchPrivacyDialog(container: AppContainer) {
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { /* 必须点按钮确认，避免误关后不知道数据存哪 */ },
        title = { Text("先说清楚：数据只存本机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("加班记不联网、不上传任何数据。你的加班记录、薪资规则、调休记录都保存在这台手机上。")
                Text("卸载应用或清除数据后，内容会一起删除，建议定期在设置里导出 CSV。")
                Text("加班费是按你填写的规则估算的，仅供参考，不代表公司实际结算。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch { container.settingsRepository.setPrivacyNoticeSeen() }
                },
            ) { Text("知道了") }
        },
    )
}

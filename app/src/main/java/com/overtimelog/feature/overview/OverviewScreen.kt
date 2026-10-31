package com.overtimelog.feature.overview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.BuildConfig
import com.overtimelog.di.AppContainer
import com.overtimelog.domain.model.InstallState
import com.overtimelog.domain.model.RemoteRelease
import com.overtimelog.domain.model.UpdateState
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.label
import com.overtimelog.ui.common.openWebPage
import com.overtimelog.ui.common.supportedAbis
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.MonthSwitcher
import com.overtimelog.ui.components.RecordCard
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.StatTile
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 首页/概览：本月时长、预计加班费、调休余额、最近记录。
 *
 * 这是用户打开 App 的第一屏，所以把"这个月加班多久、能拿多少钱、还有多少调休"放在最上面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    container: AppContainer,
    onOpenRecord: (Long) -> Unit,
    onOpenAllRecords: () -> Unit,
    onOpenCompTime: () -> Unit,
) {
    val viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary
    val isCurrentMonth = state.month == YearMonth.now()

    // 启动时静默检查更新的结果：有新版本才在这一屏顶部挂一条提示，不弹窗、不打断记录
    val updateState by container.updateChecker.state.collectAsStateWithLifecycle()
    val installState by container.apkInstaller.state.collectAsStateWithLifecycle()
    val availableUpdate = (updateState as? UpdateState.Available)?.release
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dismissedUpdateTag by rememberSaveable { mutableStateOf<String?>(null) }

    // 下载完成 → 直接拉起系统安装界面
    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { container.apkInstaller.resetIfFinished() }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("加班记") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部多留 24dp：最后的说明文字不被手势条/导航栏压住（此页无底栏 padding 时也安全）
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val updateToShow = availableUpdate?.takeIf { it.tag != dismissedUpdateTag }
            if (updateToShow != null) {
                item {
                    UpdateAvailableCard(
                        release = updateToShow,
                        installState = installState,
                        onDownload = {
                            val candidates = container.apkInstaller.candidatesFor(updateToShow, supportedAbis())
                            if (candidates.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("没有找到可下载的安装包，请到设置 → 下载里打开下载页")
                                }
                            } else {
                                container.apkInstaller.download(updateToShow, supportedAbis(), candidates) { intent ->
                                    runCatching { installLauncher.launch(intent) }
                                }
                            }
                        },
                        onOpenPage = {
                            if (!context.openWebPage(updateToShow.pageUrl)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("没有找到可用的浏览器，请到设置里手动打开发布页")
                                }
                            }
                        },
                        onDismiss = { dismissedUpdateTag = updateToShow.tag },
                    )
                }
            }

            item {
                MonthSwitcher(
                    month = state.month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            item {
                SectionCard(title = "本月概览") {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = Durations.hoursText(summary.totalMinutes),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "共 ${summary.recordCount} 条记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "预计加班费 ${Money.format(summary.totalAmountCents)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "已结算",
                                value = Money.format(summary.settledAmountCents),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "未结算",
                                value = Money.format(summary.unsettledAmountCents),
                                modifier = Modifier.weight(1f),
                                accent = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "加班天数",
                        value = "${summary.overtimeDays} 天",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "调休余额",
                        value = Durations.text(state.balance.balanceMinutes),
                        supporting = if (state.balance.balanceMinutes < 0) "已透支，点开看明细" else "点击查看明细",
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenCompTime),
                    )
                }
            }

            item {
                SectionCard(
                    title = "最近记录",
                    trailing = {
                        TextButton(onClick = onOpenAllRecords) { Text("全部") }
                    },
                ) {
                    if (state.recent.isEmpty()) {
                        EmptyState("这个月还没有加班记录，到「日历」页点「这天记一笔」开始")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.recent.forEach { record ->
                                RecordCard(record = record, onClick = { onOpenRecord(record.id) })
                            }
                        }
                    }
                }
            }

            if (!isCurrentMonth) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = viewModel::backToCurrentMonth) {
                            Text("回到本月")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.width(0.dp))
                Text(
                    text = "金额是按你自己设置的薪资规则估算的，仅供参考，不代表公司实际结算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 顶部的新版本提示条。
 *
 * 启动时静默检查发现新版本才出现；"忽略"只藏到这次退出应用为止，不清除检查结果，
 * 所以进设置页仍然能看到版本号和下载入口。
 */
@Composable
private fun UpdateAvailableCard(
    release: RemoteRelease,
    installState: InstallState,
    onDownload: () -> Unit,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = installState is InstallState.Downloading
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, bottom = 6.dp, end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发现新版本 ${release.version}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = when (installState) {
                            is InstallState.Downloading -> "正在下载 ${installState.percentText}"
                            InstallState.Installing -> "正在安装…"
                            is InstallState.Failed -> installState.message
                            else -> "当前 ${BuildConfig.VERSION_NAME} · 来自 ${release.source.label()}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (installState is InstallState.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
                TextButton(onClick = onDownload, enabled = !downloading) {
                    Text(if (downloading) "下载中" else "立即更新")
                }
                TextButton(onClick = onDismiss) { Text("忽略") }
            }
            if (downloading) {
                LinearProgressIndicator(
                    progress = { if (installState.percent >= 0) installState.percent / 100f else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, bottom = 6.dp),
                )
            }
            if (installState is InstallState.Failed) {
                TextButton(onClick = onOpenPage) { Text("打开发布页手动下载") }
            }
        }
    }
}

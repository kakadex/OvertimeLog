package com.overtimelog.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.data.settings.ThemeMode
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.label
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.SectionCard
import java.time.LocalDate

/**
 * 设置页：薪资规则入口、录入默认值、主题、导出、清除数据、隐私说明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenSalaryRule: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "薪资规则") {
                    Column {
                        if (rule == null) {
                            Text("还没有配置，加班费会算不出来。", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val current = rule!!
                            InfoRow(
                                "计算方式",
                                if (current.calculationMode == SalaryMode.MONTHLY) "按月薪" else "按时薪",
                            )
                            if (current.calculationMode == SalaryMode.MONTHLY) {
                                InfoRow("月薪", Money.format(current.monthlySalaryCents))
                                InfoRow("计薪天数", current.payDaysPerMonth.toPlainString())
                                InfoRow("日标准工时", current.standardHoursPerDay.toPlainString() + " 小时")
                            } else {
                                InfoRow("时薪", Money.hourlyRate(current.hourlyRateCents))
                            }
                            InfoRow(
                                "倍率",
                                "工作日 ${current.workdayMultiplierBp / 1000.0} / " +
                                    "休息日 ${current.restDayMultiplierBp / 1000.0} / " +
                                    "节假日 ${current.holidayMultiplierBp / 1000.0}",
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenSalaryRule) {
                            Text(if (rule == null) "去配置" else "修改薪资规则")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "录入默认值") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认休息扣除", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        viewModel.setDefaultBreakMinutes(
                                            (settings.defaultBreakMinutes - 15).coerceAtLeast(0),
                                        )
                                    },
                                ) { Text("-15") }
                                Text(
                                    text = "${settings.defaultBreakMinutes} 分钟",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(84.dp),
                                )
                                TextButton(
                                    onClick = {
                                        viewModel.setDefaultBreakMinutes(
                                            (settings.defaultBreakMinutes + 15).coerceAtMost(1439),
                                        )
                                    },
                                ) { Text("+15") }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("默认加班类型", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OvertimeType.entries.forEach { type ->
                                FilterChip(
                                    selected = settings.defaultOvertimeType == type,
                                    onClick = { viewModel.setDefaultType(type) },
                                    label = { Text(type.label()) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "跟随系统",
                            ThemeMode.LIGHT to "浅色",
                            ThemeMode.DARK to "深色",
                        ).forEach { (mode, labelText) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = { Text(labelText) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "数据") {
                    Column {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("加班记录_${LocalDate.now()}.csv")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("导出 CSV（全部记录）")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("清除全部数据", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "导出走系统文件选择器，不申请存储权限。清除后无法恢复，建议先导出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "隐私与关于") {
                    Column {
                        Text(
                            text = "所有加班记录只保存在这台手机上，应用不联网、不上传任何数据。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onOpenPrivacy) { Text("查看隐私说明") }
                        InfoRow("版本", "0.1.0（MVP）")
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清除全部数据？",
            text = "所有加班记录、调休记录都会被删除，且无法恢复。建议先导出 CSV。",
            confirmText = "确认清除",
            onConfirm = {
                viewModel.clearAllData()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

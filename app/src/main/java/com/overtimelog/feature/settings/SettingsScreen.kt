package com.overtimelog.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SalaryMode
import com.overtimelog.data.settings.BreakUnit
import com.overtimelog.data.settings.ThemeMode
import com.overtimelog.data.update.ReleaseChannels
import com.overtimelog.domain.model.InstallState
import com.overtimelog.domain.model.UpdateState
import com.overtimelog.BuildConfig
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.BreakTime
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.SimpleHours
import com.overtimelog.ui.common.label
import com.overtimelog.ui.common.openWebPage
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.TimePickerModal
import kotlinx.coroutines.launch
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
    onOpenLicense: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val holidaySync by viewModel.holidaySync.collectAsStateWithLifecycle()
    val holidayYears by viewModel.holidayYears.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<DefaultTimeTarget?>(null) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var breakInput by remember { mutableStateOf("") }
    var showSimpleHoursDialog by remember { mutableStateOf(false) }
    var simpleHoursInput by remember { mutableStateOf("3") }

    // 设备上没有浏览器时给一句提示，避免点了"发布页"像没反应
    val openPage: (String) -> Unit = { url ->
        if (!context.openWebPage(url)) {
            scope.launch { snackbarHostState.showSnackbar("没有找到可用的浏览器，请手动访问发布页") }
        }
    }

    // 默认上下班时间决定的默认工时长（跨天按次日算），让用户一眼看到改时间的效果。
    val defaultStartMinutes = settings.defaultStartTime.hour * 60 + settings.defaultStartTime.minute
    val defaultEndMinutes = settings.defaultEndTime.hour * 60 + settings.defaultEndTime.minute
    val defaultRawMinutes = if (defaultEndMinutes <= defaultStartMinutes) {
        defaultEndMinutes + 24 * 60 - defaultStartMinutes
    } else {
        defaultEndMinutes - defaultStartMinutes
    }
    val defaultDurationText = Durations.text((defaultRawMinutes - settings.defaultBreakMinutes).coerceAtLeast(0))

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportCsv(uri.toString())
    }

    // 下载完成 → 直接拉起系统安装界面（需要用户在本机允许"安装未知应用"）
    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.resetInstallState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("设置") },
            )
        },
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
                SectionCard(
                    title = "录入默认值",
                    // 「简单记模式」按需求放在"录入默认值"的右侧：标题一行两边各一个入口
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "简单记模式",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = settings.simpleEntryMode,
                                onCheckedChange = viewModel::setSimpleEntryMode,
                            )
                        }
                    },
                ) {
                    Column {
                        // 开着简单记时，默认上下班时间/休息扣除暂时用不上，
                        // 折叠成一行说明，避免设置页出现一整块"现在没意义"的选项。
                        if (settings.simpleEntryMode) {
                            Text(
                                text = "简单记已开启：点「这天记一笔」只填当天加班小时数，不填上下班时间和休息扣除。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("默认加班小时数", style = MaterialTheme.typography.bodyMedium)
                                TextButton(
                                    onClick = {
                                        simpleHoursInput = settings.defaultSimpleHours
                                        showSimpleHoursDialog = true
                                    },
                                ) {
                                    Text("${settings.defaultSimpleHours} 小时")
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingTime = DefaultTimeTarget.START }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认上班时间", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = DateTexts.time(settings.defaultStartTime),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingTime = DefaultTimeTarget.END }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("默认下班时间", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = DateTexts.time(settings.defaultEndTime),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = "默认工时长 $defaultDurationText（跨天按次日计算），实际录入时可以改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("默认休息扣除", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "按${BreakTime.unitLabel(settings.breakUnit)}填写",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    breakInput = BreakTime.format(
                                        settings.defaultBreakMinutes,
                                        settings.breakUnit,
                                    )
                                    showBreakDialog = true
                                },
                            ) {
                                Text(
                                    BreakTime.format(settings.defaultBreakMinutes, settings.breakUnit) +
                                        " " + BreakTime.unitLabel(settings.breakUnit),
                                )
                            }
                        }

                        Text("休息扣除单位", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(BreakUnit.HOURS to "小时", BreakUnit.MINUTES to "分钟").forEach { (unit, text) ->
                                FilterChip(
                                    selected = settings.breakUnit == unit,
                                    onClick = { viewModel.setBreakUnit(unit) },
                                    label = { Text(text) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("默认加班类型", style = MaterialTheme.typography.bodyMedium)
                        // 四个选项一行放不下（"自定义"会被挤出屏幕），改成自动换行并整体居中：
                        // 放不下时后面的选项整块换到下一行，不做横向压缩。
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            text = "所有加班记录只保存在这台手机上；只在检查更新时读取公开的版本号，不上传任何数据。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onOpenPrivacy) { Text("查看隐私说明") }
                        TextButton(onClick = onOpenLicense) { Text("开源许可（MIT）") }
                        InfoRow("版本", "${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）")
                    }
                }
            }

            item {
                SectionCard(title = "更新") {
                    Column {
                        val checking = updateState == UpdateState.Checking
                        Text(
                            text = when (val state = updateState) {
                                UpdateState.Idle ->
                                    "当前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）"
                                UpdateState.Checking -> "正在检查…"
                                UpdateState.UpToDate -> "已是最新版本 ${BuildConfig.VERSION_NAME}"
                                is UpdateState.Available ->
                                    "发现新版本 ${state.release.version}（来自 ${state.release.source.label()}）"
                                is UpdateState.Failed ->
                                    if (state.silent) {
                                        "当前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）"
                                    } else {
                                        "检查失败：${state.message}"
                                    }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::checkUpdate,
                            enabled = !checking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (checking) "正在检查…" else "检查更新")
                        }

                        (updateState as? UpdateState.Available)?.let { available ->
                            Spacer(Modifier.height(8.dp))
                            val install = installState
                            val downloading = install is InstallState.Downloading
                            Button(
                                onClick = {
                                    viewModel.downloadUpdate(available.release) { intent ->
                                        runCatching { installLauncher.launch(intent) }
                                    }
                                },
                                enabled = !downloading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    when {
                                        downloading -> "正在下载 ${install.percentText}"
                                        install is InstallState.Installing -> "正在安装…"
                                        else -> "下载并安装 ${available.release.version}"
                                    },
                                )
                            }
                            if (downloading) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { if (install.percent >= 0) install.percent / 100f else 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            install.let { current ->
                                if (current is InstallState.Failed) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = current.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            TextButton(
                                onClick = { openPage(available.release.pageUrl) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("打开发布页（手动下载）")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // ---- 放假安排：国家没有官方接口，说明清楚数据从哪来 ----
                        Text("更新放假安排", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.syncHolidays() },
                            enabled = !holidaySync.running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (holidaySync.running) "正在更新…"
                                else "更新 ${holidaySync.year} 年放假安排",
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "数据来源：国务院办公厅每年发布的《关于部分节假日安排的通知》，"
                                + "经公开接口 timor.tech 整理成可读格式后下载到本机。"
                                + "应用内置 2025、2026 年数据，断网也能看；"
                                + "其他年份或官方临时调整，可点上面的按钮获取，也能在日历里长按某天手动改。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val cachedYears = holidayYears.sorted()
                        if (cachedYears.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "已下载到本机：" + cachedYears.joinToString("、") { "$it 年" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        holidaySync.message?.let { message ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (message.startsWith("已更新")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("启动时自动检查更新", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "只在启动时读取公开的版本号，不发记录、不上传数据。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = settings.updateCheckOnStartup,
                                onCheckedChange = viewModel::setUpdateCheckOnStartup,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "下载") {
                    Column {
                        Text(
                            text = "发布页可以下载最新 APK、历史版本和 SHA-256 校验清单。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openPage(ReleaseChannels.GITEE_RELEASES_PAGE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Gitee 发布页（国内推荐）")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openPage(ReleaseChannels.GITHUB_RELEASES_PAGE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("GitHub 发布页")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "检查更新优先读 Gitee，连不上时自动改用 GitHub。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

    editingTime?.let { target ->
        TimePickerModal(
            initialTime = if (target == DefaultTimeTarget.START) {
                settings.defaultStartTime
            } else {
                settings.defaultEndTime
            },
            title = if (target == DefaultTimeTarget.START) "默认上班时间" else "默认下班时间",
            onConfirm = { time ->
                if (target == DefaultTimeTarget.START) {
                    viewModel.setDefaultTimes(time, settings.defaultEndTime)
                } else {
                    viewModel.setDefaultTimes(settings.defaultStartTime, time)
                }
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }

    if (showSimpleHoursDialog) {
        val parsed = SimpleHours.parseMinutes(simpleHoursInput)
        AlertDialog(
            onDismissRequest = { showSimpleHoursDialog = false },
            title = { Text("默认加班小时数") },
            text = {
                OutlinedTextField(
                    value = simpleHoursInput,
                    onValueChange = { if (it.length <= 6) simpleHoursInput = it },
                    label = { Text("小时数（可带小数）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = simpleHoursInput.isNotBlank() && parsed == null,
                    supportingText = {
                        Text(
                            if (parsed != null) "= ${Durations.text(parsed)}，新记录会预填这个数"
                            else "填 0 到 24 之间的小时数，例如 2 或 2.5",
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (parsed != null) {
                            viewModel.setDefaultSimpleHours(simpleHoursInput.trim())
                            showSimpleHoursDialog = false
                        }
                    },
                    enabled = parsed != null,
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSimpleHoursDialog = false }) { Text("取消") }
            },
        )
    }

    if (showBreakDialog) {
        val parsed = BreakTime.parse(breakInput, settings.breakUnit)
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            title = { Text("默认休息扣除") },
            text = {
                OutlinedTextField(
                    value = breakInput,
                    onValueChange = { if (it.length <= 6) breakInput = it },
                    label = { Text("时长（${BreakTime.unitLabel(settings.breakUnit)}）") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (settings.breakUnit == BreakUnit.HOURS) {
                            KeyboardType.Decimal
                        } else {
                            KeyboardType.Number
                        },
                    ),
                    singleLine = true,
                    isError = breakInput.isNotBlank() && parsed == null,
                    supportingText = {
                        Text(
                            BreakTime.hint(breakInput, settings.breakUnit)
                                ?: "例如 0.5 小时 = 30 分钟",
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let {
                            viewModel.setDefaultBreakMinutes(it)
                            showBreakDialog = false
                        }
                    },
                    enabled = parsed != null,
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBreakDialog = false }) { Text("取消") }
            },
        )
    }
}

private enum class DefaultTimeTarget { START, END }

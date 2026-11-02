package com.overtimelog.feature.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.CompTimeMode
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.data.settings.BreakUnit
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.BreakTime
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.SimpleHours
import com.overtimelog.ui.common.label
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.DatePickerModal
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.TimePickerModal
import java.time.LocalTime

/**
 * 新增/编辑记录页。
 *
 * 交互原则（PRD 7.4）：核心流程不超过 3 步 —— 默认值已经按设置填好，
 * 大多数情况用户只需要确认时间、点保存。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditScreen(
    container: AppContainer,
    recordId: Long?,
    initialDate: java.time.LocalDate? = null,
    onDone: () -> Unit,
    onOpenSalaryRule: () -> Unit,
) {
    val viewModel: RecordEditViewModel = viewModel(
        key = "record-edit-${recordId ?: "new"}-${initialDate ?: ""}",
        factory = RecordEditViewModel.factory(container, recordId, initialDate),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDatePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<TimeTarget?>(null) }
    var overdraftMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordEditEvent.Saved -> onDone()
                is RecordEditEvent.Deleted -> onDone()
                is RecordEditEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is RecordEditEvent.AskOverdraft -> overdraftMessage = event.message
            }
        }
    }

    Scaffold(
        topBar = {
            // statusBarsPadding：全面屏状态栏会压住标题和返回键（主 Tab 各页都加了，二级页同样要）
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text(if (state.isEditing) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 新增时才给切换入口：编辑老记录按它自己的方式来，避免改个备注把数据形态改了
                    if (!state.isEditing) {
                        TextButton(onClick = { viewModel.onSimpleModeToggle(!state.simpleMode) }) {
                            Text(if (state.usesSimpleEntry) "计时" else "简单记")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // navigationBarsPadding：全面屏手势条/虚拟导航栏会盖住贴底的按钮（部分国产 ROM 上
            // 保存按钮下半截被挡）。让底栏整体抬到导航栏上方，按钮任何机型都完整可点。
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // 实时摘要：不管页面滚到哪里，都能看到"这次加班多久、大概多少钱"。
                    state.preview?.let { preview ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${Durations.text(preview.durationMinutes)} · ${preview.multiplierBp / 1000.0} 倍",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = Money.format(preview.amountCents) +
                                    if (preview.compTimeMinutes > 0) " · 调休 ${Durations.text(preview.compTimeMinutes)}" else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 删除只在编辑已有记录时出现；新增时没有可删的东西。
                        if (state.isEditing) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                enabled = !state.saving,
                                modifier = Modifier.height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("删除记录")
                            }
                        }
                        Button(
                            onClick = { viewModel.save() },
                            enabled = !state.saving && !state.loading,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(20.dp).height(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.isEditing) "保存修改" else "保存")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部多留 24dp：最后一项（预览卡）不被手势条/导航栏压住
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // 简单记模式：日期 + 一个"加班小时数"输入框，没有上下班时间和休息扣除。
                // 计时模式的"时间"卡片、休息扣除输入框都不再出现，减少一整屏的输入量。
                if (state.usesSimpleEntry) {
                    SectionCard(title = "时间") {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDatePicker = true }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("日期", style = MaterialTheme.typography.bodyMedium)
                                Text(DateTexts.full(state.date), color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.simpleHoursText,
                                onValueChange = viewModel::onSimpleHoursChange,
                                label = { Text("当天加班小时数") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                isError = state.simpleHoursText.isNotBlank() && state.simpleMinutes == null,
                                supportingText = { Text(SimpleHours.hint(state.simpleHoursText)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "简单记只算总时长，不记录上下班时间；想要按起止时间算，请在右上角切回「计时」。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    SectionCard(title = "时间") {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDatePicker = true }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("日期", style = MaterialTheme.typography.bodyMedium)
                                Text(DateTexts.full(state.date), color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TimeRow("开始时间", state.start) { editingTime = TimeTarget.START }
                                TimeRow("结束时间", state.end) { editingTime = TimeTarget.END }
                            }
                            if (!state.end.isAfter(state.start)) {
                                Text(
                                    text = "结束时间早于开始时间，将按次日计算",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.breakText,
                                onValueChange = viewModel::onBreakChange,
                                label = { Text("休息扣除（${BreakTime.unitLabel(state.breakUnit)}）") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (state.breakUnit == BreakUnit.HOURS) {
                                        KeyboardType.Decimal
                                    } else {
                                        KeyboardType.Number
                                    },
                                ),
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        BreakTime.hint(state.breakText, state.breakUnit)
                                            ?: "单位可在「设置 → 录入默认值」里切换",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "加班类型") {
                    Column {
                        // 用 FlowRow 自动换行：原来 LazyRow 只能横向滚动，
                        // 屏幕窄或系统字号大时「自定义」会被截掉半个字（实际反馈过）。
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OvertimeType.entries.forEach { type ->
                                FilterChip(
                                    selected = state.type == type,
                                    onClick = { viewModel.onTypeChange(type) },
                                    label = { Text(type.label()) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "倍率由「设置 → 薪资规则」决定，改规则不会影响已经存下的记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "怎么算") {
                    Column {
                        // 同「加班类型」：FlowRow 自动换行，避免「手动填写」被截断
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompTimeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.compTimeMode == mode,
                                    onClick = { viewModel.onCompTimeModeChange(mode) },
                                    label = { Text(mode.label()) },
                                )
                            }
                        }
                        if (state.compTimeMode == CompTimeMode.MANUAL) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.manualCompText,
                                onValueChange = viewModel::onManualCompChange,
                                label = { Text("手动填写调休分钟数") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.manualAmountText,
                            onValueChange = viewModel::onManualAmountChange,
                            label = { Text("手动改金额（元，可留空）") },
                            placeholder = { Text("留空则按规则自动计算") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "备注") {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::onNoteChange,
                        placeholder = { Text("今天为什么加班？写给以后的自己看") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("${state.note.length}/200") },
                    )
                }
            }

            item {
                SectionCard(title = "结算状态") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("已结算", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "钱已经发了 / 调休已经用掉了",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.settled, onCheckedChange = viewModel::onSettledChange)
                    }
                }
            }

            if (state.ruleMissing) {
                item {
                    SectionCard(title = "还没配置薪资规则") {
                        Column {
                            Text(
                                text = "要先在设置里填时薪或月薪，才能算出加班费。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = onOpenSalaryRule) { Text("去设置薪资规则") }
                        }
                    }
                }
            }

            state.preview?.let { preview ->
                item {
                    SectionCard(title = "预览") {
                        Column {
                            InfoRow("加班时长", Durations.text(preview.durationMinutes))
                            InfoRow("小时工资", Money.hourlyRate(preview.hourlyRateCents))
                            InfoRow("倍率", "${preview.multiplierBp / 1000.0} 倍")
                            InfoRow("预计加班费", Money.format(preview.amountCents))
                            if (preview.compTimeMinutes > 0) {
                                InfoRow("折算调休", Durations.text(preview.compTimeMinutes))
                            }
                            if (state.usesSimpleEntry && state.error != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = state.error.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (state.warning != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = state.warning.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = state.date,
            onConfirm = {
                viewModel.onDateChange(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    editingTime?.let { target ->
        TimePickerModal(
            initialTime = if (target == TimeTarget.START) state.start else state.end,
            title = if (target == TimeTarget.START) "开始时间" else "结束时间",
            onConfirm = { time ->
                if (target == TimeTarget.START) viewModel.onStartChange(time) else viewModel.onEndChange(time)
                editingTime = null
            },
            onDismiss = { editingTime = null },
        )
    }

    overdraftMessage?.let { message ->
        ConfirmDialog(
            title = "调休余额不足",
            text = message,
            confirmText = "仍然保存",
            onConfirm = {
                overdraftMessage = null
                viewModel.save(allowOverdraft = true)
            },
            onDismiss = { overdraftMessage = null },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除这条记录？",
            text = "这条加班记录和它折算的调休会一起删除，删除后只能重新录入。",
            confirmText = "删除",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private enum class TimeTarget { START, END }

@Composable
private fun TimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = DateTexts.time(time),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

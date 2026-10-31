package com.overtimelog.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.MonthSwitcher
import com.overtimelog.ui.components.RecordCard
import com.overtimelog.ui.components.color
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历页：月历上标出哪天有加班（按类型上色），点某天看当天明细。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    container: AppContainer,
    onOpenRecord: (Long) -> Unit,
    onAddRecordForDate: (LocalDate) -> Unit,
) {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<OvertimeRecord?>(null) }
    // 长按某天弹出的"标记为..."菜单
    var markTarget by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已删除 ${DateTexts.full(event.record.date)} 的加班记录",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.record)
                    }
                }
                is CalendarEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // 节假日数据更新结果用 Snackbar 提示，然后清掉，避免切页面又说一遍
    LaunchedEffect(syncState.message) {
        syncState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("日历") }) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthSwitcher(
                        month = state.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "长按某天可标记休息 / 补班",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = viewModel::syncHolidays,
                        enabled = !syncState.running,
                    ) {
                        Text(if (syncState.running) "更新中…" else "更新${state.month.year}年安排")
                    }
                }
            }

            item {
                Column {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        month = state.month,
                        selected = state.selectedDate,
                        markers = state.markers,
                        dayKinds = state.dayKinds,
                        onSelect = viewModel::selectDate,
                        onLongSelect = { date -> markTarget = date },
                    )
                    Spacer(Modifier.height(8.dp))
                    DayKindLegend()
                    if (!state.hasHolidayData) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${state.month.year} 年的放假安排还没内置，可以长按某天手动标记为休息 / 补班。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = DateTexts.day(state.selectedDate),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                state.selectedDayInfo?.let { info ->
                                    Spacer(Modifier.width(8.dp))
                                    DayKindBadge(info)
                                }
                            }
                            state.selectedDayInfo?.takeIf { it.name.isNotBlank() }?.let { info ->
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onAddRecordForDate(state.selectedDate) }) {
                            Text("这天记一笔")
                        }
                    }
                }
            }

            if (state.dayRecords.isEmpty()) {
                item { EmptyState("这天没有加班记录") }
            } else {
                items(state.dayRecords.size) { index ->
                    val record: OvertimeRecord = state.dayRecords[index]
                    RecordCard(
                        record = record,
                        onClick = { onOpenRecord(record.id) },
                        // 长按直接删（带二次确认和撤销），不用先点进编辑页
                        onLongClick = { deleteTarget = record },
                    )
                }
            }
        }
    }

    deleteTarget?.let { record ->
        ConfirmDialog(
            title = "删除这条记录？",
            text = "删除后可以在下方提示里点「撤销」找回。",
            confirmText = "删除",
            onConfirm = {
                viewModel.delete(record)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    markTarget?.let { date ->
        val current = state.dayKinds[date]
        MarkDayDialog(
            date = date,
            current = current,
            onPick = { kind ->
                viewModel.setDayOverride(date, kind)
                markTarget = null
            },
            onDismiss = { markTarget = null },
        )
    }
}

/**
 * 长按某天弹出的"标记为…"对话框。
 *
 * 用户在这里改的结果优先级最高，会覆盖内置数据和远程更新下来的安排，
 * 适合公司放假和法定安排不一致、或者某年数据还没公布的场景。
 */
@Composable
private fun MarkDayDialog(
    date: LocalDate,
    current: com.overtimelog.core.model.DayInfo?,
    onPick: (com.overtimelog.core.model.DayKind?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${DateTexts.full(date)} 是？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = current?.let { info ->
                        val label = when (info.kind) {
                            com.overtimelog.core.model.DayKind.HOLIDAY -> "法定节假日"
                            com.overtimelog.core.model.DayKind.REST_DAY -> "休息日"
                            com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> "调休补班"
                            com.overtimelog.core.model.DayKind.WORKDAY -> "工作日"
                        }
                        "现在：$label${if (info.name.isNotBlank()) " · ${info.name}" else ""}" +
                            if (info.manual) "（手动标记）" else ""
                    } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    com.overtimelog.core.model.DayKind.WORKDAY to "工作日",
                    com.overtimelog.core.model.DayKind.REST_DAY to "休息日",
                    com.overtimelog.core.model.DayKind.HOLIDAY to "法定节假日",
                    com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY to "调休补班（要上班）",
                ).forEach { (kind, label) ->
                    TextButton(onClick = { onPick(kind) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text("恢复自动") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    markers: Map<LocalDate, com.overtimelog.domain.repository.DayMarker>,
    dayKinds: Map<LocalDate, com.overtimelog.core.model.DayInfo>,
    onSelect: (LocalDate) -> Unit,
    onLongSelect: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    // 周一为一周第一天：算出 1 号前面要留几个空位。
    val leading = (firstDay.dayOfWeek.value - 1 + 7) % 7
    val totalCells = leading + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7
    val today = LocalDate.now()

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0..6) {
                    val dayIndex = row * 7 + column - leading
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            // 格子比宽度高一点，才放得下"日期 + 节日名 + 休/班角标"
                            .height(56.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayIndex in 0 until month.lengthOfMonth()) {
                            val date = month.atDay(dayIndex + 1)
                            DayCell(
                                date = date,
                                isSelected = date == selected,
                                isToday = date == today,
                                marker = markers[date],
                                dayInfo = dayKinds[date],
                                onClick = { onSelect(date) },
                                onLongClick = { onLongSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一天的格子：日期数字 + 节日名 + 右上角「休 / 班」角标，配色参考小米日历。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    marker: com.overtimelog.domain.repository.DayMarker?,
    dayInfo: com.overtimelog.core.model.DayInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val kind = dayInfo?.kind
    // 休息日和法定节假日用暖红，补班用蓝，普通工作日用常规文字色
    val accent = when (kind) {
        com.overtimelog.core.model.DayKind.HOLIDAY,
        com.overtimelog.core.model.DayKind.REST_DAY,
        -> RestAccent

        com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> MakeupAccent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val dateColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        kind == com.overtimelog.core.model.DayKind.HOLIDAY ||
            kind == com.overtimelog.core.model.DayKind.REST_DAY -> RestAccent

        kind == com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> MakeupAccent
        else -> MaterialTheme.colorScheme.onSurface
    }
    // 节日名只在格子下方显示前 2 个字，像"国庆""春节"，和系统日历一致
    val label = dayInfo?.name?.take(2).orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = dateColor,
            )
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else accent,
                    maxLines = 1,
                )
            } else if (marker != null) {
                // 没有节日名时用加班类型圆点补位，两种信息不互相挤
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    marker.types.take(3).forEach { type ->
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else type.color()),
                        )
                    }
                }
            }
        }
        // 右上角「休 / 班」角标
        val badge = when (kind) {
            com.overtimelog.core.model.DayKind.HOLIDAY -> "休"
            com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> "班"
            else -> null
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

/** 图例：告诉用户红色 = 休息、蓝色「班」= 调休补班。 */
@Composable
private fun DayKindLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = RestAccent, text = "休 休息 / 法定节假日")
        LegendItem(color = MakeupAccent, text = "班 调休补班")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 选中日期旁边的性质标签：法定节假日 / 休息日 / 调休补班 / 工作日。 */
@Composable
private fun DayKindBadge(info: com.overtimelog.core.model.DayInfo) {
    val (text, color) = when (info.kind) {
        com.overtimelog.core.model.DayKind.HOLIDAY -> "法定节假日" to RestAccent
        com.overtimelog.core.model.DayKind.REST_DAY -> "休息日" to RestAccent
        com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> "调休补班" to MakeupAccent
        com.overtimelog.core.model.DayKind.WORKDAY -> "工作日" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 休息 / 法定节假日用暖红，和系统日历一致；深色模式下调亮一点保证对比。 */
private val RestAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFD32F2F)

/** 调休补班用蓝，和红色形成明显区分。 */
private val MakeupAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1976D2)

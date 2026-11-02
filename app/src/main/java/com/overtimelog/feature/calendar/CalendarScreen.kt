package com.overtimelog.feature.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.ShortHours
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
    // 点顶部年月 → 手动输入年月跳转
    var showMonthPicker by remember { mutableStateOf(false) }

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

    // 选中的不是今天就显示"今"按钮，点一下回到今天
    val isToday = state.selectedDate == LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("日历") },
                actions = {
                    if (!isToday) {
                        // 圆形"今"按钮：
                        // - 距右边框 0.5cm（≈21dp），不贴边
                        // - 与「日历」标题垂直居中对齐（不再下移，避免底部被标题栏裁掉）
                        // - 文字与标题同字号，淡蓝底
                        FilledTonalIconButton(
                            onClick = viewModel::backToToday,
                            modifier = Modifier
                                .padding(end = 21.dp)
                                .size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Text(
                                text = "今",
                                // 与 TopAppBar 标题同用 titleLarge，字号一致
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        // 右边的 Spacer 保持 0，靠 padding 精确控制 0.5cm 边距
                        Spacer(Modifier.width(0.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部留 40dp：最后一条记录不被手势条/导航栏挡住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
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
                        // 点年月文字 → 打开手动输入对话框，可一步跳到很远的年月
                        onClickMonth = { showMonthPicker = true },
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
                    Text(
                        text = if (state.hasHolidayData) "" else "本年安排未内置，可在设置里更新",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Column(
                    // 左右滑动切换月份：在月历网格这一块区域上识别水平拖动，
                    // 向左滑 → 下个月，向右滑 → 上个月（跟随常见日历 App 的手感）。
                    // 只挂在日历区域而不是整页，避免抢掉列表上下滚动。
                    modifier = Modifier.pointerInput(state.month) {
                        var dragTotalX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    dragTotalX <= -SWIPE_THRESHOLD_PX -> viewModel.nextMonth()
                                    dragTotalX >= SWIPE_THRESHOLD_PX -> viewModel.previousMonth()
                                }
                                dragTotalX = 0f
                            },
                            onDragCancel = { dragTotalX = 0f },
                        ) { _, dragAmount ->
                            dragTotalX += dragAmount
                        }
                    },
                ) {
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
                // 「+这天记一笔」缩到和日期、性质标签同一行，不再单独占一块大按钮；
                // 高度压到 36dp、字号 labelLarge，点起来依然比原来的纯文字按钮大。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DateTexts.day(state.selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        state.selectedDayInfo?.let { info ->
                            Spacer(Modifier.width(6.dp))
                            DayKindBadge(info)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    ExtendedFloatingActionButton(
                        onClick = { onAddRecordForDate(state.selectedDate) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        text = {
                            Text(
                                text = "这天记一笔",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        modifier = Modifier.height(36.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp,
                        ),
                    )
                }
                // 节日名放在日期行下方，不挤占按钮和标签的位置
                state.selectedDayInfo?.takeIf { it.name.isNotBlank() }?.let { info ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

    if (showMonthPicker) {
        MonthJumpDialog(
            current = state.month,
            onConfirm = { target ->
                viewModel.jumpTo(target)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
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
 * 手动输入年月并跳转。
 *
 * 输入两个数字框（年、月），比让用户在日历上一格一格翻页快得多 ——
 * 想查去年某个月的加班，一下就到了。年范围限制在 1970–2100，避免输入明显错误的值。
 */
@Composable
private fun MonthJumpDialog(
    current: YearMonth,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var yearText by remember { mutableStateOf(current.year.toString()) }
    var monthText by remember { mutableStateOf(current.monthValue.toString()) }

    val year = yearText.trim().toIntOrNull()
    val month = monthText.trim().toIntOrNull()
    val valid = year != null && year in 1970..2100 && month != null && month in 1..12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳到指定年月") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { if (it.length <= 4) yearText = it.filter { c -> c.isDigit() } },
                        label = { Text("年") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { if (it.length <= 2) monthText = it.filter { c -> c.isDigit() } },
                        label = { Text("月") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (valid) {
                        "将跳到 ${year}年${month}月"
                    } else {
                        "年份填 1970–2100，月份填 1–12"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && year != null && month != null) {
                        onConfirm(YearMonth.of(year, month))
                    }
                },
                enabled = valid,
            ) { Text("跳转") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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

    // 整个月的所有格子统一高度：
    // 外层 Column 用 IntrinsicSize.Min，让每一行、每一格都按"全月最高的那个格子"取高度，
    // 整块日历是整齐网格（不会出现有的行高、有的行矮）。
    // 用 IntrinsicSize 而不是写死 dp：系统字号调大时格子跟着长，
    // 不会再裁掉"国庆下方的小时数"（实际反馈过的问题）。
    Column(modifier = Modifier.height(IntrinsicSize.Min)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                for (column in 0..6) {
                    val dayIndex = row * 7 + column - leading
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 64.dp)
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
    // 当天加班小时数（有记录才显示）。格式取最紧凑的："3h" / "2.5h"，
    // 字号比日期数字小两档（bodyMedium 14sp → labelSmall 11sp），不抢日期的视觉重量。
    val hoursText = marker?.takeIf { it.totalMinutes > 0 }?.let { ShortHours.of(it.totalMinutes) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = dateColor,
            )
            // 顺序：日期 → 节日名（有的话）→ 加班小时数。
            // 有节日名时把小时数压到节日名下面，避免出现「日期/3h/国庆」这种把节日名顶走的排法。
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else accent,
                    maxLines = 1,
                )
            }
            if (hoursText != null) {
                // 加班小时数：比日期小两号，用主色/选中色，一眼能看出"这天有加班、加了几小时"
                Text(
                    text = hoursText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
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

/**
 * 选中日期旁边的性质标签：法定节假日 / 休息日 / 调休补班 / 工作日。
 *
 * 宽度固定为「3 个字」的宽度：
 * - 3 字以内（工作日 / 休息日 / 调休补班）单行显示并水平居中；
 * - 4 个字（法定节假日是 5 字，这里指宽度放不下的词）自动按词语换行、每行居中。
 *
 * 换行规则靠 [WordBreak]：在每个中文词之间插入零宽空格（U+200B），
 * 让 Compose 只在词与词之间断开，不会把「法定节假日」拆成「法/定节/假日」这种断法。
 */
@Composable
private fun DayKindBadge(info: com.overtimelog.core.model.DayInfo) {
    val (text, color) = when (info.kind) {
        com.overtimelog.core.model.DayKind.HOLIDAY -> "法定节假日" to RestAccent
        com.overtimelog.core.model.DayKind.REST_DAY -> "休息日" to RestAccent
        com.overtimelog.core.model.DayKind.MAKEUP_WORKDAY -> "调休补班" to MakeupAccent
        com.overtimelog.core.model.DayKind.WORKDAY -> "工作日" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 框体最小宽度 = 「3 个字」（labelSmall 约 11sp/字，3 字 33dp + 内边距）。
    // 用 widthIn(min = ...) 而不是固定 width：系统字体调大时框体可以跟着长，
    // 但绝不会把 3 个字的词挤成两行（固定宽度 + 大字号就是「休息日」被换行的原因）。
    // softWrap = false：禁止按宽度自动换行，只有 [breakByWord] 里的显式 \n 才换行。
    val threeCharMinWidth = 48.dp
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text = breakByWord(text),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            softWrap = false,
            modifier = Modifier
                .widthIn(min = threeCharMinWidth)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        )
    }
}

/**
 * 按词语把性质标签拆成多行，保证断行落在词边界上。
 *
 * - 3 个字以内（工作日 / 休息日）：原样返回，单行居中；
 * - 4 个字（调休补班）：拆成「调休」/「补班」两行；
 * - 5 个字（法定节假日）：拆成「法定」/「节假日」两行。
 *
 * 用显式换行符而不是自动换行：中文字符之间天然可断行，自动换行可能断成
 * 「法定节」/「假日」这种词中间劈开的样式（实测踩过）。
 */
private fun breakByWord(text: String): String = when (text) {
    "法定节假日" -> "法定\n节假日"
    "调休补班" -> "调休\n补班"
    else -> text
}

/** 左右滑动切换月份的最小位移（像素）。约 60dp 对应的粗略值，太小会误触。 */
private const val SWIPE_THRESHOLD_PX = 150f

/** 休息 / 法定节假日用暖红，和系统日历一致；深色模式下调亮一点保证对比。 */
private val RestAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFD32F2F)

/** 调休补班用蓝，和红色形成明显区分。 */
private val MakeupAccent: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8AB4F8) else Color(0xFF1976D2)

package com.overtimelog.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<OvertimeRecord?>(null) }

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
                ) {
                    MonthSwitcher(
                        month = state.month,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
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
                        onSelect = viewModel::selectDate,
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
                        text = DateTexts.day(state.selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { onAddRecordForDate(state.selectedDate) }) {
                        Text("这天记一笔")
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
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    markers: Map<LocalDate, com.overtimelog.domain.repository.DayMarker>,
    onSelect: (LocalDate) -> Unit,
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
                            .aspectRatio(1f)
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
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    marker: com.overtimelog.domain.repository.DayMarker?,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        if (marker != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                marker.types.take(3).forEach { type ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else type.color()),
                    )
                }
            }
        }
    }
}

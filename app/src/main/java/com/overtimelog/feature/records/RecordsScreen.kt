package com.overtimelog.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.label
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.MonthSwitcher
import com.overtimelog.ui.components.RecordCard
import java.time.YearMonth

/**
 * 记录列表页：按月/类型/结算状态筛选，支持备注搜索、删除（带撤销）、标记结算。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordsScreen(
    container: AppContainer,
    onOpenRecord: (Long) -> Unit,
) {
    val viewModel: RecordsViewModel = viewModel(factory = RecordsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<OvertimeRecord?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RecordsEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已删除一条记录",
                        actionLabel = "撤销",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.record)
                    }
                }
                is RecordsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("记录") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthSwitcher(
                    month = state.filter.month ?: YearMonth.now(),
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
                TextButton(
                    onClick = {
                        viewModel.setMonth(if (state.filter.month == null) YearMonth.now() else null)
                    },
                ) {
                    Text(if (state.filter.month == null) "只看本月" else "全部月份")
                }
            }

            OutlinedTextField(
                value = state.filter.keyword,
                onValueChange = viewModel::setKeyword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索备注") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )

            // 类型筛选：左侧「全部类型」，右侧固定两行
            // 第一行「工作日 / 休息日」，第二行「法定节假日 / 自定义」。
            // 「全部类型」垂直居中于右侧两行（Alignment.CenterVertically）。
            // 右侧用两个显式 Row 而不是自动换行：行数不随屏幕宽度和系统字号变，各机型一致。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.filter.type == null,
                    onClick = { viewModel.setType(null) },
                    label = { Text("全部类型") },
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OvertimeType.WORKDAY, OvertimeType.REST_DAY).forEach { type ->
                            FilterChip(
                                selected = state.filter.type == type,
                                // 再点一次已选中的类型 = 取消筛选（回到「全部类型」）
                                onClick = {
                                    viewModel.setType(if (state.filter.type == type) null else type)
                                },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OvertimeType.HOLIDAY, OvertimeType.CUSTOM).forEach { type ->
                            FilterChip(
                                selected = state.filter.type == type,
                                onClick = {
                                    viewModel.setType(if (state.filter.type == type) null else type)
                                },
                                label = { Text(type.label()) },
                            )
                        }
                    }
                }
            }

            // 结算状态整体下移一行：与上方类型筛选拉开距离，视觉上分成两组筛选
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val settlementOptions = listOf<SettlementStatus?>(null) +
                    listOf(SettlementStatus.UNSETTLED, SettlementStatus.SETTLED)
                settlementOptions.forEach { status ->
                    FilterChip(
                        selected = state.filter.settlement == status,
                        onClick = { viewModel.setSettlement(status) },
                        label = { Text(status?.label() ?: "全部状态") },
                    )
                }
            }

            if (state.isEmpty) {
                EmptyState(
                    text = if (state.filter.keyword.isNotBlank()) "没有匹配「${state.filter.keyword}」的记录"
                    else "这个月还没有记录，到「日历」页点「这天记一笔」开始",
                    // 左右各留 19dp（160dpi 基准下 3mm ≈ 0.118 英寸 ≈ 18.9dp，取 19dp），
                    // 文案不顶到屏幕边框，同时不至于缩得太窄。
                    modifier = Modifier
                        .padding(start = 19.dp, end = 19.dp, top = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onClick = { onOpenRecord(record.id) },
                            trailing = {
                                RecordMenu(
                                    record = record,
                                    onEdit = { onOpenRecord(record.id) },
                                    onToggleSettlement = { viewModel.toggleSettlement(record) },
                                    onDelete = { deleteTarget = record },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { record ->
        ConfirmDialog(
            title = "删除这条记录？",
            text = "删除后可以用列表底部的「撤销」找回最近一条。",
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
private fun RecordMenu(
    record: OvertimeRecord,
    onEdit: () -> Unit,
    onToggleSettlement: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("编辑") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (record.settlementStatus == SettlementStatus.SETTLED) "标记未结算" else "标记已结算",
                    )
                },
                onClick = {
                    expanded = false
                    onToggleSettlement()
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

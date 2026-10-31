package com.overtimelog.feature.comptime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.CompTimeUsage
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.components.ConfirmDialog
import com.overtimelog.ui.components.DatePickerModal
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.SectionCard
import java.time.LocalDate

/**
 * 调休页：看余额、新增"用掉调休"、删除用错的记录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompTimeScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: CompTimeViewModel = viewModel(factory = CompTimeViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CompTimeUsage?>(null) }
    var overdraftText by remember { mutableStateOf<String?>(null) }
    var pendingUsage by remember { mutableStateOf<Pair<LocalDate, Pair<Int, String>>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CompTimeEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is CompTimeEvent.AskOverdraft -> overdraftText = event.text
            }
        }
    }

    Scaffold(
        topBar = {
            // statusBarsPadding：全面屏状态栏会压住标题和返回键（同 RecordEditScreen）
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text("调休") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "新增调休使用")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // 底部多留 24dp：最后一条使用记录不被手势条/导航栏压住
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "调休余额") {
                    Column {
                        Text(
                            text = Durations.text(state.balance.balanceMinutes),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (state.balance.balanceMinutes < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        InfoRow("累计获得", Durations.text(state.balance.earnedMinutes))
                        InfoRow("累计使用", Durations.text(state.balance.usedMinutes))
                        if (state.balance.balanceMinutes < 0) {
                            Text(
                                text = "余额是负的：要么多记了使用，要么加班调休还没记进来。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item { Text("使用记录", style = MaterialTheme.typography.titleMedium) }

            if (state.usages.isEmpty()) {
                item { EmptyState("还没有用掉过调休") }
            } else {
                items(state.usages, key = { it.id }) { usage ->
                    UsageRow(
                        usage = usage,
                        onDelete = { deleteTarget = usage },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddUsageDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { date, minutes, note ->
                showAddDialog = false
                pendingUsage = date to (minutes to note)
                viewModel.addUsage(date, minutes, note)
            },
        )
    }

    deleteTarget?.let { usage ->
        ConfirmDialog(
            title = "删除这条调休使用记录？",
            text = "删除后余额会自动加回去。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteUsage(usage.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    overdraftText?.let { text ->
        ConfirmDialog(
            title = "调休余额不足",
            text = text,
            confirmText = "仍然记录",
            onConfirm = {
                overdraftText = null
                val pending = pendingUsage
                if (pending != null) {
                    viewModel.addUsage(
                        date = pending.first,
                        minutes = pending.second.first,
                        note = pending.second.second,
                        allowOverdraft = true,
                    )
                }
                pendingUsage = null
            },
            onDismiss = {
                overdraftText = null
                pendingUsage = null
            },
        )
    }
}

@Composable
private fun UsageRow(usage: CompTimeUsage, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${DateTexts.full(usage.date)} · ${Durations.text(usage.minutes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (usage.note.isNotBlank()) {
                Text(
                    text = usage.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

@Composable
private fun AddUsageDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Int, String) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var minutesText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val minutes = minutesText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("用掉调休") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("日期")
                    Text(DateTexts.full(date), color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { if (it.length <= 5) minutesText = it },
                    label = { Text("使用分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 200) note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(date, minutes, note) },
                enabled = minutes > 0,
            ) { Text("记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showDatePicker) {
        DatePickerModal(
            initialDate = date,
            onConfirm = {
                date = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

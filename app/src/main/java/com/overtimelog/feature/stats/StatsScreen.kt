package com.overtimelog.feature.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.core.model.MonthlySummary
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.core.model.YearSummary
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.common.label
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.InfoRow
import com.overtimelog.ui.components.MonthSwitcher
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.color
import java.time.YearMonth

/**
 * 统计页：月度/年度汇总，按类型拆分。
 *
 * 图表先用 Compose 画简单的条形，不引入第三方图表库（Vico 留到 V1.1 再评估）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == StatsMode.MONTH,
                        onClick = { viewModel.setMode(StatsMode.MONTH) },
                        label = { Text("月度") },
                    )
                    FilterChip(
                        selected = state.mode == StatsMode.YEAR,
                        onClick = { viewModel.setMode(StatsMode.YEAR) },
                        label = { Text("年度") },
                    )
                }
            }

            item {
                // 结算状态筛选：只统计对应状态的记录，SQL 里就过滤好，不是先全查再筛
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<SettlementStatus?>(
                        null,
                        SettlementStatus.UNSETTLED,
                        SettlementStatus.SETTLED,
                    ).forEach { status ->
                        FilterChip(
                            selected = state.settlement == status,
                            onClick = { viewModel.setSettlement(status) },
                            label = { Text(status?.label() ?: "全部状态") },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::previous) { Text("‹") }
                    Text(
                        text = if (state.mode == StatsMode.MONTH) {
                            DateTexts.month(state.month)
                        } else {
                            "${state.year} 年"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    TextButton(onClick = viewModel::next) { Text("›") }
                }
            }

            if (state.mode == StatsMode.MONTH) {
                monthContent(state.monthSummary)
            } else {
                yearContent(state.yearSummary)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.monthContent(summary: MonthlySummary) {
    item {
        SectionCard(title = "本月合计") {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Durations.hoursText(summary.totalMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " · ${summary.overtimeDays} 天 / ${summary.recordCount} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("预计加班费 ${Money.format(summary.totalAmountCents)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                InfoRow("已结算", Money.format(summary.settledAmountCents))
                InfoRow("未结算", Money.format(summary.unsettledAmountCents))
                InfoRow(
                    "调休获得",
                    Durations.text(summary.compTimeEarnedMinutes),
                )
                InfoRow("调休使用", Durations.text(summary.compTimeUsedMinutes))
                InfoRow("调休余额", Durations.text(summary.compTimeBalanceMinutes))
            }
        }
    }

    if (summary.byType.isEmpty()) {
        item { EmptyState("这个月没有加班记录") }
    } else {
        item {
            SectionCard(title = "按类型拆分") {
                val maxMinutes = summary.byType.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    summary.byType.forEach { row ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(row.type.label(), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${Durations.text(row.minutes)} · ${Money.format(row.amountCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(row.minutes.toFloat() / maxMinutes)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(row.type.color()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.yearContent(summary: YearSummary) {
    item {
        SectionCard(title = "${summary.year} 年合计") {
            Column {
                Text(
                    text = Durations.hoursText(summary.totalMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "预计加班费 ${Money.format(summary.totalAmountCents)}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    item {
        SectionCard(title = "每月加班时长") {
            val maxMinutes = summary.months.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                summary.months.forEach { point ->
                    val ratio = point.minutes.toFloat() / maxMinutes
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((ratio * 110).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (point.minutes > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${point.month.monthValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    item {
        SectionCard(title = "逐月明细") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.months.filter { it.minutes > 0 }.forEach { point ->
                    InfoRow(
                        label = DateTexts.month(YearMonth.from(point.month)),
                        value = "${Durations.text(point.minutes)} · ${Money.format(point.amountCents)}",
                    )
                }
                if (summary.months.all { it.minutes == 0 }) {
                    Text(
                        text = "这一年还没有记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

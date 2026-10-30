package com.overtimelog.feature.overview

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.overtimelog.di.AppContainer
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money
import com.overtimelog.ui.components.EmptyState
import com.overtimelog.ui.components.MonthSwitcher
import com.overtimelog.ui.components.RecordCard
import com.overtimelog.ui.components.SectionCard
import com.overtimelog.ui.components.StatTile
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
    onAddRecord: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onOpenAllRecords: () -> Unit,
    onOpenCompTime: () -> Unit,
) {
    val viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary
    val isCurrentMonth = state.month == YearMonth.now()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("加班记") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRecord,
                // 显式给无障碍描述：读屏能念出"记一笔"，自动化冒烟脚本也能稳定找到这个按钮
                // （Compose 的 FAB 文字默认不一定出现在语义树里）。
                modifier = Modifier.semantics { contentDescription = "记一笔" },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一笔") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                        EmptyState("这个月还没有加班记录，点右下角「记一笔」开始")
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

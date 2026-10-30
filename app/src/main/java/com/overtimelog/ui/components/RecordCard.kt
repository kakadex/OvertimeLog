package com.overtimelog.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.Durations
import com.overtimelog.ui.common.Money

/**
 * 记录卡片。列表页、概览页、日历页共用同一个卡片，保证同一条记录在哪看都长一样。
 *
 * 一次显示：日期、类型、结算状态、起止时间、时长、金额，备注只显示一行。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordCard(
    record: OvertimeRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 长按回调：日历页用来快速删除，普通列表页不传就不响应长按。 */
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = clickModifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateTexts.day(record.date),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    TypeBadge(record.type)
                    Spacer(Modifier.width(6.dp))
                    SettlementChip(record.settlementStatus)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${DateTexts.range(record.start.toLocalTime(), record.end.toLocalTime())} · ${Durations.text(record.durationMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (record.compTimeMinutes > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "调休 +${Durations.text(record.compTimeMinutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    text = Money.format(record.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${record.multiplierBp / 1000.0} 倍",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

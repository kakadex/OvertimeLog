package com.overtimelog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import com.overtimelog.ui.common.DateTexts
import com.overtimelog.ui.common.label
import java.time.YearMonth

/** 加班类型对应的颜色，日历圆点和卡片标签共用一份，避免两处颜色不一致。 */
@Composable
fun OvertimeType.color(): Color = when (this) {
    OvertimeType.WORKDAY -> MaterialTheme.colorScheme.primary
    OvertimeType.REST_DAY -> MaterialTheme.colorScheme.tertiary
    OvertimeType.HOLIDAY -> MaterialTheme.colorScheme.error
    OvertimeType.CUSTOM -> MaterialTheme.colorScheme.secondary
}

@Composable
fun TypeBadge(type: OvertimeType, modifier: Modifier = Modifier) {
    val color = type.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = type.label(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SettlementChip(status: SettlementStatus, modifier: Modifier = Modifier) {
    val settled = status == SettlementStatus.SETTLED
    val color = if (settled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.10f),
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 概览页的统计小卡片：一个大数字 + 一行说明。 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = accent)
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * 月份切换器：上个月 / 2026年10月 / 下个月。
 *
 * [onClickMonth] 非空时，中间的年月文字可点击（打开「手动输入年月」对话框）；
 * 为空则只是普通文字，保持旧行为。
 *
 * 「今」按钮不在这里：各页统一放到顶栏右上角（见 [TodayButton]）。
 */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    onClickMonth: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = DateTexts.month(month),
            style = MaterialTheme.typography.titleMedium,
            // 可点击时给出明确的热区和语义，避免用户不知道这里能点
            modifier = Modifier
                .then(
                    if (onClickMonth != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClickMonth)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    },
                ),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

/**
 * 「今」按钮（日历 / 概览 / 记录 / 统计四处共用）。
 *
 * 样式与日历页顶栏那个完全一致：右上角、40dp 圆形、淡蓝底、文字与页面标题同字号。
 * 各页只决定"什么时候显示它"（比如只在本月以外显示），长什么样统一在这里。
 */
@Composable
fun TodayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            // 距右边框 0.5cm（≈21dp），和日历页保持一致，不贴边
            .padding(end = 21.dp)
            .size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            text = "今",
            // 与页面标题同用 titleLarge，字号一致
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // 中文排版习惯：首行缩进两个字符。用「全角空格 x2」实现，
            // 只影响第一行，后面的行仍然顶格自然换行（不会整段右侧被推）。
            text = "\u3000\u3000" + text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}

/** 一条"标签 : 值"的信息行，设置页和详情里用。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

/** 一个小的色块圆点，日历里标记当天有哪些加班类型。 */
@Composable
fun TypeDot(color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .height(6.dp)
            .background(color = color, shape = RoundedCornerShape(3.dp)),
    )
}

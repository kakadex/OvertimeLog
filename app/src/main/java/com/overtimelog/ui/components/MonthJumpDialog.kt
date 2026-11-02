package com.overtimelog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

/** 可跳转的年份范围。限制一下，避免手滑输入 9999 或 0。 */
private val YEAR_RANGE = 1970..2100

/**
 * 一个"标签压在顶边横线正中、数字居中"的输入框。
 *
 * 为什么不用 OutlinedTextField 自带的 label：它固定在左上角，改不成居中。
 * 这里改为不传 label，自己在顶层 Box 里放一个文字、给它涂上对话框底色并上移半个字高，
 * 视觉上就是骑在边框上；底色必须和对话框容器同色，否则会露出一块色差。
 */
@Composable
private fun CenteredField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            // 数字居中显示，和上面居中的标签对齐
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 上移约半个字高，正好压住顶边横线
                .offset(y = (-8).dp)
                .background(containerColor)
                .padding(horizontal = 6.dp),
        )
    }
}

/**
 * 手动输入年月并跳转的对话框（概览 / 记录 / 统计 / 日历四处共用）。
 *
 * 比一格一格翻页快得多：想查去年某个月的加班，填两个数字一下就到位。
 * 排版按用户要求全部居中：标题居中、年/月在顶边横线中间、数字在框内居中。
 */
@Composable
fun MonthJumpDialog(
    current: YearMonth,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var yearText by remember { mutableStateOf(current.year.toString()) }
    var monthText by remember { mutableStateOf(current.monthValue.toString()) }

    val year = yearText.trim().toIntOrNull()
    val month = monthText.trim().toIntOrNull()
    val valid = year != null && year in YEAR_RANGE && month != null && month in 1..12

    // 固定容器色：CenteredField 里的标签要拿它当底色盖住边框
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Text(
                text = "跳到指定年月",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CenteredField(
                        value = yearText,
                        onValueChange = { if (it.length <= 4) yearText = it.filter { c -> c.isDigit() } },
                        label = "年",
                        containerColor = containerColor,
                        modifier = Modifier.weight(1f),
                    )
                    CenteredField(
                        value = monthText,
                        onValueChange = { if (it.length <= 2) monthText = it.filter { c -> c.isDigit() } },
                        label = "月",
                        containerColor = containerColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = if (valid) "将跳到 ${year}年${month}月" else "年份填 1970–2100，月份填 1–12",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid && year != null && month != null) onConfirm(YearMonth.of(year, month))
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
 * 只输入年份的跳转对话框（统计页的"年度"模式用）。
 *
 * 年度模式界面只显示「2026 年」，不能填月份，否则用户会以为能跳到某个月。
 * 排版与 [MonthJumpDialog] 一致：标题居中、标签骑在横线上、数字居中。
 */
@Composable
fun YearJumpDialog(
    currentYear: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var yearText by remember { mutableStateOf(currentYear.toString()) }
    val year = yearText.trim().toIntOrNull()
    val valid = year != null && year in YEAR_RANGE
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Text(
                text = "跳到指定年份",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CenteredField(
                    value = yearText,
                    onValueChange = { if (it.length <= 4) yearText = it.filter { c -> c.isDigit() } },
                    label = "年",
                    containerColor = containerColor,
                )
                Text(
                    text = if (valid) "将跳到 $year 年" else "年份填 1970–2100",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid && year != null) onConfirm(year) },
                enabled = valid,
            ) { Text("跳转") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

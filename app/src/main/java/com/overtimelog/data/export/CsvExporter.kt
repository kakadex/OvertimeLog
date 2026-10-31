package com.overtimelog.data.export

import android.content.ContentResolver
import android.net.Uri
import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/** 打开一个可写输出流。抽成接口是为了让导出逻辑能脱离 Android 单测。 */
fun interface UriSink {
    fun openOutputStream(uri: String): OutputStream?
}

/** 用 ContentResolver 写 SAF 返回的 Uri —— 不需要任何存储权限（ADR-005）。 */
class ContentResolverUriSink(private val resolver: ContentResolver) : UriSink {
    override fun openOutputStream(uri: String): OutputStream? =
        resolver.openOutputStream(Uri.parse(uri))
}

/**
 * CSV 导出。
 *
 * 规则：
 * - 文件头写 UTF-8 BOM，否则中文在 Excel 里会乱码；
 * - 字段按 RFC 4180 转义（逗号、双引号、换行都要包引号）；
 * - 直接流式写，不把整张表拼成一个巨大字符串，万条记录也不会 OOM。
 */
class CsvExporter(private val sink: UriSink) {

    fun export(uri: String, records: List<OvertimeRecord>): Int {
        val stream = sink.openOutputStream(uri) ?: throw IOException("无法打开导出文件")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(BOM)
            writer.write(HEADER)
            writer.write("\r\n")
            records.forEach { record ->
                writer.write(record.toCsvRow())
                writer.write("\r\n")
            }
        }
        return records.size
    }

    private fun OvertimeRecord.toCsvRow(): String {
        // 简单记的记录没有真实上下班时间：开始/结束时间列留空，时长列照常导出，
        // 这样在 Excel 里一眼就能区分"填过时间"和"只填了小时数"的记录。
        val startTimeText = if (isSimpleEntry) "" else start.format(TIME_FORMAT)
        val endTimeText = if (isSimpleEntry) "" else end.format(TIME_FORMAT)
        val cells = listOf(
            date.format(DATE_FORMAT),
            startTimeText,
            endTimeText,
            breakMinutes.toString(),
            type.displayName(),
            durationMinutes.toString(),
            formatMinutes(durationMinutes),
            BigDecimal(multiplierBp).divide(BigDecimal(1000)).toPlainString(),
            BigDecimal(amountCents).divide(BigDecimal(100)).setScale(2).toPlainString(),
            compTimeMinutes.toString(),
            if (settlementStatus == SettlementStatus.SETTLED) "已结算" else "未结算",
            note,
        )
        return cells.joinToString(",") { escape(it) }
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "${h}小时" else "${h}小时${m}分"
    }

    private fun OvertimeType.displayName(): String = when (this) {
        OvertimeType.WORKDAY -> "工作日"
        OvertimeType.REST_DAY -> "休息日"
        OvertimeType.HOLIDAY -> "法定节假日"
        OvertimeType.CUSTOM -> "自定义"
    }

    private companion object {
        const val BOM = "\uFEFF"
        const val HEADER = "日期,开始时间,结束时间,休息分钟,加班类型,时长(分钟),时长,倍率,金额(元),调休(分钟),结算状态,备注"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

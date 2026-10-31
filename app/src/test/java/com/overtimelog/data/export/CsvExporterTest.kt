package com.overtimelog.data.export

import com.overtimelog.core.model.OvertimeRecord
import com.overtimelog.core.model.OvertimeType
import com.overtimelog.core.model.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * CSV 导出单测：BOM、表头、RFC 4180 转义、行数。
 *
 * 用临时文件当"输出流"，所以不需要 Android 的文件选择器也能验证内容正确。
 */
class CsvExporterTest {

    private fun record(note: String) = OvertimeRecord(
        id = 1,
        date = LocalDate.of(2026, 10, 30),
        start = LocalDateTime.of(2026, 10, 30, 18, 0),
        end = LocalDateTime.of(2026, 10, 30, 21, 0),
        breakMinutes = 0,
        type = OvertimeType.WORKDAY,
        durationMinutes = 180,
        hourlyRateCents = 5747,
        multiplierBp = 1500,
        amountCents = 25862,
        compTimeMinutes = 0,
        settlementStatus = SettlementStatus.UNSETTLED,
        note = note,
        createdAt = Instant.parse("2026-10-30T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-30T10:00:00Z"),
    )

    @Test
    fun `csv starts with BOM and header and escapes commas and quotes`() {
        val file = File.createTempFile("overtime-", ".csv")
        try {
            val exporter = CsvExporter { uri ->
                assertEquals(file.toURI().toString(), uri)
                FileOutputStream(file)
            }
            val count = exporter.export(
                file.toURI().toString(),
                listOf(record("和产品对需求, 顺便说了\"晚点走\"")),
            )
            assertEquals(1, count)

            val text = file.readText(Charsets.UTF_8)
            assertTrue("必须以 UTF-8 BOM 开头，Excel 才不会乱码", text.startsWith("\uFEFF"))
            assertTrue(text.contains("日期,开始时间,结束时间,休息分钟"))
            assertTrue(text.contains("\"和产品对需求, 顺便说了\"\"晚点走\"\"\""))

            // 表头 + 1 行数据
            assertEquals(2, text.trim().lines().size)
            assertTrue(text.contains("258.62"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `empty range exports header only`() {
        val file = File.createTempFile("overtime-empty-", ".csv")
        try {
            val exporter = CsvExporter { FileOutputStream(file) }
            assertEquals(0, exporter.export(file.toURI().toString(), emptyList()))
            assertEquals(1, file.readText(Charsets.UTF_8).trim().lines().size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `simple entry exports blank time columns`() {
        val file = File.createTempFile("overtime-simple-", ".csv")
        try {
            val simpleRecord = record("只记小时数").copy(isSimpleEntry = true)
            val exporter = CsvExporter { FileOutputStream(file) }
            exporter.export(file.toURI().toString(), listOf(simpleRecord))

            val text = file.readText(Charsets.UTF_8)
            // 简单记：开始/结束时间两列留空，日期和时长仍然在
            val dataLine = text.trim().lines().last()
            assertTrue("开始时间列应为空", dataLine.contains("2026-10-30,,"))
            assertTrue("时长列要保留", dataLine.contains("180,3小时"))
        } finally {
            file.delete()
        }
    }
}

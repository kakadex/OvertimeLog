package com.overtimelog.domain.usecase

import com.overtimelog.core.model.DomainError
import com.overtimelog.data.export.CsvExporter
import com.overtimelog.domain.repository.OvertimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** CSV 导出结果：导出了多少条、覆盖的日期范围。 */
data class ExportResult(
    val count: Int,
    val from: LocalDate?,
    val to: LocalDate?,
)

/**
 * 导出 CSV 到用户通过系统文件选择器挑的位置。
 *
 * 整个写文件动作放在 Dispatchers.IO；返回 Result 而不是抛异常，方便 ViewModel 统一映射文案。
 */
class ExportCsvUseCase(
    private val repository: OvertimeRepository,
    private val exporter: CsvExporter,
) {
    suspend operator fun invoke(
        uri: String,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            val records = repository.getAllInRange(
                from = from ?: LocalDate.of(1970, 1, 1),
                to = to ?: LocalDate.of(2999, 12, 31),
            )
            val count = exporter.export(uri, records)
            Result.success(ExportResult(count = count, from = from, to = to))
        } catch (t: Throwable) {
            Result.failure(DomainError.StorageError(t))
        }
    }
}

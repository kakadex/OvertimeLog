package com.overtimelog.di

import android.content.Context
import com.overtimelog.core.calculation.CalculationEngine
import com.overtimelog.data.export.ContentResolverUriSink
import com.overtimelog.data.export.CsvExporter
import com.overtimelog.data.local.db.AppDatabase
import com.overtimelog.data.repository.RoomCompTimeRepository
import com.overtimelog.data.repository.RoomOvertimeRepository
import com.overtimelog.data.repository.RoomSalaryRuleRepository
import com.overtimelog.data.settings.DataStoreSettingsRepository
import com.overtimelog.domain.repository.CompTimeRepository
import com.overtimelog.domain.repository.OvertimeRepository
import com.overtimelog.domain.repository.SalaryRuleRepository
import com.overtimelog.domain.repository.SettingsRepository
import com.overtimelog.domain.usecase.AddCompTimeUsageUseCase
import com.overtimelog.domain.usecase.AddOvertimeRecordUseCase
import com.overtimelog.domain.usecase.CalculatePreviewUseCase
import com.overtimelog.domain.usecase.ClearAllDataUseCase
import com.overtimelog.domain.usecase.DeleteCompTimeUsageUseCase
import com.overtimelog.domain.usecase.DeleteOvertimeRecordUseCase
import com.overtimelog.domain.usecase.ExportCsvUseCase
import com.overtimelog.domain.usecase.MarkMonthSettledUseCase
import com.overtimelog.domain.usecase.MarkRecordsSettledUseCase
import com.overtimelog.domain.usecase.RestoreOvertimeRecordUseCase
import com.overtimelog.domain.usecase.UpdateOvertimeRecordUseCase
import java.time.Clock

/**
 * 手动依赖容器。
 *
 * 为什么不用 Hilt：这一版先保证"能编译、能跑、架构接口清晰"。依赖全部通过接口注入，
 * 之后引入 Hilt 时只需要把这里的 lazy 换成 @Provides 模块，业务代码不用动。
 * （架构文档把 Hilt 列为选型，但 AGP 9 + Kotlin 2.4 的注解处理器组合还没验证过，
 * 先不把编译风险引进来，详见 docs/实现说明.md。）
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val clock: Clock = Clock.systemDefaultZone()
    val engine: CalculationEngine = CalculationEngine()

    val overtimeRepository: OvertimeRepository by lazy {
        RoomOvertimeRepository(database.overtimeRecordDao(), database.compTimeUsageDao())
    }
    val salaryRuleRepository: SalaryRuleRepository by lazy {
        RoomSalaryRuleRepository(database.salaryRuleDao())
    }
    val compTimeRepository: CompTimeRepository by lazy {
        RoomCompTimeRepository(database.overtimeRecordDao(), database.compTimeUsageDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    private val csvExporter: CsvExporter by lazy {
        CsvExporter(ContentResolverUriSink(appContext.contentResolver))
    }

    val calculatePreview: CalculatePreviewUseCase by lazy {
        CalculatePreviewUseCase(salaryRuleRepository, engine)
    }
    val addRecord: AddOvertimeRecordUseCase by lazy {
        AddOvertimeRecordUseCase(overtimeRepository, salaryRuleRepository, compTimeRepository, engine, clock)
    }
    val updateRecord: UpdateOvertimeRecordUseCase by lazy {
        UpdateOvertimeRecordUseCase(overtimeRepository, salaryRuleRepository, compTimeRepository, engine, clock)
    }
    val deleteRecord: DeleteOvertimeRecordUseCase by lazy {
        DeleteOvertimeRecordUseCase(overtimeRepository)
    }
    val restoreRecord: RestoreOvertimeRecordUseCase by lazy {
        RestoreOvertimeRecordUseCase(overtimeRepository)
    }
    val markSettled: MarkRecordsSettledUseCase by lazy {
        MarkRecordsSettledUseCase(overtimeRepository)
    }
    val markMonthSettled: MarkMonthSettledUseCase by lazy {
        MarkMonthSettledUseCase(overtimeRepository)
    }
    val clearAllData: ClearAllDataUseCase by lazy {
        ClearAllDataUseCase(overtimeRepository, compTimeRepository)
    }
    val exportCsv: ExportCsvUseCase by lazy {
        ExportCsvUseCase(overtimeRepository, csvExporter)
    }
    val addCompTimeUsage: AddCompTimeUsageUseCase by lazy {
        AddCompTimeUsageUseCase(compTimeRepository, clock)
    }
    val deleteCompTimeUsage: DeleteCompTimeUsageUseCase by lazy {
        DeleteCompTimeUsageUseCase(compTimeRepository)
    }
}

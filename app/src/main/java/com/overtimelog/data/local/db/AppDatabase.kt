package com.overtimelog.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.overtimelog.data.local.dao.CompTimeUsageDao
import com.overtimelog.data.local.dao.OvertimeRecordDao
import com.overtimelog.data.local.dao.SalaryRuleDao
import com.overtimelog.data.local.entity.CompTimeUsageEntity
import com.overtimelog.data.local.entity.HolidayOverrideEntity
import com.overtimelog.data.local.entity.OvertimeRecordEntity
import com.overtimelog.data.local.entity.SalaryRuleEntity

/**
 * 本地数据库。
 *
 * V1 只有 version = 1；后续每次改表都要升版本并写 Migration，并在 androidTest 里跑迁移测试
 * （架构文档 14 节的"质量门槛"）。这里 exportSchema = false 是为了避免早期的 schema 目录噪音，
 * 引入第一次迁移之前会改成 true 并把 schema 提交进仓库。
 */
@Database(
    entities = [
        OvertimeRecordEntity::class,
        SalaryRuleEntity::class,
        CompTimeUsageEntity::class,
        HolidayOverrideEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun overtimeRecordDao(): OvertimeRecordDao
    abstract fun salaryRuleDao(): SalaryRuleDao
    abstract fun compTimeUsageDao(): CompTimeUsageDao

    companion object {
        private const val DB_NAME = "overtimelog.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}

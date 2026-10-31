package com.overtimelog.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * 版本历史：
 * - 1 → 初始版本；
 * - 2 → overtime_records 增加 is_simple_entry（「简单记模式」标记，老记录一律 0）。
 *
 * 升级必须走 [MIGRATION_1_2]，不能掉进 destructive migration，否则用户历史记录会被清空。
 */
@Database(
    entities = [
        OvertimeRecordEntity::class,
        SalaryRuleEntity::class,
        CompTimeUsageEntity::class,
        HolidayOverrideEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun overtimeRecordDao(): OvertimeRecordDao
    abstract fun salaryRuleDao(): SalaryRuleDao
    abstract fun compTimeUsageDao(): CompTimeUsageDao

    companion object {
        private const val DB_NAME = "overtimelog.db"

        /**
         * 1 → 2：只加一列，带默认值 0。
         *
         * 之所以给默认值，是因为 SQLite 的 ADD COLUMN 要求 NOT NULL 列必须有默认值；
         * 老记录默认 0 表示"不是简单记"，列表里继续按起止时间显示，行为不变。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE overtime_records ADD COLUMN is_simple_entry INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}

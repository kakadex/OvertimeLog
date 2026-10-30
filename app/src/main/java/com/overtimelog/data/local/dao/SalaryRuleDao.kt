package com.overtimelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.overtimelog.data.local.entity.SalaryRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryRuleDao {

    @Query("SELECT * FROM salary_rules WHERE active = 1 ORDER BY effective_from DESC LIMIT 1")
    fun observeActive(): Flow<SalaryRuleEntity?>

    @Query("SELECT * FROM salary_rules WHERE active = 1 ORDER BY effective_from DESC LIMIT 1")
    suspend fun getActive(): SalaryRuleEntity?

    @Insert
    suspend fun insert(entity: SalaryRuleEntity): Long

    @Query("UPDATE salary_rules SET active = 0 WHERE id != :keepId")
    suspend fun deactivateAllExcept(keepId: Long)

    @Query("SELECT * FROM salary_rules ORDER BY effective_from DESC")
    suspend fun getAll(): List<SalaryRuleEntity>
}

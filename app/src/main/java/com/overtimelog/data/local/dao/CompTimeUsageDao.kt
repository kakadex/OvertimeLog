package com.overtimelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.overtimelog.data.local.entity.CompTimeUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompTimeUsageDao {

    @Query("SELECT * FROM comp_time_usages ORDER BY date_epoch_day DESC, id DESC")
    fun observeAll(): Flow<List<CompTimeUsageEntity>>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM comp_time_usages")
    fun observeUsedAll(): Flow<Int>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM comp_time_usages WHERE date_epoch_day BETWEEN :from AND :to")
    fun observeUsedInRange(from: Long, to: Long): Flow<Int>

    @Insert
    suspend fun insert(entity: CompTimeUsageEntity): Long

    @Query("DELETE FROM comp_time_usages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM comp_time_usages")
    suspend fun deleteAll()
}

package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing memory usage history records in SQLite.
 */
@Dao
interface MemoryDao {

    @Query("SELECT * FROM memory_history ORDER BY timestamp DESC LIMIT 500")
    fun getAllRecords(): Flow<List<MemoryRecord>>

    @Query("SELECT * FROM memory_history WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getRecordsSince(sinceTimestamp: Long): Flow<List<MemoryRecord>>

    @Query("SELECT COUNT(*) FROM memory_history")
    suspend fun getRecordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MemoryRecord)

    @Query("DELETE FROM memory_history")
    suspend fun clearHistory(): Int

    @Query("DELETE FROM memory_history WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
}

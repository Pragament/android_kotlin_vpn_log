package com.prashik.firewallapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockLog: BlockLogEntity)

    @Query("SELECT * FROM block_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BlockLogEntity>>

    @Query("DELETE FROM block_log")
    suspend fun clearLogs()

    @Query("""
        DELETE FROM block_log 
        WHERE rowid NOT IN (
            SELECT rowid FROM block_log 
            ORDER BY timestamp DESC 
            LIMIT 100
        )
    """)
    suspend fun deleteExtraLogs()
}
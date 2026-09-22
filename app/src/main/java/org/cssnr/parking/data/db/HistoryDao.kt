package org.cssnr.parking.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<History>>

    @Insert
    suspend fun insert(history: History): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
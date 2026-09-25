package org.cssnr.parking.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<History>>

    @Insert
    suspend fun insert(history: History): Long

    @Update
    suspend fun update(history: History)

    @Query("SELECT * FROM history WHERE addr_line IS NULL ORDER BY timestamp ASC")
    suspend fun getWithoutAddress(): List<History>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getById(id: Long): History?

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
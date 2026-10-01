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

    @Query("SELECT * FROM history WHERE geocoded IS NULL ORDER BY timestamp ASC")
    suspend fun getUngeocoded(): List<History>

    /**
     * Records whose location request came back empty, oldest first, and recent
     * enough that the user could still be standing where the car is.
     *
     * [notBefore] is the oldest timestamp worth resolving, and it is what stops this
     * from stamping the user's current position onto an event from days ago. See
     * HistoryViewModel for why that matters.
     */
    @Query("SELECT * FROM history WHERE latitude IS NULL AND timestamp >= :notBefore ORDER BY timestamp ASC")
    suspend fun getUnlocatedSince(notBefore: Long): List<History>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
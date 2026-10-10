package org.cssnr.parking.data

import kotlinx.coroutines.flow.Flow
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History
import org.cssnr.parking.data.db.HistoryDao

class HistoryRepository(database: AppDatabase) {

    private val historyDao: HistoryDao = database.historyDao()

    val history: Flow<List<History>> = historyDao.getAll()

    suspend fun add(history: History): Long = historyDao.insert(history)

    suspend fun update(history: History) {
        historyDao.update(history)
    }

    suspend fun getById(id: Long): History? = historyDao.getById(id)

    suspend fun delete(id: Long) {
        historyDao.deleteById(id)
    }

    /**
     * Deletes every record older than [cutoff] (epoch millis) and returns how
     * many rows went away. The caller derives [cutoff] from the retention
     * setting; null retention (Disabled) means don't call this at all.
     */
    suspend fun prune(cutoff: Long): Int = historyDao.deleteOlderThan(cutoff)
}
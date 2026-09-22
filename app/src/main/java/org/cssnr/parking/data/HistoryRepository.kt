package org.cssnr.parking.data

import kotlinx.coroutines.flow.Flow
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.data.db.History
import org.cssnr.parking.data.db.HistoryDao

class HistoryRepository(private val database: AppDatabase) {

    private val historyDao: HistoryDao = database.historyDao()

    val history: Flow<List<History>> = historyDao.getAll()

    suspend fun add(history: History): Long = historyDao.insert(history)

    suspend fun delete(id: Long) {
        historyDao.deleteById(id)
    }
}
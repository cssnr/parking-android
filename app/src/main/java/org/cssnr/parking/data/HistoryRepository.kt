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

    suspend fun getUngeocoded(): List<History> = historyDao.getUngeocoded()

    suspend fun delete(id: Long) {
        historyDao.deleteById(id)
    }
}
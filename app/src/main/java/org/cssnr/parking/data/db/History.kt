package org.cssnr.parking.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val bluetoothAddress: String,
    val bluetoothName: String? = null,
)
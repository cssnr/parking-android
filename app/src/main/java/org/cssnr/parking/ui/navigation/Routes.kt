package org.cssnr.parking.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Location

@Serializable
data object History

@Serializable
data class MapDetail(
    val id: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
)

@Serializable
data object Automatic

@Serializable
data object Settings
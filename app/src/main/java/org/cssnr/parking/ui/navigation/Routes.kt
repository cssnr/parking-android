package org.cssnr.parking.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object History

@Serializable
data class MapDetail(
    val id: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data object Automation

@Serializable
data object Settings
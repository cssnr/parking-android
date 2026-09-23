package org.cssnr.parking.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.cssnr.parking.R
import org.cssnr.parking.ui.navigation.Automatic
import org.cssnr.parking.ui.navigation.History
import org.cssnr.parking.ui.navigation.Location
import org.cssnr.parking.ui.navigation.MapDetail
import org.cssnr.parking.ui.navigation.Settings
import org.cssnr.parking.ui.screens.AutomaticRoute
import org.cssnr.parking.ui.screens.HistoryRoute
import org.cssnr.parking.ui.screens.LocationRoute
import org.cssnr.parking.ui.screens.MapRoute
import org.cssnr.parking.ui.screens.SettingsRoute

enum class Destination(
    val route: Any,
    val label: String,
    val icon: ImageVector?,
    @DrawableRes val iconRes: Int? = null,
) {
    LOCATION(Location, "Location", null, R.drawable.md_pin_road_24px),
    HISTORY(History, "History", Icons.Filled.History),
    AUTOMATIC(Automatic, "Automatic", null, R.drawable.md_parking_sign_24px),
    SETTINGS(Settings, "Settings", Icons.Filled.Settings),
}

@Composable
fun ParKingApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val trackingStatus = rememberAutomaticTrackingStatus()
    val onTrackingStatusClick = {
        navController.navigate(Automatic) {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentDestination?.hasRoute(destination.route::class) != true) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = {
                            val imageVector = destination.icon
                            if (imageVector != null) {
                                Icon(
                                    imageVector = imageVector,
                                    contentDescription = destination.label,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(destination.iconRes!!),
                                    contentDescription = destination.label,
                                )
                            }
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Location,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<Location> {
                LocationRoute(
                    trackingStatus = trackingStatus,
                    onTrackingStatusClick = onTrackingStatusClick,
                )
            }
            composable<History> {
                HistoryRoute(
                    trackingStatus = trackingStatus,
                    onTrackingStatusClick = onTrackingStatusClick,
                    onRecordClick = { record ->
                        navController.navigate(
                            MapDetail(
                                id = record.id,
                                title = record.bluetoothName ?: record.bluetoothAddress,
                                latitude = record.latitude,
                                longitude = record.longitude,
                                timestamp = record.timestamp,
                            ),
                        )
                    },
                )
            }
            composable<MapDetail> { entry ->
                val detail = entry.toRoute<MapDetail>()
                MapRoute(
                    detail = detail,
                    title = detail.title,
                    onBack = { navController.popBackStack() },
                    trackingStatus = trackingStatus,
                    onTrackingStatusClick = onTrackingStatusClick,
                )
            }
            composable<Automatic> {
                AutomaticRoute(
                    trackingStatus = trackingStatus,
                    onTrackingStatusClick = onTrackingStatusClick,
                )
            }
            composable<Settings> {
                SettingsRoute(
                    trackingStatus = trackingStatus,
                    onTrackingStatusClick = onTrackingStatusClick,
                )
            }
        }
    }
}
package org.cssnr.parking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddLocation
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.BrowseGallery
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.cssnr.parking.ui.navigation.Automatic
import org.cssnr.parking.ui.navigation.About
import org.cssnr.parking.ui.navigation.History
import org.cssnr.parking.ui.navigation.Location
import org.cssnr.parking.ui.navigation.MapDetail
import org.cssnr.parking.ui.navigation.Settings
import org.cssnr.parking.ui.screens.AboutRoute
import org.cssnr.parking.ui.screens.AutomaticRoute
import org.cssnr.parking.ui.screens.HistoryRoute
import org.cssnr.parking.ui.screens.LocationRoute
import org.cssnr.parking.ui.screens.MapRoute
import org.cssnr.parking.ui.screens.SettingsRoute

enum class Destination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    LOCATION(Location, "Location", Icons.Filled.Map, Icons.Outlined.Map),
    HISTORY(History, "History", Icons.Filled.BrowseGallery, Icons.Outlined.BrowseGallery),
    AUTOMATIC(Automatic, "Automatic", Icons.Filled.AddLocation, Icons.Outlined.AddLocation),
    SETTINGS(Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
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

    // About is a drill-in from Settings. Animate the bar instead of
    // adding/removing it instantly, otherwise Scaffold's innerPadding
    // snaps and the outgoing/incoming content jumps.
    val isAbout = currentDestination?.hasRoute(About::class) == true
    val showBottomBar = !isAbout

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) +
                    expandVertically(expandFrom = Alignment.Bottom) +
                    fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) +
                    shrinkVertically(shrinkTowards = Alignment.Bottom) +
                    fadeOut(),
            ) {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    // Keep Settings highlighted while About is on top so the
                    // selection does not flicker out during the slide-away.
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true ||
                        (isAbout && destination == Destination.SETTINGS)
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
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
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
                        val latitude = record.latitude
                        val longitude = record.longitude
                        // A record with no position is not a navigation target. The
                        // disconnect was still recorded and the row is still listed,
                        // but there is nothing to centre a map on and the position is
                        // never filled in after the fact.
                        val title = record.displayName
                        if (latitude != null && longitude != null && title != null) {
                            navController.navigate(
                                MapDetail(
                                    id = record.id,
                                    title = title,
                                    device = record.bluetoothName ?: record.bluetoothAddress,
                                    latitude = latitude,
                                    longitude = longitude,
                                    timestamp = record.timestamp,
                                ),
                            )
                        }
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
                    onNavigateToAbout = { navController.navigate(About) },
                )
            }
            composable<About> {
                AboutRoute(
                    onBack = { navController.navigateUp() },
                )
            }
        }
    }
}
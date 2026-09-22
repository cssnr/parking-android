package org.cssnr.parking.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.cssnr.parking.ui.navigation.Automation
import org.cssnr.parking.ui.navigation.History
import org.cssnr.parking.ui.navigation.MapDetail
import org.cssnr.parking.ui.navigation.Settings
import org.cssnr.parking.ui.screens.AutomationRoute
import org.cssnr.parking.ui.screens.HistoryRoute
import org.cssnr.parking.ui.screens.MapRoute
import org.cssnr.parking.ui.screens.SettingsRoute
import org.cssnr.parking.ui.viewmodel.AutomationViewModel
import kotlinx.coroutines.flow.first

enum class Destination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    HISTORY(History, "History", Icons.Filled.History),
    AUTOMATION(Automation, "Automation", Icons.Filled.Bluetooth),
    SETTINGS(Settings, "Settings", Icons.Filled.Settings),
}

@Composable
fun ParKingApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val automationViewModel: AutomationViewModel = viewModel()

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* first launch request, result intentionally ignored */ }

    LaunchedEffect(inspectionMode) {
        if (!inspectionMode) {
            val alreadyRequested = automationViewModel.locationPermissionRequested.first()
            val locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyRequested && !locationGranted) {
                automationViewModel.markLocationPermissionRequested()
                fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
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
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = History,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<History> {
                HistoryRoute(
                    onRecordClick = { record ->
                        navController.navigate(
                            MapDetail(
                                id = record.id,
                                title = record.bluetoothName ?: record.bluetoothAddress,
                                latitude = record.latitude,
                                longitude = record.longitude,
                            ),
                        )
                    },
                )
            }
            composable<MapDetail> { entry ->
                MapRoute(
                    detail = entry.toRoute<MapDetail>(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Automation> {
                AutomationRoute()
            }
            composable<Settings> {
                SettingsRoute()
            }
        }
    }
}
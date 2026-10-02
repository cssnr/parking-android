package org.cssnr.parking.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.data.toLocationFix
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.navigation.MapDetail
import org.cssnr.parking.ui.viewmodel.LocationViewModel

@Composable
fun LocationRoute(
    viewModel: LocationViewModel = viewModel(),
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    val uiState by viewModel.locationUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberLocationState(provider = locationProvider)

    var autoRequested by remember { mutableStateOf(false) }
    var launchingPermission by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var showManualConfirm by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        launchingPermission = false
        if (results.values.any { it }) {
            pendingSave = true
        } else {
            saveFailed = true
        }
    }

    LaunchedEffect(locationState.permission) {
        if (autoRequested) return@LaunchedEffect
        val permission = locationState.permission
        if (permission is LocationPermission.NotGranted && permission.canRequest != false) {
            autoRequested = true
            locationState.requestPermission()
        }
    }

    val permission = locationState.permission
    val permissionFinished = permission is LocationPermission.Granted ||
        (permission is LocationPermission.NotGranted &&
            (permission.shouldShowRationale || permission.canRequest == false))

    LaunchedEffect(pendingSave, locationState.lastLocation) {
        if (!pendingSave) return@LaunchedEffect
        val measurement = locationState.lastLocation ?: return@LaunchedEffect
        viewModel.addManualLocation(measurement.toLocationFix())
        pendingSave = false
    }

    val requestManualSave: () -> Unit = {
        saveFailed = false
        showManualConfirm = false
        if (context.hasLocationPermission()) {
            // Save immediately when a fix is already available. Going through
            // pendingSave in that case shows the "Getting your current location"
            // dialog for a single frame before the LaunchedEffect below saves and
            // clears it, which reads as a flash.
            val measurement = locationState.lastLocation
            if (measurement != null) {
                viewModel.addManualLocation(measurement.toLocationFix())
            } else {
                pendingSave = true
            }
        } else {
            launchingPermission = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    val detail = uiState.latestRecord?.let { record ->
        val latitude = record.latitude
        val longitude = record.longitude
        val title = record.displayName
        // Only a located record can be shown on the map. A disconnect recorded
        // without a position is skipped here, the same way the History screen will
        // not navigate to one.
        if (latitude == null || longitude == null || title == null) {
            null
        } else {
            MapDetail(
                id = record.id,
                title = title,
                device = record.bluetoothName ?: record.bluetoothAddress,
                latitude = latitude,
                longitude = longitude,
                timestamp = record.timestamp,
            )
        }
    }

    MapScreen(
        detail = detail,
        title = detail?.title ?: "Location",
        userPosition = locationState.lastLocation?.position,
        onBack = null,
        trackingStatus = trackingStatus,
        onTrackingStatusClick = onTrackingStatusClick,
        onAddManualLocation = {
            saveFailed = false
            showManualConfirm = true
        },
        isSavingLocation = pendingSave,
    )

    val showInitialPrompt = uiState.shouldShowInitialPrompt &&
        permissionFinished &&
        !pendingSave &&
        !launchingPermission

    if (pendingSave) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving Location") },
            text = { Text("Getting your current location...") },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingSave = false }) {
                    Text("Cancel")
                }
            },
        )
    } else if (showManualConfirm && !launchingPermission) {
        AlertDialog(
            onDismissRequest = { showManualConfirm = false },
            title = { Text("Manually Park Car") },
            text = { Text("Park car at your current location?") },
            confirmButton = {
                TextButton(onClick = requestManualSave) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    } else if (showInitialPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissInitialLocationPrompt() },
            title = { Text("Add Parking Location") },
            text = {
                when {
                    saveFailed -> Text(
                        "Location permission is required to save your parking spot. " +
                            "Enable location access in system settings, then try again."
                    )
                    else -> Text(
                        "No parking location saved yet. " +
                            "Save your current location as your parking spot, " +
                            "or skip and it will be recorded automatically next time " +
                            "your car disconnects."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = requestManualSave,
                ) {
                    Text("Add Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInitialLocationPrompt() }) {
                    Text("Skip")
                }
            },
        )
    } else if (saveFailed) {
        AlertDialog(
            onDismissRequest = { saveFailed = false },
            title = { Text("Location Permission Required") },
            text = {
                Text(
                    "Location permission is required to save your parking spot. " +
                        "Enable location access in system settings, then try again."
                )
            },
            confirmButton = {
                TextButton(onClick = { saveFailed = false }) {
                    Text("OK")
                }
            },
        )
    }
}

private fun android.content.Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
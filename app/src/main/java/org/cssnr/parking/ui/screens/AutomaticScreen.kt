package org.cssnr.parking.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.data.LocationProvider
import org.cssnr.parking.ui.AutomaticTrackingHeader
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.theme.ParKingTheme
import org.cssnr.parking.ui.viewmodel.AutomaticViewModel

@Composable
fun AutomaticRoute(
    viewModel: AutomaticViewModel = viewModel(),
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    val context = LocalContext.current

    val automaticEnabled by viewModel.automaticEnabled.collectAsStateWithLifecycle()
    val selectedBluetoothDevices by viewModel.selectedBluetoothDevices.collectAsStateWithLifecycle()

    var fineLocationGranted by remember { mutableStateOf(LocationProvider.hasLocationPermission(context)) }
    var backgroundLocationGranted by remember {
        mutableStateOf(context.hasBackgroundLocationPermission())
    }
    var bluetoothConnectGranted by remember { mutableStateOf(context.hasBluetoothConnectPermission()) }
    var showBackgroundRationaleDialog by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(emptyList<BondedDevice>()) }
    var pendingBackgroundRequest by remember { mutableStateOf(false) }
    var autoEnablePending by remember { mutableStateOf(false) }

    val openBackgroundLocationSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationGranted = granted
        if (granted) {
            if (autoEnablePending) {
                autoEnablePending = false
                viewModel.setAutomaticEnabled(true)
            }
        } else {
            autoEnablePending = false
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !context.shouldShowPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                openBackgroundLocationSettings()
            }
        }
    }

    val requestBackgroundLocation = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        fineLocationGranted = granted
        if (granted && pendingBackgroundRequest) {
            if (context.hasBackgroundLocationPermission()) {
                if (autoEnablePending) {
                    autoEnablePending = false
                    viewModel.setAutomaticEnabled(true)
                }
            } else {
                requestBackgroundLocation()
            }
        }
        pendingBackgroundRequest = false
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        bluetoothConnectGranted = granted
        if (granted) {
            showDevicePicker = true
        }
    }

    LaunchedEffect(showDevicePicker, bluetoothConnectGranted) {
        if (bluetoothConnectGranted) {
            pairedDevices = context.getPairedDevices()
        }
    }

    val selectedDeviceNames = remember(selectedBluetoothDevices, pairedDevices) {
        val nameByAddress = pairedDevices.associate { it.address to it.name }
        selectedBluetoothDevices.map { nameByAddress[it] ?: it }.sorted()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fineLocationGranted = LocationProvider.hasLocationPermission(context)
                backgroundLocationGranted = context.hasBackgroundLocationPermission()
                bluetoothConnectGranted = context.hasBluetoothConnectPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundPermissionLabel = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.backgroundPermissionOptionLabel.toString()
        } else {
            "Allow all the time"
        }
    }

    AutomaticScreen(
        automaticEnabled = automaticEnabled,
        fineLocationGranted = fineLocationGranted,
        backgroundLocationGranted = backgroundLocationGranted,
        bluetoothConnectGranted = bluetoothConnectGranted,
        selectedDeviceNames = selectedDeviceNames,
        backgroundPermissionLabel = backgroundPermissionLabel,
        showPermissionRationaleDialog = showBackgroundRationaleDialog,
        onPermissionRationaleGrant = {
            showBackgroundRationaleDialog = false
            requestBackgroundLocation()
        },
        onPermissionRationaleDismiss = {
            showBackgroundRationaleDialog = false
            autoEnablePending = false
        },
        onAutomaticToggle = { enabled ->
            if (enabled) {
                pendingBackgroundRequest = true
                autoEnablePending = true
                if (!fineLocationGranted) {
                    fineLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                } else if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !backgroundLocationGranted
                ) {
                    if (
                        context.shouldShowPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ||
                        viewModel.backgroundPermissionRequested.value
                    ) {
                        showBackgroundRationaleDialog = true
                    } else {
                        viewModel.markBackgroundPermissionRequested()
                        requestBackgroundLocation()
                    }
                } else {
                    autoEnablePending = false
                    pendingBackgroundRequest = false
                    viewModel.setAutomaticEnabled(true)
                }
            } else {
                pendingBackgroundRequest = false
                autoEnablePending = false
                viewModel.setAutomaticEnabled(false)
            }
        },
        onRequestBluetoothPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        onEditDevices = {
            showDevicePicker = true
        },
        onRequestFineLocation = {
            pendingBackgroundRequest = false
            autoEnablePending = false
            fineLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        },
        onRequestBackgroundLocation = {
            if (!fineLocationGranted) {
                pendingBackgroundRequest = true
                autoEnablePending = false
                fineLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            } else {
                pendingBackgroundRequest = false
                autoEnablePending = false
                requestBackgroundLocation()
            }
        },
        trackingStatus = trackingStatus,
        onTrackingStatusClick = onTrackingStatusClick,
    )

    if (showDevicePicker && bluetoothConnectGranted) {
        DevicePickerDialog(
            pairedDevices = pairedDevices,
            selectedAddresses = selectedBluetoothDevices,
            onSave = { addresses ->
                viewModel.setSelectedBluetoothDevices(addresses)
                showDevicePicker = false
            },
            onDismiss = {
                showDevicePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomaticScreen(
    automaticEnabled: Boolean,
    fineLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    bluetoothConnectGranted: Boolean,
    selectedDeviceNames: List<String>,
    backgroundPermissionLabel: String,
    showPermissionRationaleDialog: Boolean,
    onPermissionRationaleGrant: () -> Unit,
    onPermissionRationaleDismiss: () -> Unit,
    onAutomaticToggle: (Boolean) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onEditDevices: () -> Unit,
    onRequestFineLocation: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automatic Parking") },
                actions = {
                    AutomaticTrackingHeader(
                        status = trackingStatus,
                        onClick = onTrackingStatusClick,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (
                automaticEnabled &&
                (!fineLocationGranted || !backgroundLocationGranted)
            ) {
                PermissionWarningBanner(
                    fineLocationGranted = fineLocationGranted,
                    backgroundLocationGranted = backgroundLocationGranted,
                )
            }
            DefineAutomaticTile(
                automaticEnabled = automaticEnabled,
                fineLocationGranted = fineLocationGranted,
                backgroundLocationGranted = backgroundLocationGranted,
                backgroundPermissionLabel = backgroundPermissionLabel,
                onAutomaticToggle = onAutomaticToggle,
            )
            DefineDevicesTile(
                bluetoothConnectGranted = bluetoothConnectGranted,
                selectedDeviceNames = selectedDeviceNames,
                onRequestBluetoothPermission = onRequestBluetoothPermission,
                onEditDevices = onEditDevices,
            )
            DefineGrantLocationTile(
                fineLocationGranted = fineLocationGranted,
                onRequestFineLocation = onRequestFineLocation,
            )
            DefineGrantBackgroundLocationTile(
                fineLocationGranted = fineLocationGranted,
                backgroundLocationGranted = backgroundLocationGranted,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
            )
        }
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = onPermissionRationaleDismiss,
            title = { Text("Background Location Access") },
            text = {
                Text(
                    "Automatic records your parking spot when your car disconnects, " +
                        "even when the app is in the background. Choose \"All the time\" " +
                        "when prompted for location access."
                )
            },
            confirmButton = {
                TextButton(onClick = onPermissionRationaleGrant) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = onPermissionRationaleDismiss) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PermissionWarningBanner(
    fineLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
) {
    val warning = when {
        !fineLocationGranted && !backgroundLocationGranted ->
            "Location and background location permissions are missing."
        !fineLocationGranted ->
            "Location permission is missing. Automatic parking can't record your spot."
        else ->
            "Background location permission is missing. Automatic parking can't record in the background."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = warning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DefineAutomaticTile(
    automaticEnabled: Boolean,
    fineLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    backgroundPermissionLabel: String,
    onAutomaticToggle: (Boolean) -> Unit,
) {
    val subtitle = when {
        !fineLocationGranted -> "Location permission not granted."
        !backgroundLocationGranted ->
            "Needs background location. Enable \"$backgroundPermissionLabel\" in Settings."
        else -> "Records your parking spot when your car disconnects."
    }
    AutomaticCard(
        icon = Icons.Filled.Power,
        title = "Automatic",
        subtitle = subtitle,
        action = {
            Switch(
                checked = automaticEnabled,
                onCheckedChange = onAutomaticToggle,
            )
        },
    )
}

@Composable
private fun DefineDevicesTile(
    bluetoothConnectGranted: Boolean,
    selectedDeviceNames: List<String>,
    onRequestBluetoothPermission: () -> Unit,
    onEditDevices: () -> Unit,
) {
    val subtitle = when {
        !bluetoothConnectGranted -> "Bluetooth access required to list paired devices."
        selectedDeviceNames.isEmpty() -> "Choose which Bluetooth devices this works with."
        else -> selectedDeviceNames.joinToString(", ")
    }
    AutomaticCard(
        icon = Icons.Filled.Bluetooth,
        title = "Devices",
        subtitle = subtitle,
        action = {
            if (bluetoothConnectGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                TextButton(
                    onClick = if (bluetoothConnectGranted) {
                        onEditDevices
                    } else {
                        onRequestBluetoothPermission
                    },
                ) {
                    Text(if (bluetoothConnectGranted) "Edit" else "Allow")
                }
            }
        },
    )
}

@Composable
private fun DefineGrantLocationTile(
    fineLocationGranted: Boolean,
    onRequestFineLocation: () -> Unit,
) {
    val subtitle = if (fineLocationGranted) {
        "Location permission granted."
    } else {
        "Required to record your parking spot when your car disconnects."
    }
    AutomaticCard(
        icon = Icons.Filled.ShareLocation,
        title = "Grant Location Permissions",
        subtitle = subtitle,
        modifier = if (fineLocationGranted) {
            Modifier.alpha(0.5f)
        } else {
            Modifier.clickable { onRequestFineLocation() }
        },
    )
}

@Composable
private fun DefineGrantBackgroundLocationTile(
    fineLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    onRequestBackgroundLocation: () -> Unit,
) {
    val subtitle = when {
        backgroundLocationGranted && fineLocationGranted ->
            "Location and background access granted."
        !fineLocationGranted -> "Grants location and background access as needed."
        else -> "Required to record parking while the app is in the background."
    }
    AutomaticCard(
        icon = Icons.Filled.Layers,
        title = "Grant Background Location",
        subtitle = subtitle,
        modifier = if (backgroundLocationGranted && fineLocationGranted) {
            Modifier.alpha(0.5f)
        } else {
            Modifier.clickable { onRequestBackgroundLocation() }
        },
    )
}

@Composable
private fun DevicePickerDialog(
    pairedDevices: List<BondedDevice>,
    selectedAddresses: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(selectedAddresses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Devices") },
        text = {
            when {
                pairedDevices.isEmpty() -> Text(
                    "No paired Bluetooth devices found. " +
                        "Pair a device in Settings, then try again."
                )
                else -> Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 360.dp),
                ) {
                    pairedDevices.forEach { device ->
                        val checked = device.address in selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClick = {
                                        selection = if (checked) {
                                            selection - device.address
                                        } else {
                                            selection + device.address
                                        }
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selection = if (isChecked) {
                                        selection + device.address
                                    } else {
                                        selection - device.address
                                    }
                                },
                            )
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selection) },
                enabled = pairedDevices.isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AutomaticCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action()
        }
    }
}

private data class BondedDevice(val name: String, val address: String)

@SuppressLint("MissingPermission")
private fun Context.getPairedDevices(): List<BondedDevice> {
    if (!hasBluetoothConnectPermission()) {
        return emptyList()
    }
    val bluetoothManager = getSystemService(BluetoothManager::class.java) ?: return emptyList()
    val adapter = bluetoothManager.adapter ?: return emptyList()
    return adapter.bondedDevices
        ?.map { BondedDevice(it.name ?: it.address, it.address) }
        ?.sortedBy { it.name }
        ?: emptyList()
}

private fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasBluetoothConnectPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowPermissionRationale(permission: String): Boolean =
    (this as? Activity)?.shouldShowRequestPermissionRationale(permission) ?: false

@Preview(showBackground = true)
@Composable
fun AutomaticScreenPreview() {
    ParKingTheme {
        AutomaticScreen(
            automaticEnabled = false,
            fineLocationGranted = true,
            backgroundLocationGranted = false,
            bluetoothConnectGranted = false,
            selectedDeviceNames = emptyList(),
            backgroundPermissionLabel = "Allow all the time",
            showPermissionRationaleDialog = true,
            onPermissionRationaleGrant = {},
            onPermissionRationaleDismiss = {},
            onAutomaticToggle = {},
            onRequestBluetoothPermission = {},
            onEditDevices = {},
            onRequestFineLocation = {},
            onRequestBackgroundLocation = {},
        )
    }
}

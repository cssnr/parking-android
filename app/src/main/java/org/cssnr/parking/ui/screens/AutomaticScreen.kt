package org.cssnr.parking.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import org.cssnr.parking.ui.theme.ParKingTheme
import org.cssnr.parking.ui.viewmodel.AutomaticViewModel

@Composable
fun AutomaticRoute(viewModel: AutomaticViewModel = viewModel()) {
    val context = LocalContext.current

    val automaticEnabled by viewModel.automaticEnabled.collectAsStateWithLifecycle()
    val selectedBluetoothDevices by viewModel.selectedBluetoothDevices.collectAsStateWithLifecycle()

    var fineLocationGranted by remember { mutableStateOf(context.hasFineLocationPermission()) }
    var backgroundLocationGranted by remember {
        mutableStateOf(context.hasBackgroundLocationPermission())
    }
    var bluetoothConnectGranted by remember { mutableStateOf(context.hasBluetoothConnectPermission()) }
    var showBackgroundRationaleDialog by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(emptyList<BondedDevice>()) }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationGranted = granted
        if (granted) {
            viewModel.setAutomaticEnabled(true)
        }
    }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        fineLocationGranted = granted
        if (granted) {
            if (context.hasBackgroundLocationPermission()) {
                viewModel.setAutomaticEnabled(true)
            } else {
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
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
                fineLocationGranted = context.hasFineLocationPermission()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        },
        onPermissionRationaleDismiss = {
            showBackgroundRationaleDialog = false
        },
        onAutomaticToggle = { enabled ->
            if (enabled) {
                if (!fineLocationGranted) {
                    fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                } else {
                    viewModel.setAutomaticEnabled(true)
                }
            } else {
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
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Automatic Parking") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

private fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

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
        )
    }
}
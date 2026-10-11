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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cssnr.parking.R
import org.cssnr.parking.data.LocationProvider
import org.cssnr.parking.data.ParkingNotifier
import org.cssnr.parking.ui.AutomaticTrackingHeader
import org.cssnr.parking.ui.AutomaticTrackingStatus
import org.cssnr.parking.ui.components.SettingsGroup
import org.cssnr.parking.ui.components.SettingsTile
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
    val failureNotificationsEnabled by viewModel.failureNotificationsEnabled.collectAsStateWithLifecycle()

    var fineLocationGranted by remember {
        mutableStateOf(
            LocationProvider.hasLocationPermission(
                context
            )
        )
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(context.hasBackgroundLocationPermission())
    }
    var bluetoothConnectGranted by remember { mutableStateOf(context.hasBluetoothConnectPermission()) }
    var notificationsGranted by remember {
        mutableStateOf(ParkingNotifier.hasNotificationPermission(context))
    }
    var showBackgroundRationaleDialog by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(emptyList<BondedDevice>()) }
    var pendingBackgroundRequest by remember { mutableStateOf(false) }
    var autoEnablePending by remember { mutableStateOf(false) }
    var pendingFailureEnable by remember { mutableStateOf(false) }

    val openBackgroundLocationSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }

    val openNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (!granted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.shouldShowPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            pendingFailureEnable = false
            openNotificationSettings()
        } else {
            notificationsGranted = ParkingNotifier.hasNotificationPermission(context)
            if (notificationsGranted && pendingFailureEnable) {
                pendingFailureEnable = false
                viewModel.setFailureNotificationsEnabled(true)
            } else if (!notificationsGranted) {
                pendingFailureEnable = false
            }
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
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
                notificationsGranted = ParkingNotifier.hasNotificationPermission(context)
                if (pendingFailureEnable && notificationsGranted) {
                    pendingFailureEnable = false
                    viewModel.setFailureNotificationsEnabled(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundFallback = stringResource(R.string.automatic_background_allow_all)
    val backgroundPermissionLabel = remember(context, backgroundFallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.backgroundPermissionOptionLabel.toString()
        } else {
            backgroundFallback
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
        notificationsGranted = notificationsGranted,
        onRequestNotifications = {
            if (ParkingNotifier.hasNotificationPermission(context)) {
                notificationsGranted = true
            } else {
                requestNotificationPermission()
            }
        },
        failureNotificationsEnabled = failureNotificationsEnabled,
        onFailureNotificationsToggle = { enabled ->
            if (!enabled) {
                pendingFailureEnable = false
                viewModel.setFailureNotificationsEnabled(false)
            } else if (ParkingNotifier.hasNotificationPermission(context)) {
                viewModel.setFailureNotificationsEnabled(true)
            } else {
                pendingFailureEnable = true
                requestNotificationPermission()
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
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    failureNotificationsEnabled: Boolean,
    onFailureNotificationsToggle: (Boolean) -> Unit,
    trackingStatus: AutomaticTrackingStatus? = null,
    onTrackingStatusClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automatic_title)) },
                actions = {
                    AutomaticTrackingHeader(
                        status = trackingStatus,
                        onClick = onTrackingStatusClick,
                    )
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (
                automaticEnabled &&
                (!fineLocationGranted || !backgroundLocationGranted)
            ) {
                PermissionWarningBanner(
                    fineLocationGranted = fineLocationGranted,
                    backgroundLocationGranted = backgroundLocationGranted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            SettingsGroup(
                title = stringResource(R.string.automatic_parking_title),
                tiles = listOf(
                    SettingsTile.Toggle(
                        icon = rememberVectorPainter(Icons.Filled.LocationOn),
                        title = stringResource(R.string.automatic_parking_title),
                        summary = when {
                            !fineLocationGranted -> stringResource(R.string.automatic_parking_no_location)
                            !backgroundLocationGranted ->
                                stringResource(
                                    R.string.automatic_parking_needs_background,
                                    backgroundPermissionLabel,
                                )

                            else -> stringResource(R.string.automatic_parking_summary)
                        },
                        checked = automaticEnabled,
                        onCheckedChange = onAutomaticToggle,
                    ),
                    SettingsTile.Action(
                        icon = rememberVectorPainter(Icons.Filled.Bluetooth),
                        title = stringResource(R.string.automatic_devices_title),
                        summary = when {
                            !bluetoothConnectGranted -> stringResource(R.string.automatic_devices_no_permission)
                            selectedDeviceNames.isEmpty() -> stringResource(R.string.automatic_devices_empty)
                            else -> selectedDeviceNames.joinToString(", ")
                        },
                        actionLabel = if (bluetoothConnectGranted) {
                            stringResource(R.string.automatic_devices_edit)
                        } else {
                            stringResource(R.string.automatic_devices_allow)
                        },
                        onClick = if (bluetoothConnectGranted) {
                            onEditDevices
                        } else {
                            onRequestBluetoothPermission
                        },
                    ),
                    SettingsTile.Toggle(
                        icon = rememberVectorPainter(Icons.Filled.Notifications),
                        title = stringResource(R.string.automatic_failure_title),
                        summary = stringResource(R.string.automatic_failure_summary),
                        checked = failureNotificationsEnabled,
                        onCheckedChange = onFailureNotificationsToggle,
                    ),
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.automatic_required_permissions),
                tiles = listOf(
                    SettingsTile.Link(
                        icon = rememberVectorPainter(Icons.Filled.ShareLocation),
                        title = stringResource(R.string.automatic_grant_location),
                        summary = if (fineLocationGranted) {
                            stringResource(R.string.automatic_location_granted)
                        } else {
                            stringResource(R.string.automatic_required_summary)
                        },
                        enabled = !fineLocationGranted,
                        onClick = onRequestFineLocation,
                    ),
                    SettingsTile.Link(
                        icon = rememberVectorPainter(Icons.Filled.Layers),
                        title = stringResource(R.string.automatic_grant_background),
                        summary = if (backgroundLocationGranted && fineLocationGranted) {
                            stringResource(R.string.automatic_background_granted)
                        } else {
                            stringResource(R.string.automatic_required_summary)
                        },
                        enabled = !(backgroundLocationGranted && fineLocationGranted),
                        onClick = onRequestBackgroundLocation,
                    ),
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.automatic_optional_permissions),
                tiles = listOf(
                    SettingsTile.Link(
                        icon = rememberVectorPainter(Icons.Filled.Notifications),
                        title = stringResource(R.string.automatic_grant_notifications),
                        summary = if (notificationsGranted) {
                            stringResource(R.string.automatic_notifications_granted)
                        } else {
                            stringResource(R.string.automatic_notifications_summary)
                        },
                        enabled = !notificationsGranted,
                        onClick = onRequestNotifications,
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = onPermissionRationaleDismiss,
            title = { Text(stringResource(R.string.automatic_rationale_title)) },
            text = {
                Text(stringResource(R.string.automatic_rationale_text))
            },
            confirmButton = {
                TextButton(onClick = onPermissionRationaleGrant) {
                    Text(stringResource(R.string.automatic_rationale_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = onPermissionRationaleDismiss) {
                    Text(stringResource(R.string.automatic_cancel))
                }
            },
        )
    }
}

@Composable
private fun PermissionWarningBanner(
    fineLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val warning = when {
        !fineLocationGranted && !backgroundLocationGranted ->
            stringResource(R.string.automatic_warning_both)

        !fineLocationGranted ->
            stringResource(R.string.automatic_warning_location)

        else ->
            stringResource(R.string.automatic_warning_background)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
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
private fun DevicePickerDialog(
    pairedDevices: List<BondedDevice>,
    selectedAddresses: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(selectedAddresses) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.automatic_picker_title)) },
        text = {
            when {
                pairedDevices.isEmpty() -> Text(stringResource(R.string.automatic_picker_empty))

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
                Text(stringResource(R.string.automatic_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.automatic_cancel))
            }
        },
    )
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
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) ==
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
            backgroundPermissionLabel = stringResource(R.string.automatic_background_allow_all),
            showPermissionRationaleDialog = true,
            onPermissionRationaleGrant = {},
            onPermissionRationaleDismiss = {},
            onAutomaticToggle = {},
            onRequestBluetoothPermission = {},
            onEditDevices = {},
            onRequestFineLocation = {},
            onRequestBackgroundLocation = {},
            notificationsGranted = false,
            onRequestNotifications = {},
            failureNotificationsEnabled = false,
            onFailureNotificationsToggle = {},
        )
    }
}

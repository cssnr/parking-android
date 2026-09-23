package org.cssnr.parking.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cssnr.parking.data.AutomaticRepository
import org.cssnr.parking.data.LocationProvider

enum class AutomaticTrackingStatus {
    ENABLED,
    DISABLED,
    ERROR,
}

@Composable
fun rememberAutomaticTrackingStatus(): AutomaticTrackingStatus? {
    val context = LocalContext.current

    val automaticEnabled by remember(context) {
        AutomaticRepository(context.applicationContext).automaticEnabled
    }.collectAsStateWithLifecycle(initialValue = null)
    var fineLocationGranted by remember {
        mutableStateOf(LocationProvider.hasLocationPermission(context))
    }
    var backgroundLocationGranted by remember {
        mutableStateOf(context.hasBackgroundLocationPermission())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fineLocationGranted = LocationProvider.hasLocationPermission(context)
                backgroundLocationGranted = context.hasBackgroundLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val enabled = automaticEnabled ?: return null

    return when {
        enabled && (!fineLocationGranted || !backgroundLocationGranted) ->
            AutomaticTrackingStatus.ERROR
        enabled -> AutomaticTrackingStatus.ENABLED
        else -> AutomaticTrackingStatus.DISABLED
    }
}

@Composable
fun AutomaticTrackingHeader(
    status: AutomaticTrackingStatus?,
    onClick: () -> Unit,
) {
    val currentStatus = status ?: return
    val icon: ImageVector = when (currentStatus) {
        AutomaticTrackingStatus.ENABLED -> Icons.Filled.LocationOn
        AutomaticTrackingStatus.DISABLED -> Icons.Filled.LocationOff
        AutomaticTrackingStatus.ERROR -> Icons.Filled.WrongLocation
    }
    val label: String = when (currentStatus) {
        AutomaticTrackingStatus.ENABLED -> "Enabled"
        AutomaticTrackingStatus.DISABLED -> "Disabled"
        AutomaticTrackingStatus.ERROR -> "Error"
    }
    val tint = when (currentStatus) {
        AutomaticTrackingStatus.ENABLED -> MaterialTheme.colorScheme.primary
        AutomaticTrackingStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        AutomaticTrackingStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

private fun Context.hasBackgroundLocationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
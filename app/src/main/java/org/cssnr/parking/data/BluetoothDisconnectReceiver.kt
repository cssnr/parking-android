package org.cssnr.parking.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.cssnr.parking.data.db.AppDatabase

/**
 * Manifest-registered receiver for [BluetoothDevice.ACTION_ACL_DISCONNECTED].
 *
 * Its only job is to decide whether this is a disconnect ParKing cares about and
 * hand it to [ParkingWatchService], which records the event and attaches a
 * position to it.
 *
 * Why a service, measured on device rather than assumed: `getCurrentLocation`
 * called straight from this broadcast came back in 112ms with a fix 135 seconds
 * old and never woke the GNSS chip at all. The provider deferred the real work to
 * an alarm. `setMaxUpdateAgeMillis(0)` did not stop it. Foreground status is what
 * makes it block and derive a fix instead, so the service exists to hold that
 * status for the moment the request takes.
 *
 * Why no `lastLocation`, the other obvious choice: it is cached per app, and
 * ParKing requests nothing while the car is being driven, so its value is wherever
 * the phone was before the trip.
 *
 * Nothing here decides whether a position was obtained. If the service cannot be
 * started, this records the event without coordinates rather than dropping it,
 * because a disconnect is the one thing the user cannot recreate and a record with
 * no position can still be placed later from the foreground. Losing the event
 * because the platform refused a foreground service is worse than a blank pin.
 *
 * The connect is deliberately not handled. Nothing is needed before the car stops,
 * and the notification is on screen for about a second, so there is nothing to
 * hold across a drive.
 */
class BluetoothDisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device: BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        // BLUETOOTH_CONNECT may not be granted even when the broadcast arrives, and
        // address is readable without it, so neither read is allowed to throw.
        val address: String = device?.let { runCatching { it.address }.getOrNull() }.orEmpty()
        val name: String? = device?.let(::safeDeviceName)
        Log.d(TAG, "${intent.action} device: $address name: $name")

        if (address.isBlank()) {
            Log.w(TAG, "no address on ${intent.action}, ignoring")
            return
        }

        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                // Hand off to the service. Foreground status is what makes the
                // provider derive a new fix instead of returning the cached one, and
                // the service is what can hold it for the moment the request takes.
                if (!ParkingWatchService.start(applicationContext, address, name)) {
                    // No service, so no foreground status, so no fresh fix worth
                    // waiting for. Record what is certain rather than dropping the
                    // event: the disconnect itself is not reproducible, and a record
                    // with no position can still be placed later from the foreground.
                    Log.w(TAG, "could not start the service, recording $address without a position")
                    ParkingRecorder(
                        AutomaticRepository(applicationContext),
                        HistoryRepository(AppDatabase.getDatabase(applicationContext)),
                        ReverseGeocoder(applicationContext),
                        applicationContext,
                    ).recordDisconnect(address, name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "could not handle ${intent.action} for $address: ${e.message}", e)
            } finally {
                Log.d(TAG, "broadcast window used ${SystemClock.elapsedRealtime() - started}ms")
                scope.cancel()
                pendingResult.finish()
            }
        }
    }

    /**
     * Reads the device name defensively. Requires BLUETOOTH_CONNECT on API 31+,
     * which may not be granted even when the broadcast still arrives.
     */
    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? =
        runCatching { device.name }.getOrNull()

    companion object {
        private const val TAG = "BTReceiver"
    }
}

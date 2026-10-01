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
 * Writes the event, then hands the new row's id to [ParkingWatchService], which
 * attaches a position to it.
 *
 * The event is written here, inside the broadcast window, because it is the one
 * thing the user cannot recreate. A position is best effort and can be lost.
 *
 * The position is never resolved after the fact: a fix taken later is where the
 * user is standing later, not where the car is.
 *
 * Measured on device, `getCurrentLocation` called straight from this broadcast
 * came back in 112ms with a fix 135 seconds old and never woke the GNSS chip. The
 * provider deferred the work to an alarm and `setMaxUpdateAgeMillis(0)` did not
 * stop it, which is why the fix is asked for from a foreground service instead.
 *
 * `lastLocation` is not consulted either: it is cached per app, and ParKing asks
 * for nothing during the drive, so it points at wherever the phone was before the
 * trip.
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
                val record = ParkingRecorder(
                    AutomaticRepository(applicationContext),
                    HistoryRepository(AppDatabase.getDatabase(applicationContext)),
                    ReverseGeocoder(applicationContext),
                    applicationContext,
                ).recordDisconnect(address, name)
                if (record == null) {
                    Log.d(TAG, "$address is not a disconnect ParKing tracks, ignoring")
                    return@launch
                }
                // The event is safe in the database now. Everything past this point
                // is only about the position, so a refusal costs nothing.
                if (!ParkingWatchService.start(applicationContext, record.id)) {
                    Log.w(TAG, "record ${record.id} keeps no position, the service was refused")
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

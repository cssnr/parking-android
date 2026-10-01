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
 * Its only job is to decide whether this is a disconnect ParKing cares about, write
 * the event, and hand the new row's id to [ParkingWatchService], which attaches a
 * position to it.
 *
 * The event is written here, inside the broadcast window, before anything else is
 * attempted. A disconnect is the one thing the user cannot recreate: if the car is
 * towed or the spot is taken, the timestamp is gone forever. A position is a
 * best-effort reading that the platform is free to refuse, so it is the part that
 * can be lost.
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
 * The position is never resolved after the fact. A fix taken later is where the
 * user is standing later, not where the car is, and a record that looks located but
 * is not is worse than one that admits it has no position. If the service cannot be
 * started, the record simply has no coordinates and stays that way.
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
                // is only about the position, so a refusal from here costs nothing
                // but the coordinates.
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

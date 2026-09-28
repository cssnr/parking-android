package org.cssnr.parking.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Manifest-registered receiver for [BluetoothDevice.ACTION_ACL_DISCONNECTED].
 *
 * The disconnect is only used to hand the event to [ParkingWatchService], which is
 * the only place a fresh fix can be obtained: in the background "the location
 * system service computes a new location for your app only a few times each hour".
 * The broadcast itself is not in the foreground and is only allowed to run for
 * about 30 seconds, neither of which suits a real cold-start fix.
 *
 * The connect is deliberately not handled. The fix is only needed once the car has
 * stopped, so the service is started here and stops as soon as the event is
 * written, rather than holding a foreground notification for the whole drive.
 *
 * Filtering reads DataStore, so the broadcast's short lifetime is turned into a
 * goAsync window, bounded by [RECEIVER_BUDGET] so a hung call costs one event
 * rather than an ANR.
 */
class BluetoothDisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        val device: BluetoothDevice? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(RECEIVER_BUDGET) {
                    if (!isSelected(applicationContext, address)) {
                        Log.d(TAG, "$address is not a selected device, ignoring ${intent.action}")
                        return@withTimeout
                    }
                    if (!ParkingWatchService.start(applicationContext, address, name)) {
                        Log.w(TAG, "no service, so no fresh fix, dropping $address")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "could not handle ${intent.action} for $address: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Whether ParKing is tracking this device. The rest of the filtering, including
     * the location permission check, is left to [ParkingRecorder] so the rule lives
     * in one place rather than two.
     */
    private suspend fun isSelected(context: Context, address: String): Boolean {
        val automatic = AutomaticRepository(context)
        if (!automatic.automaticEnabled.first()) return false
        return address in automatic.selectedBluetoothDevices.first()
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

        /**
         * Total wall clock budget for the receiver, covering the preference reads
         * and the service start.
         *
         * goAsync keeps a background broadcast alive for about 30 seconds before the
         * system treats the receiver as non-responsive, so this sits well under that.
         * Nothing on this path should take anything like this long; the budget turns
         * a genuinely hung call into one dropped event instead of an ANR.
         */
        private val RECEIVER_BUDGET = 10.seconds
    }
}

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
import kotlinx.coroutines.launch
import org.cssnr.parking.data.db.AppDatabase

/**
 * Manifest-registered receiver for [BluetoothDevice.ACTION_ACL_DISCONNECTED].
 *
 * Converts the short broadcast lifetime into a goAsync + coroutine window so the
 * suspended [ParkingRecorder] pipeline can run off the main thread.
 */
class BluetoothDisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        Log.d("BTReceiver", "new intent - ACTION_ACL_DISCONNECTED")

        val device: BluetoothDevice? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        Log.d("BTReceiver", "device: $device")
        val address: String = device?.address ?: return
        Log.d("BTReceiver", "address: $address")
        val name: String? = device.let(::safeDeviceName)
        Log.d("BTReceiver", "name: $name")

        if (address.isBlank()) return

        val pendingResult = goAsync()
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        receiverScope.launch {
            try {
                val recorder = ParkingRecorder(
                    AutomaticRepository(context.applicationContext),
                    HistoryRepository(AppDatabase.getDatabase(context)),
                    LocationProvider(context.applicationContext),
                    context.applicationContext,
                )
                recorder.recordDisconnect(address, name)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
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
}

package org.cssnr.parking.data

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.cssnr.parking.R
import org.cssnr.parking.data.db.AppDatabase
import kotlin.time.Duration.Companion.seconds

/**
 * Runs in the foreground just long enough to record one parking event, so ParKing
 * can ask for a location at the moment the car disconnects.
 *
 * Why a service at all: Android states that "if your app is running in the
 * background, the location system service computes a new location for your app
 * only a few times each hour. This is the case even when your app is requesting
 * more frequent location updates", and the fused provider's own reference names
 * the remedy: "apps may also use a foreground location service to maintain their
 * foreground status when they would normally be in the background".
 *
 * The connect is deliberately not handled. The fix is only needed once the car
 * has stopped, so starting here rather than at the connect keeps the mandatory
 * notification off screen for the length of the drive.
 */
class ParkingWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var locationProvider: LocationProvider

    override fun onCreate() {
        super.onCreate()
        ParkingNotifier.createChannel(this)
        locationProvider = LocationProvider(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Before anything else: the system kills a service that does not promote
        // itself to the foreground within a few seconds of being started this way.
        if (!promoteToForeground()) {
            // Without foreground status there is no fresh fix to be had, and a
            // record without one is not worth writing, so the event is dropped.
            Log.e(
                TAG,
                "could not enter the foreground, dropping ${intent?.action} for " +
                    "${intent?.getStringExtra(EXTRA_ADDRESS)}",
            )
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_DEVICE_DISCONNECTED -> onDeviceDisconnected(intent)
            else -> {
                Log.w(TAG, "no action on ${intent?.action}, shutting down")
                shutdown()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Records the event and stops.
     *
     * The work runs here rather than in the broadcast because a background
     * `BroadcastReceiver` is only allowed to run for "30 seconds or even a bit
     * more" and the fresh fix request needs a budget of its own. A service has no
     * such deadline, so the request can be given the time a real satellite fix
     * needs instead of being cut off part way.
     */
    private fun onDeviceDisconnected(intent: Intent) {
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME)
        Log.d(TAG, "device disconnected: $address $name")

        scope.launch {
            try {
                withTimeout(RECORD_BUDGET) {
                    ParkingRecorder(
                        AutomaticRepository(applicationContext),
                        HistoryRepository(AppDatabase.getDatabase(applicationContext)),
                        locationProvider,
                        ReverseGeocoder(applicationContext),
                        applicationContext,
                    ).recordDisconnect(address, name)
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to record the event: ${e.message}", e)
            } finally {
                shutdown()
            }
        }
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Enters the foreground, reporting whether the platform allowed it.
     *
     * This can fail. `startForeground` with a `location` type throws
     * `SecurityException` when the app is not in a state eligible for while-in-use
     * access, and the background start itself throws
     * `ForegroundServiceStartNotAllowedException`. Left uncaught, the first kills
     * the process mid-disconnect and the second leaves the service unable to
     * promote, so the caller has to be able to tell that neither worked.
     *
     * [ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION] arrived in API 29, below this
     * app's minimum, but it is a compile-time constant the compiler inlines by
     * value, so it cannot fail on an older device. Below 29 [ServiceCompat] calls
     * the two-argument startForeground and drops the type, which changes nothing
     * here: the app's foreground process state is what buys the location, not the
     * declared type.
     */
    @SuppressLint("InlinedApi")
    private fun promoteToForeground(): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            ParkingNotifier.TRACKING_NOTIFICATION_ID,
            ParkingNotifier.trackingNotification(this, getString(R.string.notification_watching)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }.onFailure { Log.e(TAG, "startForeground refused: ${it.message}", it) }
        .isSuccess

    companion object {
        private const val TAG = "ParkingWatchService"

        const val ACTION_DEVICE_DISCONNECTED = "org.cssnr.parking.DEVICE_DISCONNECTED"

        const val EXTRA_ADDRESS = "org.cssnr.parking.extra.ADDRESS"
        const val EXTRA_NAME = "org.cssnr.parking.extra.NAME"

        /**
         * Total wall clock budget for recording one event.
         *
         * Generous, because it is dominated by the fresh fix request and the
         * service has no broadcast deadline to respect. It exists only so that a
         * genuinely hung call cannot leave the service running forever with its
         * notification on screen.
         */
        private val RECORD_BUDGET = 45.seconds

        /**
         * Hands a disconnect to the service, starting it.
         *
         * Returns whether the service was started. A false return means the
         * platform refused a background foreground service start, which leaves no
         * way to get a fresh fix, so the caller drops the event.
         */
        fun start(context: Context, address: String, name: String?): Boolean {
            val intent = Intent(context, ParkingWatchService::class.java)
                .setAction(ACTION_DEVICE_DISCONNECTED)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_NAME, name)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException, if this platform does
                // not allow the disconnect broadcast to start a foreground service.
                Log.e(
                    TAG,
                    "could not start the service for $address. Without foreground " +
                        "status Android will not offer a new location, so the event " +
                        "is dropped. Cause: ${e.message}",
                    e,
                )
                false
            }
        }

        /**
         * Stops a running service, for when the user turns automatic tracking off.
         *
         * `stopService` rather than a `startService` carrying a stop action: the
         * latter would start the service in order to stop it, flashing the
         * foreground notification every time tracking is switched off.
         */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ParkingWatchService::class.java)) }
                .onFailure { Log.d(TAG, "nothing to stop: ${it.message}") }
        }
    }
}

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
 * Runs in the foreground just long enough to attach a position to one parking
 * event. It does not record the event: [BluetoothDisconnectReceiver] has already
 * written the row, which is what makes the event safe regardless of what happens
 * in here. This service owns nothing but the coordinates.
 *
 * Foreground status is the reason it exists. Android throttles location for a
 * background app to "a few times each hour", and the fix request made from the
 * broadcast returned a 135 second old position without waking the GNSS chip.
 *
 * The connect is deliberately not handled. The fix is only needed once the car has
 * stopped, so the mandatory notification is not held on screen for the drive.
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
        val recordId = intent?.getLongExtra(EXTRA_RECORD_ID, NO_RECORD_ID) ?: NO_RECORD_ID
        if (intent?.action != ACTION_ATTACH_POSITION || recordId == NO_RECORD_ID) {
            Log.w(TAG, "nothing to place on ${intent?.action}, shutting down")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!promoteToForeground()) {
            // Without foreground status there is no fresh fix to be had. The event
            // is already written and safe; all that is lost is the position, and it
            // is never resolved after the fact because a later fix would be the
            // user's current position rather than the car's.
            Log.e(TAG, "could not enter the foreground, record $recordId keeps no position")
            stopSelf()
            return START_NOT_STICKY
        }

        attachPosition(recordId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Attaches a position to an event the receiver has already written, then stops.
     *
     * Every failure here costs the position and nothing else: the record still
     * exists and still shows when the car was parked.
     *
     * The work runs here rather than in the broadcast because a background
     * `BroadcastReceiver` is only allowed to run for "30 seconds or even a bit
     * more", and a real satellite fix needs a budget of its own.
     */
    private fun attachPosition(recordId: Long) {
        scope.launch {
            try {
                withTimeout(RECORD_BUDGET) {
                    val historyRepository = HistoryRepository(
                        AppDatabase.getDatabase(applicationContext),
                    )
                    val record = historyRepository.getById(recordId)
                    if (record == null) {
                        Log.d(TAG, "record $recordId is gone, nothing to place")
                        return@withTimeout
                    }
                    val fix = locationProvider.getBestFix(FIX_BUDGET)
                    if (fix == null) {
                        // Not retried anywhere. A fix obtained after the fact would
                        // be where the user is standing now, not where the car is.
                        Log.w(TAG, "no fix, record $recordId keeps no position")
                    } else {
                        ParkingRecorder(
                            AutomaticRepository(applicationContext),
                            historyRepository,
                            ReverseGeocoder(applicationContext),
                            applicationContext,
                        ).attachFix(record, fix)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to place record $recordId: ${e.message}", e)
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
     * This can fail: `startForeground` with a `location` type throws
     * `SecurityException` when the app is not eligible for while-in-use access,
     * and the background start itself throws
     * `ForegroundServiceStartNotAllowedException`. A refusal is not worth fighting:
     * the event is already written, so the only thing lost is the position.
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

        const val ACTION_ATTACH_POSITION = "org.cssnr.parking.ATTACH_POSITION"

        const val EXTRA_RECORD_ID = "org.cssnr.parking.extra.RECORD_ID"

        /** Sentinel for an intent that carries no record to place. */
        private const val NO_RECORD_ID = -1L

        /**
         * Total wall clock budget, dominated by the fix request. It exists so a
         * genuinely hung call cannot leave the service and its notification up
         * forever.
         */
        private val RECORD_BUDGET = 45.seconds

        /**
         * How long the fix request may take before the record is left without one.
         */
        private val FIX_BUDGET = 25.seconds

        /**
         * Asks the service to place an already-recorded event.
         *
         * Returns false if the platform refused a background foreground service
         * start, which leaves the record with the position it has, which is none.
         */
        fun start(context: Context, recordId: Long): Boolean {
            val intent = Intent(context, ParkingWatchService::class.java)
                .setAction(ACTION_ATTACH_POSITION)
                .putExtra(EXTRA_RECORD_ID, recordId)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException, if this platform does
                // not allow the disconnect broadcast to start a foreground service.
                Log.e(
                    TAG,
                    "could not start the service for record $recordId. Without " +
                        "foreground status Android will not offer a new location, so " +
                        "the record keeps no position. Cause: ${e.message}",
                    e,
                )
                false
            }
        }
    }
}

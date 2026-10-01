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
 * event, so ParKing can ask for a location at the moment the car disconnects.
 *
 * It does not record the event. [BluetoothDisconnectReceiver] has already written
 * the row by the time this starts, which is what makes the event safe regardless of
 * what happens in here. This service owns nothing but the coordinates.
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
     * The event is already in the database by the time this runs, so this service
     * owns nothing but the coordinates. Every failure mode here costs exactly the
     * position: the record still exists, still shows when the car was parked, and
     * simply carries no pin.
     *
     * The work runs here rather than in the broadcast because a background
     * `BroadcastReceiver` is only allowed to run for "30 seconds or even a bit
     * more" and the fresh fix request needs a budget of its own. A service has no
     * such deadline, so the request can be given the time a real satellite fix
     * needs instead of being cut off part way.
     *
     * Foreground status is the reason this file exists at all. Measured on device, a
     * `getCurrentLocation` issued straight from the broadcast came back in 112ms
     * with a fix 135 seconds old and never woke the GNSS chip; the provider
     * deferred the real work to an alarm. With foreground status it blocks and
     * derives a fix instead. `setMaxUpdateAgeMillis(0)` did not prevent the stale
     * value in the background.
     */
    private fun attachPosition(recordId: Long) {
        Log.d(TAG, "attaching a position to record $recordId")

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
     * This can fail. `startForeground` with a `location` type throws
     * `SecurityException` when the app is not in a state eligible for while-in-use
     * access, and the background start itself throws
     * `ForegroundServiceStartNotAllowedException`. Left uncaught, the first kills
     * the process mid-disconnect and the second leaves the service unable to
     * promote, so the caller has to be able to tell that neither worked.
     *
     * A refusal is not worth fighting. The event is already recorded by the time
     * this runs, so the only thing lost is the position, and the service shuts down
     * immediately rather than lingering without foreground status it will never get.
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
         * Total wall clock budget for placing one event.
         *
         * Generous, because it is dominated by the fresh fix request and the
         * service has no broadcast deadline to respect. It exists only so that a
         * genuinely hung call cannot leave the service running forever with its
         * notification on screen.
         */
        private val RECORD_BUDGET = 45.seconds

        /**
         * How long the fix request may take before the record is left without one.
         *
         * A service has no broadcast deadline, so this is not about the receiver's
         * window. It only exists so a genuinely hung call cannot leave the service
         * and its notification running forever.
         */
        private val FIX_BUDGET = 25.seconds

        /**
         * Asks the service to place an already-recorded event, starting it.
         *
         * Returns whether the service was started. A false return means the platform
         * refused a background foreground service start, which leaves no way to get a
         * fresh fix, so the record keeps the position it has, which is none.
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

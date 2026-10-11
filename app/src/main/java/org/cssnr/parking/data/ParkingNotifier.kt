package org.cssnr.parking.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import org.cssnr.parking.MainActivity
import org.cssnr.parking.R

/**
 * The ongoing notification a foreground service is required to show.
 *
 * It is deliberately secret and low importance: it exists only because the
 * platform will not let a service read a location without one, and the user
 * never asked to hear about it. The service stops as soon as the event is
 * written, so it is on screen for the seconds the fix takes and no longer.
 */
object ParkingNotifier {

    const val TRACKING_CHANNEL_ID = "parking_tracking"
    const val TRACKING_NOTIFICATION_ID = 1

    const val FAILURE_CHANNEL_ID = "parking_failures"
    const val FAILURE_NOTIFICATION_ID = 2

    private const val TAG = "ParkingNotifier"

    /**
     * Safe to call on every service start. Creating a channel that already exists
     * updates its name and description and leaves the user's importance choice
     * alone, because importance is fixed at creation and cannot be raised after.
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKING_CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.service_channel_description)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FAILURE_CHANNEL_ID,
                context.getString(R.string.failure_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.failure_channel_description)
            },
        )
    }

    /**
     * Whether a failure alert would actually be shown. On API 33+ this is the
     * POST_NOTIFICATIONS runtime grant; below that notifications are opt-out,
     * so it reflects the system toggle instead.
     */
    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun trackingNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.md_parking_sign_24px)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    /**
     * Alert shown when a disconnect was recorded but no position could be
     * attached (no GPS underground, fix timed out, service refused). Uses its
     * own id so removing the foreground tracking notification never clears it.
     */
    fun failureNotification(context: Context): Notification =
        NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setSmallIcon(R.drawable.md_parking_sign_24px)
            .setContentTitle(context.getString(R.string.notification_failure_title))
            .setContentText(context.getString(R.string.notification_failure_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notification_failure_text)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    /**
     * Posts the failure alert if the user opted into failure notifications and
     * granted the notification permission. Never throws: a disabled toggle, a
     * missing permission or a blocked channel just means silence.
     *
     * Guarded by `hasNotificationPermission()`; lint cannot see through that
     * helper, hence the suppression. The [runCatching] below also absorbs a
     * [SecurityException] if the grant is revoked between the check and the post.
     */
    @SuppressLint("MissingPermission")
    suspend fun notifyFailure(context: Context) {
        if (!AutomaticRepository(context).failureNotificationsEnabled.first()) {
            Log.d(TAG, "failure notifications are off, skipping failure alert")
            return
        }
        if (!hasNotificationPermission(context)) {
            Log.d(TAG, "notifications not granted, skipping failure alert")
            return
        }
        runCatching {
            createChannel(context)
            NotificationManagerCompat.from(context)
                .notify(FAILURE_NOTIFICATION_ID, failureNotification(context))
        }.onFailure { Log.w(TAG, "could not post failure alert: ${it.message}") }
    }
}

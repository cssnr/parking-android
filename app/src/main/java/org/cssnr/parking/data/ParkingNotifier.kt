package org.cssnr.parking.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
}

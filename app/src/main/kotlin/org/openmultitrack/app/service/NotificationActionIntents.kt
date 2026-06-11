package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.openmultitrack.app.MainActivity

/** Pending intents for notification actions. Activity intents collapse the notification shade. */
object NotificationActionIntents {
    fun broadcast(
        context: Context,
        requestCode: Int,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, SessionTransportReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Opens [MainActivity] and runs [action] — used when the shade should close. */
    fun mainActivity(
        context: Context,
        requestCode: Int,
        action: String,
    ): PendingIntent {
        val intent = SessionTransportActions.openMainActivityIntent(context).setAction(action)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

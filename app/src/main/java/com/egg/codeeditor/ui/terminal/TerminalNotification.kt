package com.egg.codeeditor.ui.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.egg.codeeditor.MainActivity
import com.egg.codeeditor.R

private const val TERMINAL_CHANNEL_ID = "terminal_session"
private const val TERMINAL_NOTIFICATION_ID = 9001
internal const val ACTION_CLOSE_TERMINAL = "com.egg.codeeditor.CLOSE_TERMINAL"

/** Create the notification channel for the terminal session (Android 8+). */
internal fun ensureTerminalChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(TERMINAL_CHANNEL_ID, "Terminal Session", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Shows the active terminal session"
    }
    nm.createNotificationChannel(channel)
}

/** Show the "active session" notification in the notification drawer, with a Close Session action. */
internal fun postTerminalNotification(context: Context, folderPath: String?) {
    ensureTerminalChannel(context)

    // "Close Session" action → sent to the dynamic BroadcastReceiver in EggApp
    val closeIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(ACTION_CLOSE_TERMINAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Tap notification → reopen the app
    val openIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Detect the currently active File Manager folder
    val text = folderPath?.let { "Active folder: $it" } ?: "Alpine shell is running"

    val notification = NotificationCompat.Builder(context, TERMINAL_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Alpine Terminal — Session Active")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setContentIntent(openIntent)
        .addAction(0, "Close Session", closeIntent)
        .setAutoCancel(false)
        .build()

    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(TERMINAL_NOTIFICATION_ID, notification)
}

/** Remove the terminal session notification. */
internal fun cancelTerminalNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(TERMINAL_NOTIFICATION_ID)
}

/** Escape a path for safe use inside a single-quoted POSIX shell string. */
internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/** Build a `cd` command that prefers the /mnt/sdcard bind, then falls back to other aliases. */
internal fun buildCdCommand(androidPath: String): String {
    val suffix = androidPath.removePrefix("/storage/emulated/0")
    val candidates = listOf(
        "/mnt/sdcard$suffix",
        androidPath,
        "/sdcard$suffix"
    ).filter { it.isNotEmpty() }.distinct()
    return candidates.joinToString(" || ") { "cd ${shellQuote(it)} 2>/dev/null" }
}
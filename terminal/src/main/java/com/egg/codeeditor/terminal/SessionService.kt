package com.egg.codeeditor.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.mutableStateMapOf
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

typealias SessionId = String
typealias SessionPwd = String

data class SessionInfo(
    val id: SessionId,
    val pwd: SessionPwd,
    val session: TerminalSession
)

class TerminalSessionService : Service() {
    private val sessions = hashMapOf<SessionId, TerminalSession>()
    private val sessionWorkDirs = mutableMapOf<SessionId, SessionPwd>()
    private val _sessionList = MutableStateFlow<List<SessionId>>(emptyList())
    val sessionList = _sessionList.asStateFlow()
    val currentSession = MutableStateFlow("main")

    inner class SessionBinder : Binder() {
        fun getService(): TerminalSessionService = this@TerminalSessionService

        fun createSession(id: SessionId, client: TerminalSessionClient): SessionInfo {
            val (session, pwd) = MkSession.createSession(this@TerminalSessionService, client, id)
            sessions[id] = session
            sessionWorkDirs[id] = pwd
            _sessionList.update { list -> list + id }
            updateNotification()
            return SessionInfo(id, pwd, session)
        }

        fun getSession(id: SessionId): TerminalSession? = sessions[id]

        private val guestCwds = mutableStateMapOf<SessionId, String>()

        @Synchronized
        fun setGuestCwd(session: TerminalSession, path: String) {
            val id = sessions.entries.firstOrNull { it.value === session }?.key ?: return
            guestCwds[id] = path
        }

        @Synchronized
        fun getCwdAsHostFile(id: SessionId): File? {
            guestCwds[id]?.let { guest ->
                val host = terminalToHostFile(guest)
                if (host.exists()) return host
            }
            return sessionWorkDirs[id]?.let { File(it) }
        }

        @Synchronized
        fun getCwd(id: SessionId): String? = guestCwds[id] ?: sessionWorkDirs[id]

        @Synchronized
        fun terminateSession(id: SessionId) {
            sessions[id]?.finishIfRunning()
            sessions.remove(id)
            _sessionList.update { list -> list - id }
            sessionWorkDirs.remove(id)
            guestCwds.remove(id)
            if (sessions.isEmpty()) {
                stopSelf()
            } else {
                updateNotification()
            }
        }

        @Synchronized
        fun renameSession(oldId: String, newId: String) {
            if (oldId == newId || newId.isEmpty() || newId in _sessionList.value) return
            val session = sessions.remove(oldId) ?: return
            val pwd = sessionWorkDirs.remove(oldId) ?: return
            sessions[newId] = session
            sessionWorkDirs[newId] = pwd
            guestCwds.remove(oldId)?.let { guestCwds[newId] = it }
            _sessionList.update { list -> list.map { if (it == oldId) newId else it } }
            currentSession.update { if (it == oldId) newId else it }
            updateNotification()
        }
    }

    private val binder = SessionBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
        if (wakeLock == null) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Egg::TerminalSessionService")
                .apply { acquire(10 * 60 * 1000L) }
        }
    }

    override fun onDestroy() {
        sessions.forEach { it.value.finishIfRunning() }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val prefs = getSharedPreferences("egg_terminal", MODE_PRIVATE)
        if (prefs.getBoolean("terminate_on_exit", true)) {
            sessions.forEach { it.value.finishIfRunning() }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "egg_terminal",
            "Terminal",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Terminal sessions running" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val count = sessions.size
        return android.app.Notification.Builder(this, "egg_terminal")
            .setContentTitle("Egg Terminal")
            .setContentText("$count session(s) running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(1, createNotification())
    }
}

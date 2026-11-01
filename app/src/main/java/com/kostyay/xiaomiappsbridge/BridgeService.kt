package com.kostyay.xiaomiappsbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.shell.AdbShellPacket
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import okio.Path.Companion.toPath

fun Context.startBridge(action: String? = null) {
    val intent = BridgeService.intent(this).setAction(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
}

class BridgeService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val controller = Executors.newSingleThreadExecutor()
    private val output = StringBuilder()

    @Volatile private var stopped = false

    @Volatile private var mode = Mode.NORMAL

    @Volatile private var listener: AutoCloseable? = null

    @Volatile private var activeAdb: Kadb? = null

    override fun onCreate() {
        super.onCreate()
        SleepTimer.restore(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Connecting to local ADB"))
        Log.i(
            TAG,
            "Diagnostic build 2.0.2-mitv-diagnostic.2; Kadb 2.1.5; model=${Build.MODEL}; Android=${Build.VERSION.RELEASE}; SDK=${Build.VERSION.SDK_INT}"
        )
        worker.execute(::listen)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SleepTimer.FIRE) {
            controller.execute { SleepTimer.fire(this) }
        } else if (intent?.action == ACTION_TEST) {
            mode = Mode.TEST
            updateStatus("Test mode: press a mapped button")
        } else if (intent?.action == ACTION_CAPTURE) {
            mode = Mode.CAPTURE
            updateStatus("Press the remote button to map")
        } else if (intent?.action == ACTION_CANCEL_CAPTURE) {
            mode = Mode.NORMAL
            updateStatus("Key capture canceled")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        updateStatus("Bridge stopped", false)
        runCatching { listener?.close() }
        runCatching { activeAdb?.close() }
        worker.shutdownNow()
        controller.shutdownNow()
        super.onDestroy()
    }

    private fun listen() {
        var identityReady = false
        while (!stopped) {
            var phase = "ADB identity"
            try {
                if (!identityReady) {
                    updateStatus("Preparing local ADB RSA identity", false)
                    KadbCert.configure(
                        OkioFilePrivateKeyStore(File(filesDir, "adbkey.pem").absolutePath.toPath())
                    )
                    KadbCert.ensureReady()
                    identityReady = true
                    Log.i(TAG, "ADB identity ready (private key is never logged)")
                }
                phase = "Connect / RSA authorization"
                updateStatus(
                    "Connecting to ADB. Allow this app's RSA key if prompted; the PC has a separate key.",
                    false
                )
                Log.i(TAG, "Connecting to local TCP 5555; waiting for ADB handshake")
                Kadb.create("127.0.0.1", 5555, connectTimeout = 5_000).use { adb ->
                    activeAdb = adb
                    if (stopped) return
                    val probe = adb.shell("true")
                    check(probe.exitCode == 0) { "ADB shell probe failed: ${probe.exitCode}" }
                    Log.i(TAG, "ADB handshake and shell probe succeeded")
                    phase = "Key event listener"
                    output.setLength(0)
                    updateStatus("Working: ${loadMappings().size} key mapping(s)", true)
                    adb.openShell("exec getevent -lt | grep ' EV_KEY '").use { shell ->
                        listener = shell
                        while (!stopped) {
                            when (val packet = shell.read()) {
                                is AdbShellPacket.StdOut -> consume(packet.payload)

                                is AdbShellPacket.Exit -> throw IOException(
                                    "Key listener exited: ${packet.payload.firstOrNull()?.toInt()?.and(255)}"
                                )

                                is AdbShellPacket.StdError -> Log.w(TAG, "getevent stderr: ${String(packet.payload)}")
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (!stopped) {
                    reportFailure(phase, error, false)
                    try {
                        Thread.sleep(5_000)
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            } finally {
                listener = null
                activeAdb = null
            }
        }
    }

    private fun consume(bytes: ByteArray) {
        output.append(String(bytes))
        var newline = output.indexOf("\n")
        while (newline >= 0) {
            val line = output.substring(0, newline)
            output.delete(0, newline + 1)
            KEY_EVENT.find(line)?.groupValues?.get(1)?.let(::onKey)
            newline = output.indexOf("\n")
        }
    }

    private fun onKey(key: String) {
        if (mode == Mode.CAPTURE) {
            mode = Mode.NORMAL
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CAPTURED_KEY, key).apply()
            updateStatus("Detected $key", true)
        } else {
            val mapping = loadMappings().firstOrNull { it.key == key } ?: return
            if (mode == Mode.NORMAL) {
                controller.execute { runAction(key, mapping) }
                return
            }
            mode = Mode.NORMAL
            updateStatus("OK: $key detected", true)
        }
    }

    private fun runAction(key: String, mapping: KeyMapping) {
        val command = mapping.command() ?: return
        runCatching {
            Kadb.create("127.0.0.1", 5555, connectTimeout = 5_000).use { adb ->
                val response = adb.shell(command)
                check(response.exitCode == 0) { "Mapped command exited: ${response.exitCode}; ${response.allOutput}" }
            }
            updateStatus("Ran mapping for $key", true)
        }.onFailure {
            if (!stopped) reportFailure("Mapped action $key", it, true)
        }
    }

    private fun reportFailure(phase: String, error: Throwable, connected: Boolean) {
        Log.e(TAG, "$phase failed: ${error.message}", error)
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val detail = causes.joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message ?: "no message"}" }
        val hint = when {
            phase == "ADB identity" -> "Local RSA key/certificate failure before authorization. See Diagnostics."

            causes.any {
                it.javaClass.simpleName.contains("Auth", true)
            } -> "Allow this app's RSA key on the TV. PC authorization is separate."

            else -> "Check ADB TCP 5555 and TV authorization. See Diagnostics."
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(LAST_ERROR, "$phase\n$detail\n\n${Log.getStackTraceString(error)}")
            .apply()
        updateStatus("$phase: ${detail.take(240)}\n$hint", connected)
    }

    private fun updateStatus(text: String, adbConnected: Boolean? = null) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(STATUS, text).apply {
            if (adbConnected != null) putBoolean(ADB_CONNECTED, adbConnected)
        }.apply()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TV Key Mapper", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("TV Key Mapper")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_TEST = "com.kostyay.xiaomiappsbridge.TEST"
        const val ACTION_CAPTURE = "com.kostyay.xiaomiappsbridge.CAPTURE"
        const val ACTION_CANCEL_CAPTURE = "com.kostyay.xiaomiappsbridge.CANCEL_CAPTURE"
        const val ACTION_STATUS = "com.kostyay.xiaomiappsbridge.STATUS"
        const val PREFS = "bridge"
        const val STATUS = "status"
        const val LAST_ERROR = "last_error"
        private const val TAG = "TVKeyMapperADB"
        const val ADB_CONNECTED = "adb_connected"
        const val CAPTURED_KEY = "captured_key"
        const val MAPPINGS = "mappings"
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 1
        private val KEY_EVENT = Regex("EV_KEY\\s+((?:KEY|BTN)_[A-Z0-9_]+)\\s+DOWN")

        fun intent(context: Context) = Intent(context, BridgeService::class.java)
    }

    private enum class Mode { NORMAL, TEST, CAPTURE }
}

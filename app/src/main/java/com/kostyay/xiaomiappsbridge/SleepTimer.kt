package com.kostyay.xiaomiappsbridge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import java.io.File
import okio.Path.Companion.toPath

object LocalShell {
    fun run(context: Context, command: String): String {
        KadbCert.configure(OkioFilePrivateKeyStore(File(context.filesDir, "adbkey.pem").absolutePath.toPath()))
        KadbCert.ensureReady()
        return Kadb.create("127.0.0.1", 5555, connectTimeout = 5_000).use { adb ->
            val response = adb.shell(command)
            check(response.exitCode == 0) { "ADB command failed: ${response.allOutput}" }
            response.allOutput
        }
    }
}

object SleepTimer {
    const val FIRE = "com.kostyay.xiaomiappsbridge.TIMER_FIRE"
    private fun prefs(c: Context) = c.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
    private fun boot(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun pending(c: Context) = PendingIntent.getBroadcast(
        c,
        42,
        Intent(c, TimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @Synchronized fun schedule(c: Context, minutes: Int) {
        val alarm = c.getSystemService(AlarmManager::class.java)
        check(Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            "Allow Alarms & reminders in Android settings first"
        }
        val deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
        alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, pending(c))
        prefs(c).edit().putLong("deadline", deadline).putInt("boot", boot(c)).putString("status", "").commit()
        changed(c)
    }

    @Synchronized fun cancel(c: Context, message: String = "No sleep timer") {
        c.getSystemService(AlarmManager::class.java).cancel(pending(c))
        prefs(c).edit().remove("deadline").putString("status", message).commit()
        changed(c)
    }

    @Synchronized fun restore(c: Context) {
        val p = prefs(c)
        val deadline = p.getLong("deadline", 0)
        if (deadline == 0L) return
        if (p.getInt("boot", -2) != boot(c)) {
            cancel(c, "Timer cleared after reboot")
            return
        }
        if (deadline < SystemClock.elapsedRealtime() - 120_000) {
            cancel(c, "Expired timer cleared")
            return
        }
        val alarm = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                maxOf(
                    deadline,
                    SystemClock.elapsedRealtime() + 1000
                ),
                pending(c)
            )
        }
    }
    fun status(c: Context): String {
        val p = prefs(c)
        val deadline = p.getLong("deadline", 0)
        return if (deadline > 0 && p.getInt("boot", -2) == boot(c)) {
            "Sleep timer: ${maxOf(0, (deadline - SystemClock.elapsedRealtime() + 59_999) / 60_000)} min remaining"
        } else {
            p.getString("status", "No sleep timer") ?: "No sleep timer"
        }
    }

    @Synchronized fun fire(c: Context) {
        val p = prefs(c)
        val deadline = p.getLong("deadline", 0)
        if (deadline == 0L || p.getInt("boot", -2) != boot(c) || SystemClock.elapsedRealtime() < deadline) return
        val lock = c.getSystemService(
            PowerManager::class.java
        ).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TVKeyMapper:timer")
        lock.acquire(30_000)
        try {
            // Sleep is idempotent; unlike POWER, this cannot wake an already sleeping TV.
            LocalShell.run(c, "input keyevent 223")
            cancel(c, "Sleep command sent")
        } catch (e: Exception) {
            cancel(c, "Sleep failed: ${e.message}")
        } finally {
            if (lock.isHeld) lock.release()
        }
    }
    private fun changed(c: Context) {
        c.sendBroadcast(Intent(BridgeService.ACTION_STATUS).setPackage(c.packageName))
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startBridge(SleepTimer.FIRE)
    }
}

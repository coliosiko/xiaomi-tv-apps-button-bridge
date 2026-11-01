package com.kostyay.xiaomiappsbridge

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class PanelActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rows: LinearLayout
    private lateinit var subtitle: TextView
    private val tick = object : Runnable {
        override fun run() {
            if (intent.getStringExtra("panel") == "SLEEP_TIMER") subtitle.text = SleepTimer.status(this@PanelActivity)
            handler.postDelayed(this, 1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val timer = intent.getStringExtra("panel") == "SLEEP_TIMER"
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        subtitle = TextView(this).apply {
            textSize = 17f
            setTextColor(TvTheme.MUTED)
            setPadding(0, 10, 0, 16)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 20, 28, 20)
                setBackgroundColor(TvTheme.BACKGROUND)
                addView(
                    TextView(this@PanelActivity).apply {
                        text = if (timer) "Sleep timer" else "Recent apps"
                        textSize = 27f
                        setTextColor(TvTheme.TEXT)
                    }
                )
                addView(subtitle)
                addView(ScrollView(this@PanelActivity).apply { addView(rows) }, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        )
        window.setLayout(
            (resources.displayMetrics.widthPixels * if (timer) 0.48 else 0.62).toInt(),
            (
                resources.displayMetrics.heightPixels *
                    0.82
                ).toInt()
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            dimAmount = 0.30f
            gravity = Gravity.CENTER
        }
        if (timer) {
            showTimer()
            rows.getChildAt(0)?.requestFocus()
        } else {
            showRecents()
        }
        handler.post(tick)
    }
    private fun showTimer() {
        subtitle.text = SleepTimer.status(this)
        listOf(30, 60, 90, 120).forEach { minutes ->
            addButton("$minutes minutes") {
                runCatching { SleepTimer.schedule(this, minutes) }.onSuccess { finish() }
                    .onFailure { subtitle.text = it.message }
            }
        }
        addButton("Cancel timer") {
            SleepTimer.cancel(this)
            finish()
        }
        addButton("Back") { finish() }
    }
    private fun showRecents() {
        subtitle.text = "Loading from ADB…"
        worker.execute {
            val result = runCatching {
                val dump = LocalShell.run(this, "dumpsys activity recents")
                check(!dump.contains("Permission Denial")) { "ADB cannot read recent tasks" }
                val homes = packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    0
                )
                    .map { it.activityInfo.packageName }.toSet()
                RecentParser.packages(dump).filter {
                    it != packageName && it !in homes &&
                        it !in setOf("com.spocky.projengmenu", "com.mitv.tvhome.atv", "com.mitv.tvhome.mitvplus")
                }
                    .mapNotNull { pkg ->
                        val launch =
                            packageManager.getLeanbackLaunchIntentForPackage(pkg)
                                ?: packageManager.getLaunchIntentForPackage(pkg)
                        launch?.let {
                            runCatching {
                                val info = packageManager.getApplicationInfo(pkg, 0)
                                Triple(pkg, packageManager.getApplicationLabel(info).toString(), info)
                            }.getOrNull()
                        }
                    }.take(20)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { apps ->
                    subtitle.text =
                        if (apps.isEmpty()) "No recent apps found." else "ADB recents (may no longer be running)"
                    apps.forEach { (pkg, label, info) ->
                        val button = addButton(label) {
                            val launch =
                                packageManager.getLeanbackLaunchIntentForPackage(pkg)
                                    ?: packageManager.getLaunchIntentForPackage(pkg)
                            runCatching {
                                checkNotNull(launch)
                                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }
                                .onFailure { subtitle.text = "Cannot open $label: ${it.message}" }
                        }
                        runCatching {
                            val icon = packageManager.getApplicationIcon(info)
                            val size = (40 * resources.displayMetrics.density).toInt()
                            icon.setBounds(0, 0, size, size)
                            button.setCompoundDrawables(icon, null, null, null)
                            button.compoundDrawablePadding = 20
                        }
                    }
                }.onFailure { subtitle.text = "Could not load recent apps: ${it.message}" }
                addButton("Back") { finish() }
                rows.getChildAt(0)?.requestFocus()
            }
        }
    }
    private fun addButton(title: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = title
            textSize = 20f
            isAllCaps = false
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(20, 8, 20, 8)
            setTextColor(TvTheme.TEXT)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(TvTheme.ORANGE))
                addState(intArrayOf(android.R.attr.state_pressed), shape(TvTheme.ORANGE))
                addState(intArrayOf(), shape(TvTheme.BUTTON))
            }
            setOnClickListener { action() }
        }
        val rowHeight = if (intent.getStringExtra("panel") == "SLEEP_TIMER") 48 else 56
        rows.addView(
            button,
            LinearLayout.LayoutParams(-1, (rowHeight * resources.displayMetrics.density).toInt()).apply {
                bottomMargin =
                    8
            }
        )
        return button
    }
    private fun shape(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 12f
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }
}

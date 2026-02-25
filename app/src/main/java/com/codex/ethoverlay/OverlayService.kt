package com.codex.ethoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var panelUrl: String = "https://cdaae6da237dbb.lhr.life/?mini=1"
    private var apiUrl: String = toApiUrl(panelUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var polling = false

    private var valueUpdated: TextView? = null
    private var valuePrice: TextView? = null
    private var valueSignal: TextView? = null
    private var valueTrend: TextView? = null
    private var valueEntry: TextView? = null
    private var valueSl: TextView? = null
    private var valueTp1: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                panelUrl = normalizeUrl(intent?.getStringExtra(EXTRA_URL))
                apiUrl = toApiUrl(panelUrl)
                startInForeground()
                showOverlay()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun startInForeground() {
        val channelId = "eth_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ETH Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ETH 浮窗运行中")
            .setContentText("点击应用可修改链接或关闭")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun showOverlay() {
        if (overlayRoot != null) {
            startPolling()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 16
        params.y = 160

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0F1E22.toInt())
            elevation = 12f
        }
        val pad = dp(8)
        root.setPadding(pad, pad, pad, pad)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF193038.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "ETH 15m"
            setTextColor(0xFFEAFCF3.toInt())
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMin = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFEAFCF3.toInt())
            contentDescription = "最小化"
        }

        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFEAFCF3.toInt())
            contentDescription = "关闭"
        }

        header.addView(title)
        header.addView(btnMin)
        header.addView(btnClose)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(250))
        }

        valueUpdated = addItem(content, "更新", "--")
        valuePrice = addItem(content, "价格", "-")
        valueSignal = addItem(content, "信号", "-")
        valueTrend = addItem(content, "趋势", "-")
        valueEntry = addItem(content, "进场", "-")
        valueSl = addItem(content, "止损", "-")
        valueTp1 = addItem(content, "止盈1", "-")

        root.addView(header)
        root.addView(content)

        btnMin.setOnClickListener {
            val hidden = content.visibility == View.GONE
            content.visibility = if (hidden) View.VISIBLE else View.GONE
            btnMin.setImageResource(
                if (hidden) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        attachDragListener(header, params)

        windowManager?.addView(root, params)
        overlayRoot = root
        startPolling()
    }

    private fun attachDragListener(handle: View, params: WindowManager.LayoutParams) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX - (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        windowManager?.updateViewLayout(overlayRoot, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun removeOverlay() {
        stopPolling()
        overlayRoot?.let { root ->
            windowManager?.removeView(root)
        }
        overlayRoot = null
    }

    private fun normalizeUrl(raw: String?): String {
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty()) return panelUrl
        return if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            candidate
        } else {
            "https://$candidate"
        }
    }

    private fun toApiUrl(url: String): String {
        return url
            .replace(Regex("/\\?mini=1$"), "")
            .replace(Regex("/$"), "") + "/api/state"
    }

    private fun addItem(parent: LinearLayout, label: String, value: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val left = TextView(this).apply {
            text = "$label:"
            setTextColor(0xFF9FC7B3.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val right = TextView(this).apply {
            text = value
            setTextColor(0xFFEAFCF3.toInt())
            textSize = 13f
        }
        row.addView(left)
        row.addView(right)
        parent.addView(row)
        return right
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        schedulePoll(0)
    }

    private fun stopPolling() {
        polling = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun schedulePoll(delayMs: Long) {
        mainHandler.postDelayed({
            if (!polling) return@postDelayed
            fetchState()
        }, delayMs)
    }

    private fun fetchState() {
        thread {
            try {
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(body)
                val snap = root.optJSONObject("snapshot")
                val status = root.optString("status", "-")
                val updated = root.optString("updatedAt", "-")

                if (snap == null) {
                    postUi("-", "-", "-", "-", "-", "-", "-", "状态: $status")
                } else {
                    val live = snap.optDouble("livePrice")
                    val signal = snap.optString("signal", "-").uppercase(Locale.getDefault())
                    val trend = snap.optString("trend", "-")
                    val entry = snap.optDouble("entry")
                    val sl = snap.optDouble("sl")
                    val tp1 = snap.optDouble("tp1")
                    postUi(
                        formatNum(live),
                        signal,
                        trend,
                        formatNum(entry),
                        formatNum(sl),
                        formatNum(tp1),
                        trimIso(updated),
                        "状态: $status"
                    )
                }
            } catch (_: Exception) {
                postUi("-", "ERR", "-", "-", "-", "-", "--:--:--", "网络异常")
            } finally {
                schedulePoll(3000)
            }
        }
    }

    private fun postUi(
        price: String,
        signal: String,
        trend: String,
        entry: String,
        sl: String,
        tp1: String,
        updated: String,
        statusLine: String
    ) {
        mainHandler.post {
            valueUpdated?.text = updated
            valuePrice?.text = price
            valueSignal?.text = signal
            valueTrend?.text = trend
            valueEntry?.text = entry
            valueSl?.text = sl
            valueTp1?.text = tp1
            if (statusLine.isNotEmpty()) {
                valueTrend?.text = "$trend ($statusLine)"
            }
        }
    }

    private fun trimIso(iso: String): String {
        if (iso.length < 19) return iso
        return iso.substring(11, 19)
    }

    private fun formatNum(v: Double): String {
        if (v.isNaN()) return "-"
        return String.format(Locale.US, "%.2f", v)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.codex.ethoverlay.action.START"
        const val ACTION_STOP = "com.codex.ethoverlay.action.STOP"
        const val EXTRA_URL = "extra_url"
    }
}

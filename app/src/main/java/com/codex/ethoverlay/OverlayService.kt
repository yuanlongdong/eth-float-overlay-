package com.codex.ethoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var panelUrl: String = "https://api.binance.com"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var polling = false

    private var valueUpdated: TextView? = null
    private var valuePrice: TextView? = null
    private var valueSignal: TextView? = null
    private var valueTrend: TextView? = null
    private var valueEntry: TextView? = null
    private var valueSl: TextView? = null
    private var valueTp1: TextView? = null
    private var lastSignal: String = ""
    private var lastAlertAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                panelUrl = normalizeUrl(intent?.getStringExtra(EXTRA_URL))
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
            layoutParams = LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT)
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
                val conn = URL("https://api.binance.com/api/v3/klines?symbol=ETHUSDT&interval=15m&limit=300").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val arr = JSONArray(body)
                val n = arr.length()
                if (n < 220) throw IllegalStateException("kline too short")

                val close = DoubleArray(n)
                val high = DoubleArray(n)
                val low = DoubleArray(n)
                val ts = LongArray(n)
                for (i in 0 until n) {
                    val k = arr.getJSONArray(i)
                    ts[i] = k.getLong(0)
                    high[i] = k.getString(2).toDouble()
                    low[i] = k.getString(3).toDouble()
                    close[i] = k.getString(4).toDouble()
                }

                val ema20 = ema(close, 20)
                val ema50 = ema(close, 50)
                val ema200 = ema(close, 200)
                val rsi14 = rsi(close, 14)
                val atr14 = atr(high, low, close, 14)

                val i = n - 1
                val c = i - 1
                val live = close[i]
                val cClose = close[c]

                val trend = if (ema20[c] > ema50[c] && ema50[c] > ema200[c]) {
                    "bullish"
                } else if (ema20[c] < ema50[c] && ema50[c] < ema200[c]) {
                    "bearish"
                } else {
                    "mixed"
                }

                val signal = if (trend == "bullish" && rsi14[c] in 45.0..70.0 && cClose > ema20[c]) {
                    "LONG"
                } else if (trend == "bearish" && rsi14[c] in 30.0..55.0 && cClose < ema20[c]) {
                    "SHORT"
                } else {
                    "WAIT"
                }

                val from = maxOf(0, c - 31)
                var swingHigh = high[from]
                var swingLow = low[from]
                for (j in from..c) {
                    if (high[j] > swingHigh) swingHigh = high[j]
                    if (low[j] < swingLow) swingLow = low[j]
                }

                val entry = if (signal == "LONG") maxOf(high[c], ema20[c]) else minOf(low[c], ema20[c])
                val sl = if (signal == "LONG") minOf(ema50[c], swingLow) - 0.25 * atr14[c] else maxOf(ema50[c], swingHigh) + 0.25 * atr14[c]
                val rr = kotlin.math.abs(entry - sl)
                val tp1 = if (signal == "LONG") entry + rr else entry - rr

                postUi(
                    price = formatNum(live),
                    signal = signal,
                    trend = trend,
                    entry = formatNum(entry),
                    sl = formatNum(sl),
                    tp1 = formatNum(tp1),
                    updated = trimTime(ts[i]),
                    statusLine = "本地策略"
                )
                maybeAlertSignalChanged(signal)
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

    private fun trimTime(ms: Long): String {
        val sec = ms / 1000
        val h = (sec / 3600) % 24
        val m = (sec / 60) % 60
        val s = sec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun formatNum(v: Double): String {
        if (v.isNaN()) return "-"
        return String.format(Locale.US, "%.2f", v)
    }

    private fun maybeAlertSignalChanged(signal: String) {
        if (lastSignal.isEmpty()) {
            lastSignal = signal
            return
        }
        if (signal == lastSignal) return

        val now = System.currentTimeMillis()
        if (now - lastAlertAtMs < 15000) {
            lastSignal = signal
            return
        }
        lastSignal = signal
        lastAlertAtMs = now

        mainHandler.post {
            playSignalTone(signal)
            vibrateSignal()
        }
    }

    private fun playSignalTone(signal: String) {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        val toneType = when (signal) {
            "LONG" -> ToneGenerator.TONE_PROP_ACK
            "SHORT" -> ToneGenerator.TONE_SUP_ERROR
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        tone.startTone(toneType, 260)
        mainHandler.postDelayed({ tone.release() }, 350)
    }

    private fun vibrateSignal() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(220)
        }
    }

    private fun ema(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size)
        val k = 2.0 / (period + 1)
        out[0] = values[0]
        for (i in 1 until values.size) {
            out[i] = values[i] * k + out[i - 1] * (1 - k)
        }
        return out
    }

    private fun rsi(values: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(values.size)
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d >= 0) avgGain += d else avgLoss += -d
        }
        avgGain /= period
        avgLoss /= period
        out[period] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            val gain = if (d > 0) d else 0.0
            val loss = if (d < 0) -d else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            out[i] = if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)
        }
        for (i in 0 until period) out[i] = out[period]
        return out
    }

    private fun atr(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int): DoubleArray {
        val tr = DoubleArray(close.size)
        tr[0] = high[0] - low[0]
        for (i in 1 until close.size) {
            val a = high[i] - low[i]
            val b = kotlin.math.abs(high[i] - close[i - 1])
            val c = kotlin.math.abs(low[i] - close[i - 1])
            tr[i] = maxOf(a, b, c)
        }
        val out = DoubleArray(close.size)
        var prev = 0.0
        for (i in 0 until period) prev += tr[i]
        prev /= period
        for (i in tr.indices) {
            if (i < period) out[i] = prev
            else {
                prev = (prev * (period - 1) + tr[i]) / period
                out[i] = prev
            }
        }
        return out
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.codex.ethoverlay.action.START"
        const val ACTION_STOP = "com.codex.ethoverlay.action.STOP"
        const val EXTRA_URL = "extra_url"
    }
}

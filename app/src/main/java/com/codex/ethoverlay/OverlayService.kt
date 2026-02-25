package com.codex.ethoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var webView: WebView? = null
    private var panelUrl: String = "https://c60bcff2154b64.lhr.life/?mini=1"

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
                showOverlay(panelUrl)
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

    private fun showOverlay(url: String) {
        if (overlayRoot != null) {
            webView?.loadUrl(url)
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

        val wv = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(320))
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            loadUrl(url)
        }

        root.addView(header)
        root.addView(wv)

        btnMin.setOnClickListener {
            val hidden = wv.visibility == View.GONE
            wv.visibility = if (hidden) View.VISIBLE else View.GONE
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
        webView = wv
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
        webView?.destroy()
        webView = null
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "com.codex.ethoverlay.action.START"
        const val ACTION_STOP = "com.codex.ethoverlay.action.STOP"
        const val EXTRA_URL = "extra_url"
    }
}

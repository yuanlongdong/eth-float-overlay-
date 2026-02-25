package com.codex.ethoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.inputUrl)
        statusText = findViewById(R.id.txtStatus)

        val grantBtn = findViewById<Button>(R.id.btnGrant)
        val startBtn = findViewById<Button>(R.id.btnStart)
        val stopBtn = findViewById<Button>(R.id.btnStop)

        val pref = getSharedPreferences("overlay_pref", MODE_PRIVATE)
        val savedUrl = pref.getString("panel_url", "") ?: ""
        urlInput.setText(savedUrl)

        grantBtn.setOnClickListener { openOverlayPermission() }

        startBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            pref.edit().putString("panel_url", url).apply()

            if (!canDrawOverlays()) {
                statusText.text = "请先授予悬浮窗权限"
                openOverlayPermission()
                return@setOnClickListener
            }

            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_URL, url)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            statusText.text = "浮窗已启动（原生行情模式）"
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(intent)
            statusText.text = "浮窗已停止"
        }

        statusText.text = if (canDrawOverlays()) "悬浮窗权限: 已授权" else "悬浮窗权限: 未授权"
    }

    override fun onResume() {
        super.onResume()
        statusText.text = if (canDrawOverlays()) "悬浮窗权限: 已授权" else "悬浮窗权限: 未授权"
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

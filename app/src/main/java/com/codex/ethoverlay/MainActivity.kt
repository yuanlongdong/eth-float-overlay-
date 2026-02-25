package com.codex.ethoverlay

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

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
        val updateBtn = findViewById<Button>(R.id.btnUpdate)

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

        updateBtn.setOnClickListener {
            checkUpdate(manual = true)
        }

        statusText.text = if (canDrawOverlays()) "悬浮窗权限: 已授权" else "悬浮窗权限: 未授权"
        checkUpdate(manual = false)
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

    private fun checkUpdate(manual: Boolean) {
        if (manual) statusText.text = "正在检查更新..."

        thread {
            try {
                val conn = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "eth-overlay-app")

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(body)
                val latestTag = root.optString("tag_name", "")
                val latestName = root.optString("name", "")
                val assets = root.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name") == "app-debug.apk") {
                            apkUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                }

                val current = getCurrentVersionName()
                val hasUpdate = isRemoteNewer(latestTag, current)

                runOnUiThread {
                    if (hasUpdate && apkUrl.isNotEmpty()) {
                        statusText.text = "发现新版本: $latestTag"
                        showUpdateDialog(latestTag, latestName, apkUrl)
                    } else if (manual) {
                        statusText.text = "当前已是最新版（$current）"
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (manual) statusText.text = "检查更新失败，请稍后重试"
                }
            }
        }
    }

    private fun showUpdateDialog(tag: String, name: String, apkUrl: String) {
        val msg = if (name.isBlank()) "检测到新版本 $tag，是否下载更新？" else "$name ($tag)\n\n是否下载更新？"
        AlertDialog.Builder(this)
            .setTitle("发现更新")
            .setMessage(msg)
            .setNegativeButton("稍后") { d, _ -> d.dismiss() }
            .setPositiveButton("立即下载") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
            }
            .show()
    }

    private fun isRemoteNewer(remoteTag: String, currentVersion: String): Boolean {
        val r = parseVersion(remoteTag)
        val c = parseVersion(currentVersion)
        if (r == null || c == null) return remoteTag != currentVersion
        return compareVersion(r, c) > 0
    }

    private fun parseVersion(raw: String): IntArray? {
        val m = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(raw) ?: return null
        return intArrayOf(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt()
        )
    }

    private fun compareVersion(a: IntArray, b: IntArray): Int {
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] - b[i]
        }
        return 0
    }

    private fun getCurrentVersionName(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/yuanlongdong/eth-float-overlay-/releases/latest"
    }
}

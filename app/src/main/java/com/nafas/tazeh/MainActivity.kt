package com.nafas.tazeh

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingBackup: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 38, 31)
        window.navigationBarColor = android.graphics.Color.rgb(5, 38, 31)

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(5, 38, 31))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            }
            addJavascriptInterface(AndroidBridge(), "NafasAndroid")
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("window.handleAndroidBack && window.handleAndroidBack()", null)
    }

    @Deprecated("Deprecated by Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 402) {
            val content = pendingBackup
            pendingBackup = null
            val uri: Uri? = data?.data
            if (resultCode == RESULT_OK && uri != null && content != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    }
                    webView.evaluateJavascript("toast('نسخه پشتیبان ذخیره شد')", null)
                } catch (_: Exception) {
                    Toast.makeText(this, "ذخیره فایل انجام نشد", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 401)
                }
            }
        }

        @JavascriptInterface
        fun scheduleDailyReminder(hour: Int, minute: Int, message: String) {
            val safeHour = hour.coerceIn(0, 23)
            val safeMinute = minute.coerceIn(0, 59)
            getSharedPreferences("nafas_reminder", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", true)
                .putInt("hour", safeHour)
                .putInt("minute", safeMinute)
                .putString("message", message.take(180))
                .apply()

            val intent = Intent(this@MainActivity, ReminderReceiver::class.java).apply {
                putExtra("message", message.take(180))
            }
            val pending = PendingIntent.getBroadcast(
                this@MainActivity, 712, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, safeHour)
                set(java.util.Calendar.MINUTE, safeMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            alarm.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, next.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending
            )
        }

        @JavascriptInterface
        fun cancelDailyReminder() {
            getSharedPreferences("nafas_reminder", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", false).apply()
            val intent = Intent(this@MainActivity, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                this@MainActivity, 712, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }

        @JavascriptInterface
        fun requestBackup(json: String) {
            runOnUiThread {
                pendingBackup = json.take(1_000_000)
                val create = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "nafas-tazeh-backup.json")
                }
                startActivityForResult(create, 402)
            }
        }

        @JavascriptInterface
        fun showMessage(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message.take(120), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

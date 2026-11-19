package com.example.sonybuds

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val info = TextView(this).apply {
            text = "1. Cấp quyền Bluetooth & thông báo\n2. Cấp quyền hiển thị trên ứng dụng khác\n3. Bật dịch vụ, rồi mở nắp hộp sạc WF-1000XM4.\n\nLưu ý: tắt app Sony | Headphones Connect trong lúc test (nó chiếm kết nối)."
            setPadding(48, 96, 48, 48)
        }
        val btn = Button(this).apply {
            text = "Cấp quyền & bật dịch vụ"
            setOnClickListener { setup() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info); addView(btn)
        })
    }

    private fun setup() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) perms += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, BudsService::class.java))
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, BudsService::class.java))
    }
}

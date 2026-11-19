package com.example.sonybuds

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
class BudsService : Service() {

    private val ui = Handler(Looper.getMainLooper())
    private var socket: BluetoothSocket? = null
    private var popup: View? = null
    private val bat = Battery()
    @Volatile private var session = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33)
                i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
            if (dev.name?.contains("WF-1000XM4", true) != true) return

            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> connect(dev)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> { session++; closeAll() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val ch = "buds"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "Buds", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, ch)
            .setContentTitle("Đang chờ WF-1000XM4")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, n)
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        // Nếu tai nghe đã kết nối sẵn thì lấy luôn
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices
            ?.firstOrNull { it.name?.contains("WF-1000XM4", true) == true }
            ?.let { connect(it) }
    }

    private fun connect(dev: BluetoothDevice) {
        val my = ++session
        thread {
            repeat(6) { attempt ->
                if (my != session) return@thread
                try {
                    Thread.sleep(if (attempt == 0) 1500L else 2500L)
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
                    val s = dev.createRfcommSocketToServiceRecord(SonyProtocol.SONY_UUID)
                    s.connect()
                    socket = s
                    runSession(s, my)
                    return@thread
                } catch (_: Exception) { /* thử lại */ }
            }
        }
    }

    private fun runSession(s: BluetoothSocket, my: Int) {
        val out = s.outputStream
        val inp = s.inputStream
        out.write(SonyProtocol.init()); out.flush()
        var requested = false
        var shown = false
        while (my == session) {
            val f = SonyProtocol.readFrame(inp) ?: break
            if (f.type != SonyProtocol.TYPE_ACK && f.type != -1) {
                out.write(SonyProtocol.ack(f.seq)); out.flush()
            }
            if (!requested) {
                requested = true
                SonyProtocol.batteryRequests(1).forEach { out.write(it); out.flush(); Thread.sleep(150) }
            }
            if (SonyProtocol.parseBattery(f.payload, bat)) {
                ui.post {
                    if (!shown || popup == null) { showPopup(); shown = true }
                    refresh()
                }
            }
        }
    }

    // ---------- Popup kiểu HeyMelody ----------
    private var card: PopupCard? = null

    private fun showPopup() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        closePopup()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val c = PopupCard(this, "Sony WF-1000XM4")
        c.setOnClickListener { closePopup() }
        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels - 32 * d).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (28 * d).toInt() }
        wm.addView(c, lp)
        card = c; popup = c
        c.translationY = 400 * d; c.alpha = 0f
        c.animate().translationY(0f).alpha(1f).setDuration(380).setInterpolator(DecelerateInterpolator()).start()
        ui.postDelayed({ closePopup() }, 8000)
    }

    private fun refresh() { card?.update(bat) }

    private fun closePopup() {
        val v = popup ?: return
        popup = null; card = null
        v.animate().translationY(400f).alpha(0f).setDuration(250).withEndAction {
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
        }.start()
    }

    private fun closeAll() {
        runCatching { socket?.close() }
        socket = null
        ui.post { closePopup() }
    }

    override fun onDestroy() {
        session++
        runCatching { unregisterReceiver(receiver) }
        closeAll()
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}

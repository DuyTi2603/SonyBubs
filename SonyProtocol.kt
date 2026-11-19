package com.example.sonybuds

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID

/** Pin: -1 = chưa có dữ liệu */
data class Battery(
    var left: Int = -1, var leftCharging: Boolean = false,
    var right: Int = -1, var rightCharging: Boolean = false,
    var case: Int = -1, var caseCharging: Boolean = false,
)

/**
 * Giao thức riêng của Sony Headphones Connect qua Bluetooth Classic (RFCOMM).
 * Khung: 0x3E | type | seq | len(4 byte) | payload | checksum | 0x3C
 */
object SonyProtocol {
    val SONY_UUID: UUID = UUID.fromString("96CC203E-5068-46ad-B32D-E316F5E069BA")

    const val TYPE_ACK = 0x01
    const val TYPE_CMD1 = 0x0C
    const val TYPE_CMD2 = 0x0E

    private const val START = 0x3E
    private const val END = 0x3C
    private const val ESC = 0x3D

    // Loại pin
    private const val BAT_SINGLE = 0x00
    private const val BAT_DUAL = 0x09   // tai trái + phải
    private const val BAT_CASE = 0x0A   // hộp sạc

    fun build(type: Int, seq: Int, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(type); write(seq)
            write(ByteBuffer.allocate(4).putInt(payload.size).array())
            write(payload)
        }.toByteArray()
        val sum = (body.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        val out = ByteArrayOutputStream()
        out.write(START)
        for (b in body + sum) {
            val v = b.toInt() and 0xFF
            if (v == START || v == END || v == ESC) { out.write(ESC); out.write(v and 0xEF) }
            else out.write(v)
        }
        out.write(END)
        return out.toByteArray()
    }

    fun init() = build(TYPE_CMD1, 0, byteArrayOf(0x00, 0x00))

    fun ack(receivedSeq: Int) = build(TYPE_ACK, if (receivedSeq == 0) 1 else 0, ByteArray(0))

    /** Yêu cầu pin theo cả 2 đời giao thức (V1: 0x22, V2: 0x10) */
    fun batteryRequests(startSeq: Int): List<ByteArray> {
        var s = startSeq
        val list = mutableListOf<ByteArray>()
        for (t in intArrayOf(BAT_DUAL, BAT_CASE)) {
            list += build(TYPE_CMD2, s++ and 1, byteArrayOf(0x10, t.toByte()))
            list += build(TYPE_CMD1, s++ and 1, byteArrayOf(0x22, t.toByte()))
        }
        return list
    }

    class Frame(val type: Int, val seq: Int, val payload: ByteArray)

    /** Đọc 1 khung từ stream (blocking). Trả null nếu stream đóng. */
    fun readFrame(input: InputStream): Frame? {
        val buf = ByteArrayOutputStream()
        var started = false
        var escaped = false
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (!started) { if (b == START) started = true; continue }
            if (b == END) break
            if (escaped) { buf.write(b or 0x10); escaped = false }
            else if (b == ESC) escaped = true
            else buf.write(b)
        }
        val d = buf.toByteArray()
        if (d.size < 7) return Frame(-1, 0, ByteArray(0))
        return Frame(d[0].toInt() and 0xFF, d[1].toInt() and 0xFF, d.copyOfRange(6, d.size - 1))
    }

    /** Cập nhật pin nếu payload là reply/notify pin. Trả true nếu có thay đổi. */
    fun parseBattery(p: ByteArray, bat: Battery): Boolean {
        if (p.size < 4) return false
        val cmd = p[0].toInt() and 0xFF
        if (cmd != 0x11 && cmd != 0x13 && cmd != 0x23 && cmd != 0x25) return false
        fun lv(i: Int) = p[i].toInt() and 0xFF
        when (p[1].toInt()) {
            BAT_SINGLE -> { bat.left = lv(2); bat.leftCharging = lv(3) != 0 }
            BAT_DUAL -> if (p.size >= 6) {
                bat.left = lv(2); bat.leftCharging = lv(3) != 0
                bat.right = lv(4); bat.rightCharging = lv(5) != 0
            } else return false
            BAT_CASE -> { bat.case = lv(2); bat.caseCharging = lv(3) != 0 }
            else -> return false
        }
        return true
    }
}

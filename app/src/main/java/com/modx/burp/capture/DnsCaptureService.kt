package com.modx.burp.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in DNS metadata monitor. Only the synthetic DNS resolver route is intercepted. */
class DnsCaptureService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private val resolver = byteArrayOf(10, 111, 0, 2)
    private val upstream = InetAddress.getByName("1.1.1.1")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.get()) return START_NOT_STICKY
        val packages = intent?.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty()
        if (packages.isEmpty()) { stopSelf(startId); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification())
        try {
            val builder = Builder().setSession("MODx BURP DNS Monitor").setMtu(1500)
                .addAddress("10.111.0.1", 32).addDnsServer("10.111.0.2").addRoute("10.111.0.2", 32)
            packages.forEach { builder.addAllowedApplication(it) }
            tunnel = builder.establish() ?: error("VPN permission not granted")
            running.set(true)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply()
            worker = Thread({ packetLoop() }, "MODx-DNS-Capture").apply { start() }
        } catch (e: Exception) {
            append(JSONObject().put("type", "error").put("message", e.message ?: "VPN start failed"))
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun notification(): android.app.Notification {
        val id = "modx_dns_capture"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(id, "MODx BURP capture", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, id)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("MODx BURP DNS capture active")
            .setContentText("Selected apps • DNS metadata only")
            .setOngoing(true).build()
    }

    private fun packetLoop() {
        val descriptor = tunnel ?: return
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (running.get()) {
                val n = input.read(buffer)
                if (n > 0) processPacket(buffer, n, output)
            }
        } catch (_: Exception) {
            // Tunnel close while stopping is expected.
        } finally {
            try { input.close() } catch (_: Exception) {}
            try { output.close() } catch (_: Exception) {}
        }
    }

    private fun processPacket(p: ByteArray, size: Int, output: FileOutputStream) {
        if (size < 28 || (p[0].toInt() ushr 4 and 15) != 4) return
        val ihl = (p[0].toInt() and 15) * 4
        if (ihl < 20 || size < ihl + 8 || (p[9].toInt() and 255) != 17) return
        val frag = ((p[6].toInt() and 255) shl 8) or (p[7].toInt() and 255)
        if (frag and 0x1fff != 0 || !p.copyOfRange(16, 20).contentEquals(resolver)) return
        val srcPort = u16(p, ihl)
        if (u16(p, ihl + 2) != 53) return
        val udpLen = u16(p, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > size) return
        val dns = p.copyOfRange(ihl + 8, ihl + udpLen)
        val question = parseQuestion(dns) ?: return
        try {
            DatagramSocket().use { socket ->
                check(protect(socket)) { "Could not protect upstream DNS socket" }
                socket.soTimeout = 2500
                socket.send(DatagramPacket(dns, dns.size, upstream, 53))
                val bytes = ByteArray(4096)
                val reply = DatagramPacket(bytes, bytes.size)
                socket.receive(reply)
                val answer = reply.data.copyOf(reply.length)
                output.write(responsePacket(p.copyOfRange(12, 16), srcPort, answer))
                append(JSONObject().put("type", "dns")
                    .put("time", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date()))
                    .put("domain", question.first).put("recordType", question.second)
                    .put("responseCode", if (answer.size >= 4) answer[3].toInt() and 15 else -1)
                    .put("responseBytes", answer.size))
            }
        } catch (e: SocketTimeoutException) {
            append(JSONObject().put("type", "dns").put("domain", question.first).put("recordType", question.second).put("error", "DNS timeout"))
        } catch (e: Exception) {
            append(JSONObject().put("type", "dns").put("domain", question.first).put("recordType", question.second).put("error", e.message ?: "Forwarding error"))
        }
    }

    private fun parseQuestion(dns: ByteArray): Pair<String, Int>? {
        if (dns.size < 17) return null
        var i = 12
        val labels = ArrayList<String>()
        while (i < dns.size) {
            val n = dns[i++].toInt() and 255
            if (n == 0) break
            if (n > 63 || i + n > dns.size) return null
            labels.add(String(dns, i, n, Charsets.US_ASCII)); i += n
        }
        if (labels.isEmpty() || i + 4 > dns.size) return null
        return labels.joinToString(".") to u16(dns, i)
    }

    private fun responsePacket(clientIp: ByteArray, clientPort: Int, dns: ByteArray): ByteArray {
        val out = ByteArray(28 + dns.size)
        out[0] = 0x45; out[2] = (out.size ushr 8).toByte(); out[3] = out.size.toByte()
        out[8] = 64; out[9] = 17
        System.arraycopy(resolver, 0, out, 12, 4); System.arraycopy(clientIp, 0, out, 16, 4)
        val sum = checksum(out, 0, 20); out[10] = (sum ushr 8).toByte(); out[11] = sum.toByte()
        out[21] = 53; out[22] = (clientPort ushr 8).toByte(); out[23] = clientPort.toByte()
        val udpLen = 8 + dns.size; out[24] = (udpLen ushr 8).toByte(); out[25] = udpLen.toByte()
        System.arraycopy(dns, 0, out, 28, dns.size)
        return out
    }

    private fun checksum(b: ByteArray, start: Int, len: Int): Int {
        var sum = 0L; var i = start
        while (i < start + len) {
            val hi = (b[i].toInt() and 255) shl 8
            val lo = if (i + 1 < start + len) b[i + 1].toInt() and 255 else 0
            sum += (hi or lo).toLong(); i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 65535) + (sum shr 16)
        return sum.inv().toInt() and 65535
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)

    private fun append(item: JSONObject) {
        try {
            val file = File(filesDir, LOG_FILE)
            if (file.exists() && file.length() > MAX_LOG_BYTES) file.writeText(file.readLines().takeLast(400).joinToString("\n", postfix = "\n"))
            file.appendText(item.toString() + "\n")
        } catch (_: Exception) {}
    }

    override fun onRevoke() { stopSelf(); super.onRevoke() }
    override fun onDestroy() {
        running.set(false)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply()
        try { tunnel?.close() } catch (_: Exception) {}
        tunnel = null; worker?.interrupt(); worker = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PACKAGES = "capture_packages"
        const val PREFS = "modx_capture"
        const val KEY_RUNNING = "running"
        const val LOG_FILE = "dns_capture.jsonl"
        const val MAX_LOG_BYTES = 1024 * 1024
        const val NOTIFICATION_ID = 7412
    }
}

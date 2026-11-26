package com.perki.conect

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.*
import android.net.wifi.WifiManager
import android.os.Bundle
import android.telephony.*
import android.text.InputType
import android.view.View
import android.widget.*
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var mobile = false
    private lateinit var out: TextView
    private lateinit var ip: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private val blue = Color.parseColor("#185FA5")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun log(s: String) = runOnUiThread { out.append(s + "\n") }
    private fun f(x: Double?) = x?.let { "%.0f".format(it) } ?: "—"
    private fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { a() } }
    private fun field(h: String) = EditText(this).apply { hint = h; layoutDirection = View.LAYOUT_DIRECTION_LTR; setSingleLine() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE), 1)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        col.addView(TextView(this).apply { text = "perki conect"; textSize = 24f; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#0C447C")); setPadding(dp(16), dp(16), dp(16), dp(16)) })
        col.addView(Switch(this).apply { text = "فحص بيانات الهاتف بدل الواي فاي"; setPadding(0, dp(12), 0, dp(12)); setOnCheckedChangeListener { _, c -> mobile = c } })
        col.addView(btn("ابدأ الفحص") { run() })
        ip = field("عنوان الراوتر مثل 192.168.100.1"); user = field("اسم المستخدم")
        pass = field("كلمة المرور").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        ip.setText(getSharedPreferences("p", 0).getString("ip", gateway()))
        listOf(ip, user, pass).forEach { col.addView(it) }
        col.addView(btn("اختبر الاتصال بالراوتر") { testRouter() })
        out = TextView(this).apply { setTextIsSelectable(true); setPadding(0, dp(12), 0, 0); textSize = 15f }
        col.addView(out)
        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun gateway(): String = try {
        val d = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).dhcpInfo.gateway
        "%d.%d.%d.%d".format(d and 255, d shr 8 and 255, d shr 16 and 255, d shr 24 and 255)
    } catch (e: Exception) { "" }

    private fun testRouter() = thread {
        val h = ip.text.toString().trim()
        if (!Regex("""^(\d{1,3}\.){3}\d{1,3}$""").matches(h)) { log("العنوان غير صحيح"); return@thread }
        getSharedPreferences("p", 0).edit().putString("ip", h).apply()
        try {
            val c = URL("http://$h/").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000
            val body = c.inputStream.bufferedReader().readText()
            val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
            log("الراوتر استجاب: ${c.responseCode} · ${title ?: "بدون عنوان"} · ${c.getHeaderField("Server") ?: ""}")
        } catch (e: Exception) { log("تعذّر الوصول إلى $h. جرّب 192.168.1.1 أو 192.168.0.1 أو 192.168.100.1") }
    }

    private fun run() {
        out.text = ""
        thread {
            try {
                var net: Network? = null
                if (mobile) { net = mobileTest() ?: return@thread } else wifiTest()
                speed(net)
                log("انتهى الفحص")
            } catch (e: Exception) { log("خطأ: ${e.message}") }
        }
    }

    private fun ch(f: Int) = when { f == 2484 -> 14; f < 3000 -> (f - 2412) / 5 + 1; f < 5900 -> (f - 5000) / 5; else -> (f - 5950) / 5 }
    private fun width(c: Int) = when (c) { 0 -> 20; 1 -> 40; 2 -> 80; 3 -> 160; else -> 80 }

    private fun wifiTest() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ci = wm.connectionInfo
        val fr = ci.frequency
        log("الواي فاي: ${ci.ssid}\nالإشارة ${ci.rssi} dBm · سرعة الربط ${ci.linkSpeed} Mbps\nالتردد $fr MHz · قناة ${ch(fr)}")
        if (ci.rssi < -70) log("تحذير: الإشارة ضعيفة، اقترب من الراوتر")
        wm.startScan(); Thread.sleep(3500)
        val sr = wm.scanResults
        sr.firstOrNull { it.BSSID == ci.bssid }?.let {
            log("عرض القناة: ${width(it.channelWidth)} MHz")
            if (fr > 5000 && it.channelWidth == 0) log("اقتراح: فعّل عرض 80 MHz على 5 GHz")
        }
        val g = sr.filter { it.frequency < 3000 }.map { ch(it.frequency) }
        log("شبكات 2.4 GHz: ${g.size} · أنظف قناة: ${listOf(1, 6, 11).minByOrNull { c -> g.count { Math.abs(it - c) <= 4 } }}")
        val g5 = sr.filter { it.frequency in 5000..5900 }.map { ch(it.frequency) }
        log("شبكات 5 GHz: ${g5.size} · أنظف قناة: ${listOf(36, 40, 44, 48, 149, 153, 157, 161).minByOrNull { c -> g5.count { it == c } }}")
        val gw = gateway()
        if (gw.isNotEmpty()) log("كمون الراوتر ($gw): ${f(rtt(gw, 80, null, 10)?.first)} ms")
    }

    private fun mobileTest(): Network? {
        val tm = getSystemService(TelephonyManager::class.java)
        when (val c = tm.allCellInfo?.firstOrNull { it.isRegistered }) {
            is CellInfoLte -> log("LTE · RSRP ${c.cellSignalStrength.rsrp} dBm · SINR ${c.cellSignalStrength.rssnr}")
            is CellInfoNr -> log("5G · RSRP ${(c.cellSignalStrength as CellSignalStrengthNr).ssRsrp} dBm")
            else -> log("تعذّرت قراءة قوة الشبكة الخلوية")
        }
        val cm = getSystemService(ConnectivityManager::class.java)
        var n: Network? = null
        val l = CountDownLatch(1)
        cm.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(x: Network) { n = x; l.countDown() }
        })
        l.await(5, TimeUnit.SECONDS)
        if (n == null) log("تعذّر استعمال بيانات الهاتف، تأكد أنها مفعّلة")
        return n
    }

    private fun rtt(h: String, p: Int, net: Network?, n: Int): Pair<Double, Double>? {
        val t = ArrayList<Double>()
        repeat(n) {
            val s = net?.socketFactory?.createSocket() ?: Socket()
            val st = System.nanoTime()
            try { s.connect(InetSocketAddress(h, p), 1500); t.add((System.nanoTime() - st) / 1e6) }
            catch (e: ConnectException) { t.add((System.nanoTime() - st) / 1e6) }
            catch (e: Exception) { }
            finally { s.close() }
            Thread.sleep(100)
        }
        return if (t.isEmpty()) null else Pair(t.average(), t.max() - t.min())
    }

    private fun transfer(up: Boolean, net: Network?): Double {
        val u = URL(if (up) "https://speed.cloudflare.com/__up" else "https://speed.cloudflare.com/__down?bytes=100000000")
        val c = (net?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        c.connectTimeout = 5000
        val st = System.currentTimeMillis(); val end = st + 7000
        var bytes = 0L
        val buf = ByteArray(65536)
        try {
            if (up) {
                c.doOutput = true; c.requestMethod = "POST"; c.setChunkedStreamingMode(65536)
                c.outputStream.use { while (System.currentTimeMillis() < end) { it.write(buf); bytes += buf.size } }
            } else c.inputStream.use { while (System.currentTimeMillis() < end) { val r = it.read(buf); if (r < 0) break; bytes += r } }
        } finally { c.disconnect() }
        return bytes * 8 / 1e6 / ((System.currentTimeMillis() - st) / 1000.0)
    }

    private fun speed(net: Network?) {
        val idle = rtt("1.1.1.1", 443, net, 10)
        log("الكمون الخامل: ${f(idle?.first)} ms (تذبذب ${f(idle?.second)})")
        for (up in listOf(false, true)) {
            var loaded: Pair<Double, Double>? = null
            val th = thread { Thread.sleep(1500); loaded = rtt("1.1.1.1", 443, net, 12) }
            val mbps = try { transfer(up, net) } catch (e: Exception) { log("فشل اختبار ${if (up) "الرفع" else "التنزيل"}: ${e.message}"); 0.0 }
            th.join()
            log("${if (up) "الرفع" else "التنزيل"}: ${"%.1f".format(mbps)} Mbps · كمون تحت الحمل: ${f(loaded?.first)} ms")
            val l = loaded?.first; val i = idle?.first
            if (l != null && i != null && l > i * 4) log("تشخيص: bufferbloat مرتفع (×${"%.0f".format(l / i)}). فعّل QoS أو SQM في الراوتر وحدّد 85–90% من السرعة")
        }
    }
}

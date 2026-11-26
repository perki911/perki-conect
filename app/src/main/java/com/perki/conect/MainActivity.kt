package com.perki.conect

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.*
import android.net.wifi.WifiManager
import android.os.Bundle
import android.telephony.*
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var mobile = false
    private var busy = false
    private lateinit var gauge: GaugeView
    private lateinit var info: TextView
    private lateinit var phase: TextView
    private lateinit var netTv: TextView
    private lateinit var diag: TextView
    private lateinit var panel: LinearLayout
    private lateinit var mainBox: LinearLayout
    private lateinit var histBox: LinearLayout
    private lateinit var histBtn: TextView
    private lateinit var cDown: TextView
    private lateinit var cUp: TextView
    private lateinit var l0: TextView
    private lateinit var lj: TextView
    private lateinit var l1: TextView
    private lateinit var l2: TextView
    private lateinit var ip: EditText
    private lateinit var user: EditText
    private lateinit var pass: EditText
    private val blue = Color.parseColor("#185FA5")
    private val M = ViewGroup.LayoutParams.MATCH_PARENT
    private val W = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun log(s: String) = runOnUiThread { info.append(s + "\n") }
    private fun ui(a: () -> Unit) = runOnUiThread { a() }
    private fun f(x: Double?) = x?.let { "%.0f".format(it) } ?: "—"
    private fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setTextColor(Color.WHITE); setBackgroundColor(blue); setOnClickListener { a() } }
    private fun field(h: String) = EditText(this).apply { hint = h; layoutDirection = View.LAYOUT_DIRECTION_LTR; setSingleLine() }
    private fun card(label: String) = TextView(this).apply {
        tag = label; text = "$label\n—"; textSize = 15f
        setTextColor(Color.parseColor("#0C447C")); setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { setColor(Color.parseColor("#EEF4FB")); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.parseColor("#D3DDEA")) }
    }
    private fun upd(t: TextView, v: String) = runOnUiThread { t.text = "${t.tag}\n$v" }
    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, W, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE), 1)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.parseColor("#0C447C")); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        head.addView(TextView(this).apply { text = "perki conect"; textSize = 22f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, W, 1f))
        histBtn = TextView(this).apply { text = "النتائج السابقة"; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(8), 0, dp(12), 0); setOnClickListener { toggleHist() } }
        head.addView(histBtn)
        head.addView(TextView(this).apply { text = "⚙"; textSize = 26f; setTextColor(Color.WHITE); setOnClickListener { panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE } })

        mainBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        histBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        mainBox.addView(Switch(this).apply { text = "فرض بيانات الهاتف (حتى مع الواي فاي)"; setPadding(0, dp(10), 0, dp(10)); setOnCheckedChangeListener { _, c -> mobile = c; refreshNet() } })
        cDown = card("تنزيل"); cUp = card("رفع")
        mainBox.addView(row(cDown, cUp))
        netTv = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(blue); setPadding(0, dp(8), 0, 0) }
        phase = TextView(this).apply { text = "جاهز للفحص"; gravity = Gravity.CENTER }
        mainBox.addView(netTv); mainBox.addView(phase)
        gauge = GaugeView(this); mainBox.addView(gauge)
        mainBox.addView(btn("ابدأ الفحص") { run() })
        l0 = card("كمون خامل (ms)"); lj = card("تذبذب (ms)"); l1 = card("كمون تحت التنزيل (ms)"); l2 = card("كمون تحت الرفع (ms)")
        mainBox.addView(row(l0, lj)); mainBox.addView(row(l1, l2))
        diag = TextView(this).apply { visibility = View.GONE; setTextColor(Color.parseColor("#A32D2D")); setBackgroundColor(Color.parseColor("#FDECEA")); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        mainBox.addView(diag)
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        ip = field("عنوان الراوتر مثل 192.168.100.1"); user = field("اسم المستخدم")
        pass = field("كلمة المرور").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        ip.setText(getSharedPreferences("p", 0).getString("ip", gateway()))
        listOf(ip, user, pass).forEach { panel.addView(it) }
        panel.addView(btn("اختبر الاتصال بالراوتر") { testRouter() })
        mainBox.addView(panel)
        info = TextView(this).apply { setTextIsSelectable(true); setPadding(0, dp(12), 0, 0); textSize = 15f }
        mainBox.addView(info)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)) }
        col.addView(mainBox); col.addView(histBox)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(head, LinearLayout.LayoutParams(M, W))
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(M, 0, 1f))
        setContentView(root)
    }

    override fun onResume() { super.onResume(); refreshNet() }

    private fun netInfo(): Pair<Boolean, String> = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        val onWifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val m = mobile || !onWifi
        Pair(m, if (m) "بيانات الهاتف · " + getSystemService(TelephonyManager::class.java).networkOperatorName
            else "واي فاي · " + (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ssid.trim('"'))
    } catch (e: Exception) { Pair(mobile, "غير معروفة") }

    private fun refreshNet() { netTv.text = "الشبكة: " + netInfo().second }

    private fun toggleHist() {
        val show = histBox.visibility == View.GONE
        histBox.visibility = if (show) View.VISIBLE else View.GONE
        mainBox.visibility = if (show) View.GONE else View.VISIBLE
        histBtn.text = if (show) "رجوع" else "النتائج السابقة"
        if (show) showHistory()
    }

    private fun showHistory() {
        histBox.removeAllViews()
        val arr = JSONArray(getSharedPreferences("h", 0).getString("list", "[]"))
        if (arr.length() == 0) histBox.addView(TextView(this).apply { text = "لا توجد نتائج محفوظة بعد."; setPadding(0, dp(12), 0, dp(12)) })
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val base = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(o.getLong("t"))) + " · " + o.getString("net") +
                "\n↓ ${o.getString("down")}   ↑ ${o.getString("up")}\nكمون: خامل ${o.getString("idle")} · تنزيل ${o.getString("l1")} · رفع ${o.getString("l2")} ms" +
                (if (o.getString("warn").isNotEmpty()) "\n" + o.getString("warn") else "")
            val d = o.getString("info")
            var open = false
            val tv = card("").apply { text = base; textSize = 14f }
            tv.setOnClickListener { open = !open; tv.text = base + if (open && d.isNotEmpty()) "\n\n" + d else "" }
            histBox.addView(tv, LinearLayout.LayoutParams(M, W).apply { setMargins(0, dp(6), 0, dp(6)) })
        }
        if (arr.length() > 0) histBox.addView(btn("مسح السجل") { getSharedPreferences("h", 0).edit().remove("list").apply(); showHistory() })
    }

    private fun saveResult(net: String, down: String, up: String, idle: String, jit: String, a1: String, a2: String, warn: String, details: String) {
        val sp = getSharedPreferences("h", 0)
        val old = JSONArray(sp.getString("list", "[]"))
        val o = JSONObject().put("t", System.currentTimeMillis()).put("net", net).put("down", down).put("up", up)
            .put("idle", idle).put("jit", jit).put("l1", a1).put("l2", a2).put("warn", warn).put("info", details)
        val out = JSONArray().put(o)
        for (i in 0 until minOf(old.length(), 99)) out.put(old.get(i))
        sp.edit().putString("list", out.toString()).apply()
    }

    private fun gateway(): String = try {
        val d = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).dhcpInfo.gateway
        if (d == 0) "" else "%d.%d.%d.%d".format(d and 255, d shr 8 and 255, d shr 16 and 255, d shr 24 and 255)
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
        if (busy) return
        busy = true
        val (useMobile, netName) = netInfo()
        ui { info.text = ""; diag.visibility = View.GONE; gauge.value = 0.0; netTv.text = "الشبكة: $netName"
            listOf(cDown, cUp, l0, lj, l1, l2).forEach { it.text = "${it.tag}\n—" } }
        thread {
            var dS = "—"; var uS = "—"; var iS = "—"; var jS = "—"; var a1 = "—"; var a2 = "—"; var warn = ""
            try {
                var net: Network? = null
                if (useMobile) { ui { phase.text = "جارٍ فحص الشبكة الخلوية…" }; net = mobileTest() ?: return@thread }
                else { ui { phase.text = "جارٍ مسح الواي فاي…" }; wifiTest() }
                ui { phase.text = "جارٍ قياس الكمون…" }
                val idle = rtt("1.1.1.1", 443, net, 10)
                iS = f(idle?.first); jS = f(idle?.second)
                upd(l0, iS); upd(lj, jS)
                for (up in listOf(false, true)) {
                    ui { phase.text = if (up) "جارٍ اختبار الرفع…" else "جارٍ اختبار التنزيل…" }
                    var loaded: Pair<Double, Double>? = null
                    val th = thread { Thread.sleep(1500); loaded = rtt("1.1.1.1", 443, net, 12) }
                    val mbps = try { transfer(up, net) { v -> ui { gauge.value = v } } }
                        catch (e: Exception) { log("فشل اختبار ${if (up) "الرفع" else "التنزيل"}: ${e.message}"); 0.0 }
                    th.join()
                    ui { gauge.value = mbps }
                    val ms = "%.1f".format(mbps) + " Mbps"
                    val lat = f(loaded?.first)
                    if (up) { uS = ms; a2 = lat } else { dS = ms; a1 = lat }
                    upd(if (up) cUp else cDown, ms)
                    upd(if (up) l2 else l1, lat)
                    val l = loaded?.first; val i = idle?.first
                    if (l != null && i != null && l > i * 3 && warn.isEmpty())
                        warn = "Bufferbloat مرتفع: الكمون ×${"%.0f".format(l / i)} تحت حمل ${if (up) "الرفع" else "التنزيل"}. فعّل QoS أو SQM في الراوتر وحدّد 85–90% من السرعة."
                    if (!up) { Thread.sleep(800); ui { gauge.value = 0.0 } }
                }
                if (warn.isNotEmpty()) ui { diag.text = warn; diag.visibility = View.VISIBLE }
                ui { phase.text = "اكتمل الفحص"; saveResult(netName, dS, uS, iS, jS, a1, a2, warn, info.text.toString()) }
            } catch (e: Exception) { log("خطأ: ${e.message}") }
            finally { busy = false }
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
            is CellInfoLte -> log("LTE · RSRP ${c.cellSignalStrength.rsrp} dBm · SINR ${c.cellSignalStrength.rssnr.let { if (it == Int.MAX_VALUE || it == 0) "غير متاح" else it.toString() }}")
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
        if (t.isEmpty()) return null
        val j = if (t.size < 2) 0.0 else (1 until t.size).map { Math.abs(t[it] - t[it - 1]) }.average()
        return Pair(t.average(), j)
    }

    private fun transfer(up: Boolean, net: Network?, live: (Double) -> Unit): Double {
        val total = AtomicLong(0)
        var err = ""
        val st = System.currentTimeMillis(); val end = st + 7000
        val ws = (1..(if (up) 2 else 4)).map { thread {
            val buf = ByteArray(65536)
            try {
                while (System.currentTimeMillis() < end) {
                    val u = URL(if (up) "https://speed.cloudflare.com/__up" else "https://speed.cloudflare.com/__down?bytes=10000000")
                    val c = (net?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
                    c.connectTimeout = 5000
                    try {
                        if (up) {
                            c.doOutput = true; c.requestMethod = "POST"; c.setChunkedStreamingMode(65536)
                            c.outputStream.use { o -> while (System.currentTimeMillis() < end) { o.write(buf); total.addAndGet(buf.size.toLong()) } }
                            c.responseCode
                        } else {
                            if (c.responseCode != 200) { err = "HTTP ${c.responseCode}"; return@thread }
                            c.inputStream.use { i -> while (System.currentTimeMillis() < end) { val r = i.read(buf); if (r < 0) break; total.addAndGet(r.toLong()) } }
                        }
                    } finally { c.disconnect() }
                }
            } catch (e: Exception) { err = e.javaClass.simpleName + ": " + e.message }
        } }
        var last = 0L; var s = 0.0
        while (System.currentTimeMillis() < end && ws.any { it.isAlive }) {
            Thread.sleep(200)
            val t = total.get()
            s = 0.6 * s + 0.4 * ((t - last) * 8 / 1e6 / 0.2); last = t; live(s)
        }
        ws.forEach { it.join() }
        if (total.get() == 0L) throw Exception(err)
        return total.get() * 8 / 1e6 / ((System.currentTimeMillis() - st) / 1000.0)
    }
}

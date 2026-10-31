package app.helio.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.helio.MainActivity
import app.helio.R
import app.helio.data.LinkStatus
import app.helio.data.Repository
import app.helio.logic.Stress
import app.helio.widget.WidgetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Keeps the standard Bluetooth heart-rate link to the Helio Strap open in the background
 * (service 0x180D, characteristic 0x2A37), computes live stress every 5 s, refreshes the widget
 * every 30 s and sends a notification when stress stays high.
 */
@SuppressLint("MissingPermission")
class LiveHeartRateService : Service() {

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val CH_LIVE = "live"
        private const val CH_ALERT = "alerts"
        private const val NOTIF_LIVE = 1
        private const val NOTIF_ALERT = 2
        private const val PREFS = "helio_ble"
        private const val ACTION_STOP = "app.helio.STOP"

        private const val WINDOW_MS = 10 * 60_000L
        private const val STRESS_EVERY_MS = 5_000L
        private const val WIDGET_EVERY_MS = 30_000L
        private const val HIGH_STRESS = 2.5
        private const val HIGH_FOR_MS = 2 * 60_000L
        private const val ALERT_COOLDOWN_MS = 30 * 60_000L

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, LiveHeartRateService::class.java))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, LiveHeartRateService::class.java).setAction(ACTION_STOP))

        fun hasPermissions(ctx: Context): Boolean =
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }

        fun rememberedDevice(ctx: Context): String? =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("address", null)

        /** Heart Rate Measurement: flags byte, then HR as uint8 or uint16 (flag bit 0). */
        fun parseHr(v: ByteArray): Int? {
            if (v.size < 2) return null
            val flags = v[0].toInt() and 0xFF
            return if (flags and 0x01 != 0) {
                if (v.size < 3) null else (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8)
            } else v[1].toInt() and 0xFF
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var loopJob: Job? = null
    private var strapJob: Job? = null
    private var retry = 0
    private val samples = ArrayDeque<Pair<Long, Int>>()
    private val stressHist = ArrayDeque<Pair<Long, Double>>()
    private var highSince: Long? = null
    private var lastAlert = 0L
    private var lastWidget = 0L
    private var stopping = false

    private val adapter get() = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        createChannels()
        val n = liveNotification("Connecting to Helio…")
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_LIVE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_LIVE, n)
        if (!hasPermissions(this)) { shutdown(); return START_NOT_STICKY }
        if (app.helio.strap.StrapManager.pairing(this) != null) {
            // Paired directly: one authenticated link carries live HR and the history sync.
            if (strapJob == null) strapJob = scope.launch {
                app.helio.strap.StrapManager.run(
                    this@LiveHeartRateService,
                    onStatus = { s, name -> setStatus(s, name) },
                    onHeartRate = { bpm -> onBpm(bpm) },
                    afterSync = {
                        app.helio.data.Workouts.fillFromStrap(applicationContext)
                        Repository.refresh(applicationContext)
                        notify(NOTIF_LIVE, liveNotification(lastLiveText))
                    },
                )
            }
        } else if (gatt == null && !scanning) connectOrScan()
        if (loopJob == null) loopJob = scope.launch { stressLoop() }
        return START_STICKY
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private fun connectOrScan() {
        val addr = rememberedDevice(this)
        if (addr != null) {
            setStatus(LinkStatus.Connecting)
            connect(adapter.getRemoteDevice(addr))
        } else scan()
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: ""
            // The Helio advertises the Heart Rate service; prefer Amazfit devices if several are around.
            if (name.contains("Helio", true) || name.contains("Amazfit", true) || name.isNotEmpty()) {
                stopScan()
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("address", result.device.address).putString("name", name).apply()
                setStatus(LinkStatus.Connecting, name)
                connect(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) { scanning = false; setStatus(LinkStatus.Lost) }
    }

    private fun scan() {
        val scanner = adapter?.bluetoothLeScanner ?: return setStatus(LinkStatus.Lost)
        setStatus(LinkStatus.Scanning)
        scanning = true
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCb,
        )
        scope.launch { delay(20_000); if (scanning) { stopScan(); setStatus(LinkStatus.Lost) } }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) { }
    }

    private fun connect(device: BluetoothDevice) {
        gatt?.close()
        gatt = device.connectGatt(this, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                retry = 0
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close()
                if (gatt == g) gatt = null
                if (stopping) return
                setStatus(LinkStatus.Lost)
                // Reconnect with backoff: 2 s, 4 s, 8 s … capped at 60 s
                val wait = (2000L shl retry.coerceAtMost(5)).coerceAtMost(60_000L)
                retry++
                scope.launch { delay(wait); if (!stopping && gatt == null) connectOrScan() }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
            g.setCharacteristicNotification(ch, true)
            val d = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(d)
            }
            val name = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("name", null) ?: g.device.name
            Repository.live.value = Repository.live.value.copy(sessionStart = System.currentTimeMillis(), stressAtStart = null)
            setStatus(LinkStatus.Live, name)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            onBeat(value)
        }

        @Deprecated("Android 12 path")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) ch.value?.let { onBeat(it) }
        }
    }

    private fun onBeat(value: ByteArray) { parseHr(value)?.let { onBpm(it) } }

    private fun onBpm(hr: Int) {
        if (hr !in 25..230) return
        val now = System.currentTimeMillis()
        synchronized(samples) {
            samples.addLast(now to hr)
            while (samples.isNotEmpty() && now - samples.first().first > WINDOW_MS) samples.removeFirst()
        }
        Repository.live.value = Repository.live.value.copy(hr = hr, hrHistory = synchronized(samples) { samples.toList() })
        app.helio.data.Workouts.onHr(now, hr)
    }

    // ── Live stress, widget, alerts ───────────────────────────────────────────

    private suspend fun stressLoop() {
        while (scope.isActive) {
            delay(STRESS_EVERY_MS)
            val now = System.currentTimeMillis()
            val lastMin = synchronized(samples) { samples.filter { now - it.first <= 60_000 } }
            if (lastMin.size < 5) continue
            val state = Repository.live.value
            val stress = Stress.fromHr(lastMin.map { it.second }.average(), state.restingBaseline)
            stressHist.addLast(now to stress)
            while (stressHist.isNotEmpty() && now - stressHist.first().first > WINDOW_MS) stressHist.removeFirst()
            Repository.live.value = state.copy(
                stress = stress,
                stressHistory = stressHist.toList(),
                stressAtStart = state.stressAtStart ?: stress,
            )

            if (now - lastWidget >= WIDGET_EVERY_MS) {
                lastWidget = now
                WidgetStore.saveLive(this, state.hr, stress, true)
                WidgetStore.refreshWidgets(this)
                lastLiveText = "${state.hr ?: "—"} bpm · stress ${String.format("%.1f", stress)} ${Stress.label(stress).lowercase()}"
                notify(NOTIF_LIVE, liveNotification(lastLiveText))
            }

            if (stress >= HIGH_STRESS) {
                if (highSince == null) highSince = now
                if (now - highSince!! >= HIGH_FOR_MS && now - lastAlert >= ALERT_COOLDOWN_MS) {
                    lastAlert = now
                    notify(NOTIF_ALERT, alertNotification(stress))
                }
            } else highSince = null
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private var lastLiveText = "Connected"

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Live heart rate", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Stress alerts", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The ongoing notification doubles as the lock-screen view: the title carries recovery, strain
     * and sleep, and the card draws the widget's week and strain (one line collapsed, the full
     * widget expanded). Public, so the lock screen shows it.
     */
    private fun liveNotification(text: String): Notification {
        val s = WidgetStore.read(this)
        val title = listOfNotNull(
            s.recovery?.let { "Recovery $it%" }, s.strain?.let { "Strain ${String.format("%.1f", it)}" }, s.sleepScore?.let { "Sleep $it" },
        ).joinToString(" · ").ifEmpty { "Helio" }
        val d = resources.displayMetrics.density
        val b = NotificationCompat.Builder(this, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        try {
            val small = android.widget.RemoteViews(packageName, R.layout.notif_image).apply {
                setImageViewBitmap(R.id.notif_image, app.helio.widget.WidgetRender.strip(this@LiveHeartRateService, s, d))
            }
            val big = android.widget.RemoteViews(packageName, R.layout.notif_image).apply {
                setImageViewBitmap(R.id.notif_image, app.helio.widget.WidgetRender.render(this@LiveHeartRateService, s, 340f, 152f, d, card = true))
            }
            b.setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(small).setCustomBigContentView(big)
        } catch (e: Exception) { }
        return b
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .addAction(0, "Stop", PendingIntent.getService(this, 1, Intent(this, LiveHeartRateService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()
    }

    private fun alertNotification(stress: Double): Notification = NotificationCompat.Builder(this, CH_ALERT)
        .setSmallIcon(R.drawable.ic_stat_heart)
        .setContentTitle("Stress is high (${String.format("%.1f", stress)})")
        .setContentText("You've been still with a raised heart rate for 2 minutes. Take a minute to breathe.")
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()

    private fun notify(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun setStatus(s: LinkStatus, name: String? = null) {
        Repository.live.value = Repository.live.value.copy(status = s, deviceName = name ?: Repository.live.value.deviceName)
    }

    private fun shutdown() {
        stopping = true
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        loopJob?.cancel()
        loopJob = null
        strapJob?.cancel()
        strapJob = null
        Repository.live.value = Repository.live.value.copy(status = LinkStatus.Off, hr = null)
        WidgetStore.saveLive(this, null, null, false)
        scope.launch { WidgetStore.refreshWidgets(this@LiveHeartRateService) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopping = true
        stopScan()
        gatt?.close()
        scope.cancel()
        super.onDestroy()
    }
}

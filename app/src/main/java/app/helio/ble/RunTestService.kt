package app.helio.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.helio.MainActivity
import app.helio.R
import app.helio.data.Repository
import app.helio.logic.RunTest
import app.helio.logic.Vo2Max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** State of the guided test, read by the test screen. */
data class TestState(
    val running: Boolean = false,
    val startMs: Long = 0,
    val elapsedS: Int = 0,
    val phase: Int = 0,
    val distanceM: Double = 0.0,
    val speedMps: Double? = null,
    val gpsAccuracy: Float? = null,
    val hr: Int? = null,
)

/**
 * Records the 12-minute guided test with the screen off: GPS every second (foreground location
 * service), heart rate from the live Helio link, spoken and vibration cues at each pace change.
 */
@SuppressLint("MissingPermission")
class RunTestService : Service(), LocationListener {
    companion object {
        val state = MutableStateFlow(TestState())
        private const val CH = "test"
        private const val ID = 3
        private const val ACTION_CANCEL = "app.helio.CANCEL_TEST"

        fun hasPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, RunTestService::class.java))
        fun cancel(ctx: Context) = ctx.startService(Intent(ctx, RunTestService::class.java).setAction(ACTION_CANCEL))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fixes = mutableListOf<RunTest.Fix>()
    private val hr = mutableListOf<Pair<Long, Int>>()
    private var tts: TextToSpeech? = null
    private var startMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { finish(save = false); return START_NOT_STICKY }
        if (state.value.running) return START_NOT_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "VO2 test", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CH).setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("VO2 test running").setContentText("Keep the phone with you. Cues will tell you when to change pace.")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(ID, n)

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.ENGLISH }
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, mainLooper)
        startMs = System.currentTimeMillis()
        state.value = TestState(running = true, startMs = startMs)
        scope.launch { loop() }
        return START_NOT_STICKY
    }

    private suspend fun loop() {
        var lastPhase = -1
        delay(1500)
        while (true) {
            val el = ((System.currentTimeMillis() - startMs) / 1000).toInt()
            Repository.live.value.hr?.let { synchronized(hr) { hr += System.currentTimeMillis() to it } }
            val ph = RunTest.phases.indexOfLast { el >= it.startS }.coerceAtLeast(0)
            if (ph != lastPhase) { cue(RunTest.phases[ph].cue); lastPhase = ph }
            val left = RunTest.TOTAL_S - el
            if (left == 60) say("One minute left.")
            state.value = state.value.copy(elapsedS = el, phase = ph, hr = Repository.live.value.hr)
            if (el >= RunTest.TOTAL_S) { cue("Test complete. Well done. You can walk now."); finish(save = true); return }
            delay(1000)
        }
    }

    override fun onLocationChanged(l: Location) {
        val f = RunTest.Fix(System.currentTimeMillis(), l.latitude, l.longitude, l.altitude, l.accuracy)
        var added = 0.0
        synchronized(fixes) {
            fixes.lastOrNull()?.let { p -> if (p.acc <= 20 && f.acc <= 20) added = Location("").apply { latitude = p.lat; longitude = p.lon }.distanceTo(l).toDouble() }
            fixes += f
        }
        state.value = state.value.copy(distanceM = state.value.distanceM + added, speedMps = if (l.hasSpeed()) l.speed.toDouble() else null, gpsAccuracy = l.accuracy)
    }

    private fun say(text: String) { tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text) }

    private fun cue(text: String) {
        say(text)
        try {
            val v = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
        } catch (_: Exception) { }
    }

    private fun finish(save: Boolean) {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        if (save) {
            val days = Repository.days.value
            val today = LocalDate.now()
            val hrMax = Vo2Max.hrMax(Repository.profile.value, days, today).first
            val result = RunTest.analyse(startMs, synchronized(fixes) { fixes.toList() }, synchronized(hr) { hr.toList() }, hrMax, today.toEpochDay())
            Repository.addTest(this, result)
        }
        state.value = state.value.copy(running = false)
        scope.launch { delay(4000); tts?.shutdown() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this) } catch (_: Exception) { }
        scope.cancel()
        super.onDestroy()
    }
}

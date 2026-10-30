package app.helio.strap

import android.content.Context
import android.util.Log
import app.helio.data.LinkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import strap.protocol.auth.HandshakeFailure
import strap.protocol.crypto.HuamiCrypto
import strap.protocol.session.StrapException
import strap.protocol.session.StrapFailure
import strap.protocol.session.StrapSession
import strap.protocol.sync.StrapSync
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The ONE owner of the strap connection when the Helio is paired directly (MAC + auth key).
 * Holds a single authenticated link open: live heart rate streams on it every second, and the
 * strap's stored history (HR, HRV, stress, skin temperature, SpO2, breathing, sleep stages,
 * workouts, PAI…) is pulled on the same link every [SYNC_EVERY], or at once on [syncNow].
 */
object StrapManager {
    data class Pairing(val mac: String, val key: ByteArray)

    data class Status(
        val lastSync: Instant? = null,
        val syncing: Boolean = false,
        val progress: String? = null,
        val battery: Int? = null,
        val error: String? = null,
    )

    private const val PREFS = "helio_strap"
    private const val TAG = "helio-strap"
    val SYNC_EVERY = 10.minutes

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile private var store: LocalStore? = null
    fun store(ctx: Context): LocalStore = store ?: synchronized(this) { store ?: LocalStore(ctx.applicationContext).also { store = it } }

    fun pairing(ctx: Context): Pairing? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mac = p.getString("mac", null) ?: return null
        val key = p.getString("key", null) ?: return null
        return runCatching { Pairing(mac, HuamiCrypto.parseAuthKey(key)) }.getOrNull()
    }

    fun pairingText(ctx: Context): Pair<String, String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (p.getString("mac", "") ?: "") to (p.getString("key", "") ?: "")
    }

    /** Saves the strap's MAC and auth key; returns an error sentence, or null when they look right. */
    fun savePairing(ctx: Context, mac: String, key: String): String? {
        val m = mac.trim().uppercase()
        if (m.isEmpty() && key.isBlank()) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            return null
        }
        if (!Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}").matches(m)) return "The MAC should look like C4:A1:23:45:67:89."
        if (runCatching { HuamiCrypto.parseAuthKey(key.trim()) }.isFailure) return "The auth key should be 32 hex characters (it may start with 0x)."
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mac", m).putString("key", key.trim()).apply()
        return null
    }

    fun syncNow() { trigger.tryEmit(Unit) }

    /**
     * Connects, authenticates and keeps the link until cancelled: syncs at once, then every
     * [SYNC_EVERY] (or on [syncNow]); [onHeartRate] gets each live reading. Reconnects with
     * backoff when the link drops. Never throws except for cancellation.
     */
    suspend fun run(ctx: Context, onStatus: (LinkStatus, String?) -> Unit, onHeartRate: (Int) -> Unit, afterSync: suspend () -> Unit) {
        var retry = 0
        while (true) {
            val pairing = pairing(ctx) ?: return
            onStatus(LinkStatus.Connecting, null)
            val link = try {
                HelioStrapLink.connect(ctx, pairing.mac, ::log)
            } catch (e: IOException) {
                fail("Could not reach the Helio: ${e.message}")
                onStatus(LinkStatus.Lost, null)
                delay(backoff(retry++)); continue
            }
            try {
                coroutineScope {
                    val session = StrapSession.open(link, pairing.key, this, log = ::log)
                    link.onHeartRate = onHeartRate
                    val hr = link.enableHeartRate()
                    onStatus(LinkStatus.Live, "Helio Strap")
                    if (!hr) log("no standard heart-rate broadcast on this strap; history only")
                    retry = 0
                    try {
                        var busy = 0
                        while (true) {
                            val ok = sync(ctx, session, link, quiet = busy < 2)
                            afterSync()
                            // The strap sometimes answers "busy" to a history request: try again in 45 s (twice), then wait as usual.
                            busy = if (ok) 0 else busy + 1
                            withTimeoutOrNull(if (busy in 1..2) 45_000L else SYNC_EVERY) { trigger.first() }
                            if (link.disconnected) throw IOException("link dropped")
                        }
                    } finally {
                        session.close()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StrapException) {
                val f = e.failure
                fail(
                    if (f is StrapFailure.HandshakeRefused && f.reason == HandshakeFailure.WrongAuthKey)
                        "The Helio rejected the auth key. It changes when the strap is paired again in Zepp: fetch the new key."
                    else "The Helio refused the connection: $f",
                )
                onStatus(LinkStatus.Lost, null)
            } catch (e: Exception) {
                fail("Connection dropped: ${e.message}")
                onStatus(LinkStatus.Lost, null)
            } finally {
                link.onHeartRate = null
                link.close()
            }
            delay(backoff(retry++))
        }
    }

    /** One history sync; true when everything came through. [quiet]: a retry, so a failure isn't shown yet. */
    private suspend fun sync(ctx: Context, session: StrapSession, link: HelioStrapLink, quiet: Boolean = false): Boolean {
        val store = store(ctx)
        _status.value = _status.value.copy(syncing = true, progress = "Starting", error = null)
        session.requestServices()
        val result = StrapSync(session) { _status.value = _status.value.copy(progress = it.label) }.run(store.window())
        val battery = link.batteryPercent()
        store.save(result, battery)
        log("sync: ${result.samples.size} samples, ${result.sleepSessions.size} nights, ${result.workouts.size} workouts, failure=${result.failure}")
        val f = result.failure
        _status.value = Status(
            lastSync = if (f == null) Instant.now() else _status.value.lastSync ?: Instant.now(),
            syncing = false, progress = null, battery = battery ?: _status.value.battery,
            error = when {
                f == null || quiet -> null
                f is strap.protocol.fetch.FetchFailure.StartRejected -> "The strap was busy, so part of the history waits for the next sync (what arrived was saved)."
                else -> "Sync stopped early: $f (what arrived was saved)"
            },
        )
        return f == null
    }

    private fun fail(msg: String) {
        log(msg)
        _status.value = _status.value.copy(syncing = false, progress = null, error = msg)
    }

    private fun backoff(n: Int) = (2000L shl n.coerceAtMost(5)).coerceAtMost(60_000L)

    private fun log(line: String) { Log.i(TAG, line) }
}

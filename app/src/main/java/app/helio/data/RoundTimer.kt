package app.helio.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Boxing-style round timer: get ready → work → rest → work … Bell at each round start, a double
 * "clapper" 10 s before the round ends, a long tone at the end, and a vibration with each cue.
 * Every finished work round is handed to [Workouts.addRound] so the activity detail can break the
 * session down round by round. Runs independently of the screen, so folding or unfolding never stops it.
 */
object RoundTimer {
    data class Config(val rounds: Int = 5, val workS: Int = 180, val restS: Int = 60, val prepS: Int = 10)

    enum class Phase { Idle, Prep, Work, Rest, Done }

    data class State(
        val phase: Phase = Phase.Idle,
        /** 1-based round being worked or rested after. */
        val round: Int = 0,
        /** When the current phase ends (epoch millis), while running. */
        val endsAt: Long = 0,
        /** Time left when paused; null while running. */
        val pausedLeftMs: Long? = null,
        val phaseLengthMs: Long = 0,
    ) {
        val paused get() = pausedLeftMs != null
        val running get() = phase == Phase.Prep || phase == Phase.Work || phase == Phase.Rest
        fun leftMs(now: Long) = pausedLeftMs ?: (endsAt - now).coerceAtLeast(0)
    }

    val presets = listOf(
        "Fight" to Config(5, 180, 60),
        "Amateur" to Config(3, 120, 60),
        "Pads" to Config(6, 180, 60),
        "Bag sprints" to Config(8, 60, 30),
    )

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var app: Context? = null
    private var workStart = 0L
    private var warned = false

    fun load(ctx: Context) {
        app = ctx.applicationContext
        val p = ctx.getSharedPreferences("helio_rounds", Context.MODE_PRIVATE)
        _config.value = Config(p.getInt("rounds", 5), p.getInt("work", 180), p.getInt("rest", 60))
    }

    fun setConfig(ctx: Context, c: Config) {
        val fixed = c.copy(rounds = c.rounds.coerceIn(1, 30), workS = c.workS.coerceIn(15, 900), restS = c.restS.coerceIn(0, 300))
        _config.value = fixed
        ctx.getSharedPreferences("helio_rounds", Context.MODE_PRIVATE).edit()
            .putInt("rounds", fixed.rounds).putInt("work", fixed.workS).putInt("rest", fixed.restS).apply()
    }

    fun start(ctx: Context) {
        app = ctx.applicationContext
        enter(Phase.Prep, 0, _config.value.prepS * 1000L)
        cue(Cue.Tick)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(100)
                tick()
            }
        }
    }

    fun pause() {
        val s = _state.value
        if (!s.running || s.paused) return
        _state.value = s.copy(pausedLeftMs = s.leftMs(System.currentTimeMillis()))
    }

    fun resume() {
        val s = _state.value
        val left = s.pausedLeftMs ?: return
        _state.value = s.copy(endsAt = System.currentTimeMillis() + left, pausedLeftMs = null)
    }

    /** Jump to the next phase now (ends the round early, or cuts the rest short). */
    fun skip() {
        val s = _state.value
        if (!s.running) return
        _state.value = s.copy(endsAt = System.currentTimeMillis(), pausedLeftMs = null)
        tick()
    }

    /** Saves a round that is in progress (if it ran 30 s or more), e.g. when the activity ends mid-round. */
    fun closeOpenRound() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (s.phase == Phase.Work && now - workStart >= 30_000) Workouts.addRound(workStart, now)
    }

    fun reset() {
        ticker?.cancel(); ticker = null
        _state.value = State()
    }

    private fun enter(phase: Phase, round: Int, lengthMs: Long) {
        warned = false
        _state.value = State(phase, round, System.currentTimeMillis() + lengthMs, null, lengthMs)
    }

    private fun tick() {
        val s = _state.value
        if (!s.running || s.paused) return
        val now = System.currentTimeMillis()
        val left = s.endsAt - now
        val c = _config.value
        if (left > 0) {
            if (!warned && left <= 10_000 && s.phaseLengthMs > 20_000 && (s.phase == Phase.Work || s.phase == Phase.Rest)) {
                warned = true
                cue(if (s.phase == Phase.Work) Cue.Clapper else Cue.Tick)
            }
            return
        }
        when (s.phase) {
            Phase.Prep -> startWork(1)
            Phase.Work -> {
                Workouts.addRound(workStart, now)
                if (s.round >= c.rounds) {
                    _state.value = State(Phase.Done, s.round)
                    ticker?.cancel(); ticker = null
                    cue(Cue.Finish)
                } else if (c.restS == 0) startWork(s.round + 1)
                else {
                    enter(Phase.Rest, s.round, c.restS * 1000L)
                    cue(Cue.End)
                }
            }
            Phase.Rest -> startWork(s.round + 1)
            else -> Unit
        }
    }

    private fun startWork(round: Int) {
        workStart = System.currentTimeMillis()
        enter(Phase.Work, round, _config.value.workS * 1000L)
        cue(Cue.Bell)
    }

    private enum class Cue { Bell, Clapper, End, Finish, Tick }

    private val tones by lazy { runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull() }

    private fun cue(c: Cue) {
        val ctx = app ?: return
        val vib = runCatching { (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator }.getOrNull()
        val pattern = when (c) {
            Cue.Bell -> longArrayOf(0, 180, 120, 180, 120, 180)
            Cue.Clapper -> longArrayOf(0, 90, 90, 90)
            Cue.End -> longArrayOf(0, 600)
            Cue.Finish -> longArrayOf(0, 300, 150, 300, 150, 700)
            Cue.Tick -> longArrayOf(0, 60)
        }
        runCatching { vib?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
        scope.launch {
            val tg = tones ?: return@launch
            when (c) {
                Cue.Bell -> repeat(3) { tg.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, 160); delay(260) }
                Cue.Clapper -> repeat(2) { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80); delay(160) }
                Cue.End -> tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 700)
                Cue.Finish -> repeat(3) { tg.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 400); delay(550) }
                Cue.Tick -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            }
        }
    }
}

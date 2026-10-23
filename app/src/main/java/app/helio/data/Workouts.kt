package app.helio.data

import android.content.Context
import app.helio.logic.Strain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class WorkoutType(val label: String) { MuayThai("Muay Thai"), Running("Run"), Tennis("Tennis"), CrossFit("Cross"), Other("Workout") }

/** What a session was, asked with one tap when it ends, so progress compares like with like. */
enum class SessionKind(val label: String, val sport: WorkoutType) {
    Pads("Pads / bag", WorkoutType.MuayThai), Sparring("Sparring", WorkoutType.MuayThai), Both("Both", WorkoutType.MuayThai),
    Match("Match", WorkoutType.Tennis), Practice("Practice", WorkoutType.Tennis), Lesson("Lesson", WorkoutType.Tennis);

    companion object {
        fun forSport(t: WorkoutType) = entries.filter { it.sport == t }
    }
}

/** An activity recorded in the app from the Helio's live heart rate. */
data class Workout(
    val start: Long,
    val end: Long,
    val type: WorkoutType,
    /** Heart rate every ~5 s: (epoch millis, bpm). */
    val hr: List<Pair<Long, Int>>,
    /** Work rounds from the round timer: (start, end) epoch millis. */
    val rounds: List<Pair<Long, Long>> = emptyList(),
    val tag: String? = null,
)

data class WorkoutStats(
    val durationS: Long,
    val avgHr: Int,
    val maxHr: Int,
    val strain: Double,
    val calories: Int,
    /** Lower bpm bound of zones 1..5 (zone 0 is below zone 1). */
    val zoneFloors: List<Int>,
    /** Seconds in zones 0..5. */
    val zoneSeconds: List<Long>,
)

object WorkoutMath {
    /** Zones as % of max heart rate: Z1 50 %, Z2 60 %, Z3 70 %, Z4 80 %, Z5 90 %. */
    fun floors(hrMax: Double) = listOf(0.5, 0.6, 0.7, 0.8, 0.9).map { (it * hrMax).toInt() }

    /**
     * Calories from heart rate (Keytel et al. 2005), per minute:
     * men (−55.0969 + 0.6309·HR + 0.1988·kg + 0.2017·age) / 4.184,
     * women (−20.4022 + 0.4472·HR − 0.1263·kg + 0.074·age) / 4.184.
     */
    fun kcalPerMin(hr: Double, p: Profile): Double {
        val kg = p.weightKg ?: 75.0
        val age = (p.age ?: 40).toDouble()
        val v = if (p.male != false) (-55.0969 + 0.6309 * hr + 0.1988 * kg + 0.2017 * age) / 4.184
        else (-20.4022 + 0.4472 * hr - 0.1263 * kg + 0.074 * age) / 4.184
        return v.coerceAtLeast(1.0)
    }

    fun stats(w: Workout, hrMax: Double, rest: Double, p: Profile): WorkoutStats {
        val f = floors(hrMax)
        val zones = LongArray(6)
        var kcal = 0.0
        for (i in 1 until w.hr.size) {
            val dt = ((w.hr[i].first - w.hr[i - 1].first) / 1000).coerceIn(0, 60)
            val bpm = w.hr[i].second
            zones[f.count { bpm >= it }] += dt
            kcal += kcalPerMin(bpm.toDouble(), p) * dt / 60.0
        }
        return WorkoutStats(
            durationS = (w.end - w.start) / 1000,
            avgHr = if (w.hr.isEmpty()) 0 else w.hr.map { it.second }.average().toInt(),
            maxHr = w.hr.maxOfOrNull { it.second } ?: 0,
            strain = Strain.fromHeartRate(w.hr, rest, hrMax),
            calories = kcal.toInt(),
            zoneFloors = f,
            zoneSeconds = zones.toList(),
        )
    }
}

/** Any activity (from Zepp / Health Connect, or sample) as a Workout, so it opens the same detail view. */
fun Activity.toWorkout(): Workout = Workout(
    startMs, endMs,
    when {
        name.contains("muay", true) || name.contains("box", true) || name.contains("martial", true) -> WorkoutType.MuayThai
        name.contains("run", true) -> WorkoutType.Running
        name.contains("tennis", true) -> WorkoutType.Tennis
        name.contains("cross", true) || name.contains("strength", true) -> WorkoutType.CrossFit
        else -> WorkoutType.Other
    },
    hr,
    rounds,
    tag,
)

/** Records the activity in progress and keeps finished ones on the phone. */
object Workouts {
    data class Active(val type: WorkoutType, val start: Long, val hr: List<Pair<Long, Int>>, val rounds: List<Pair<Long, Long>> = emptyList())

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()
    private val _all = MutableStateFlow<List<Workout>>(emptyList())
    val all: StateFlow<List<Workout>> = _all.asStateFlow()
    private var lastSample = 0L

    /** Session kind by workout start time; works for in-app and strap-synced sessions alike. */
    private val _tags = MutableStateFlow<Map<Long, SessionKind>>(emptyMap())
    val tags: StateFlow<Map<Long, SessionKind>> = _tags.asStateFlow()

    fun kindOf(w: Workout, tags: Map<Long, SessionKind> = _tags.value): SessionKind? =
        tags[w.start] ?: w.tag?.let { t -> SessionKind.entries.firstOrNull { it.name == t } }

    fun setKind(ctx: Context, w: Workout, k: SessionKind) {
        val m = _tags.value + (w.start to k)
        _tags.value = m
        val o = JSONObject(); m.forEach { (t, v) -> o.put(t.toString(), v.name) }
        ctx.getSharedPreferences("helio_workouts", Context.MODE_PRIVATE).edit().putString("tags", o.toString()).apply()
    }

    /** The activity that just ended, so the detail opens wherever it was ended from. */
    private val _finished = MutableStateFlow<Workout?>(null)
    val finished: StateFlow<Workout?> = _finished.asStateFlow()
    fun consumeFinished() { _finished.value = null }

    fun start(type: WorkoutType) {
        lastSample = 0
        _active.value = Active(type, System.currentTimeMillis(), emptyList())
    }

    /** Called by the Bluetooth service on every beat; keeps one sample every 5 s. */
    fun onHr(t: Long, bpm: Int) {
        val a = _active.value ?: return
        if (t - lastSample < 5000) return
        lastSample = t
        _active.value = a.copy(hr = a.hr + (t to bpm))
    }

    /** The round timer reports each finished work round here. */
    fun addRound(start: Long, end: Long) {
        val a = _active.value ?: return
        _active.value = a.copy(rounds = a.rounds + (start to end))
    }

    fun stop(ctx: Context): Workout? {
        val a = _active.value ?: return null
        _active.value = null
        if (a.hr.size < 12) return null
        val w = Workout(a.start, System.currentTimeMillis(), a.type, a.hr, a.rounds)
        save(ctx, _all.value + w)
        _finished.value = w
        return w
    }

    fun discard() { _active.value = null }

    fun delete(ctx: Context, w: Workout) = save(ctx, _all.value.filter { it.start != w.start })

    private fun save(ctx: Context, list: List<Workout>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("s", w.start); put("e", w.end); put("t", w.type.name)
                put("hr", JSONArray().apply { w.hr.forEach { (t, b) -> put(JSONArray().put(t).put(b)) } })
                put("r", JSONArray().apply { w.rounds.forEach { (a, b) -> put(JSONArray().put(a).put(b)) } })
            })
        }
        ctx.getSharedPreferences("helio_workouts", Context.MODE_PRIVATE).edit().putString("all", arr.toString()).apply()
        _all.value = list
    }

    fun load(ctx: Context) {
        _tags.value = try {
            val o = JSONObject(ctx.getSharedPreferences("helio_workouts", Context.MODE_PRIVATE).getString("tags", "{}") ?: "{}")
            o.keys().asSequence().mapNotNull { k -> runCatching { k.toLong() to SessionKind.valueOf(o.getString(k)) }.getOrNull() }.toMap()
        } catch (e: Exception) { emptyMap() }
        val raw = ctx.getSharedPreferences("helio_workouts", Context.MODE_PRIVATE).getString("all", "[]") ?: "[]"
        _all.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val hr = o.getJSONArray("hr")
                Workout(
                    o.getLong("s"), o.getLong("e"),
                    runCatching { WorkoutType.valueOf(o.getString("t")) }.getOrDefault(WorkoutType.Other),
                    (0 until hr.length()).map { k -> hr.getJSONArray(k).let { it.getLong(0) to it.getInt(1) } },
                    o.optJSONArray("r")?.let { r -> (0 until r.length()).map { k -> r.getJSONArray(k).let { it.getLong(0) to it.getLong(1) } } } ?: emptyList(),
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    /** A believable tennis session for trying the screens on the emulator. */
    fun sample(): Workout {
        val start = System.currentTimeMillis() - 3 * 3600_000L
        val hr = (0..970).map { k ->
            val t = start + k * 5000L
            val base = when { k < 40 -> 105 + k; k < 760 -> 140 + (12 * kotlin.math.sin(k / 60.0)).toInt(); k < 790 -> 98; else -> 132 - (k - 790) / 8 }
            t to (base + (k * 37 % 13) - 6)
        }
        return Workout(start, start + 970 * 5000L, WorkoutType.Tennis, hr)
    }
}

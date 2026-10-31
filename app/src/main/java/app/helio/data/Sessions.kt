package app.helio.data

import app.helio.logic.Strain
import app.helio.logic.Vo2Max
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * One set of rules for sessions, applied once to the days before any screen, the weekly goal,
 * the progress cards or the widget read them:
 *
 * 1. **One session, counted once.** The strap logs some workouts by itself; when one overlaps a
 *    session recorded in Helio (by half of the shorter one or more), it is the same session and
 *    Helio's recording wins (it has the sport, the rounds and live detail).
 * 2. **Max heart rate from what you actually reach.** Each day's max includes the peaks of the
 *    sessions recorded in Helio (third-highest reading, so one spike doesn't count), so the
 *    observed max takes over from the age formula as soon as you go past it.
 * 3. **One strain for a session everywhere:** the same max HR, and the resting HR of the session's
 *    own day, wherever its strain is shown or added up.
 */
object Sessions {
    data class Context(val hrMax: Double, private val rest: Map<LocalDate, Double>) {
        fun restOn(d: LocalDate): Double = rest[d] ?: rest.filterKeys { it <= d }.maxByOrNull { it.key }?.value ?: 60.0
    }

    private val zone get() = ZoneId.systemDefault()
    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Same session: they overlap by at least half of the shorter one. */
    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
        val ov = min(aEnd, bEnd) - max(aStart, bStart)
        val shorter = min(aEnd - aStart, bEnd - bStart)
        return ov > 0 && shorter > 0 && ov * 2 >= shorter
    }

    /** A session's peak: its third-highest reading, so a single optical spike doesn't set your max. */
    fun peak(hr: List<Pair<Long, Int>>): Int? = hr.map { it.second }.sortedDescending().getOrNull(2)

    /** The days with duplicates removed and session peaks folded into each day's max HR; and the context to score sessions with. */
    fun normalize(days: List<DaySummary>, workouts: List<Workout>, profile: Profile): Pair<List<DaySummary>, Context> {
        val byDay = workouts.groupBy { dateOf(it.start) }
        val merged = days.map { d ->
            val mine = byDay[d.date].orEmpty()
            val acts = d.activities.filter { a -> a.endMs <= a.startMs || mine.none { w -> overlaps(a.startMs, a.endMs, w.start, w.end) } }
            val peaks = mine.mapNotNull { peak(it.hr) }
            d.copy(activities = acts, maxHr = (listOfNotNull(d.maxHr) + peaks).maxOrNull())
        }
        val today = merged.lastOrNull()?.date ?: LocalDate.now()
        val hrMax = Vo2Max.hrMax(profile, merged, today).first
        val ctx = Context(hrMax, merged.mapNotNull { d -> d.restingHr?.let { d.date to it.toDouble() } }.toMap())
        // Strap sessions scored the same way as Helio's.
        val scored = merged.map { d ->
            d.copy(activities = d.activities.map { a ->
                if (a.hr.size > 10) a.copy(strain = round1(Strain.fromHeartRate(a.hr, ctx.restOn(d.date), hrMax))) else a
            })
        }
        return scored to ctx
    }

    /** Strain of the sessions recorded in Helio, per day (the weekly goal adds these to the strap's own sessions). */
    fun strainByDay(workouts: List<Workout>, ctx: Context): Map<LocalDate, Double> =
        workouts.groupBy { dateOf(it.start) }.mapValues { (d, ws) -> ws.sumOf { Strain.fromHeartRate(it.hr, ctx.restOn(d), ctx.hrMax) } }

    fun stats(w: Workout, ctx: Context, p: Profile): WorkoutStats = WorkoutMath.stats(w, ctx.hrMax, ctx.restOn(dateOf(w.start)), p)

    private fun round1(x: Double) = Math.round(x * 10) / 10.0
}

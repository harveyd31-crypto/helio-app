package app.helio.logic

import app.helio.data.DaySummary
import app.helio.data.Stage
import strap.science.BioAge
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The plain-words layer over the numbers, after Ridge 0.6 (github.com/TheCommishDeuce/ridge-helio-strap,
 * AGPL-3.0): values against your usual, the 7-day load range, sleep health's four dimensions, the
 * night's headline, the day / recovery / night sentences and the habits-based biological age.
 * Worked from Helio's days, so it is the same whether they came from the strap, Health Connect or samples.
 */
object Insights {
    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** The day's value and the median of up to 30 days before it (five or more). */
    data class VsUsual(val value: Double, val usual: Double?) {
        val delta: Double? get() = usual?.let { value - it }
    }

    fun vsUsual(days: List<DaySummary>, i: Int, f: (DaySummary) -> Double?): VsUsual? {
        val v = days.getOrNull(i)?.let(f) ?: return null
        val before = days.subList((i - 30).coerceAtLeast(0), i).mapNotNull(f)
        return VsUsual(v, before.takeIf { it.size >= 5 }?.let(::median))
    }

    // ── Load: 7-day mean cardio load against 0.8–1.3× the 28-day mean
    class Load(val acute: Double, val low: Double, val high: Double) {
        val words: String get() = when { acute < low -> "Below range"; acute > high -> "Load spike"; else -> "In range" }
    }

    fun loadRange(days: List<DaySummary>, i: Int): Load? {
        val rows = days.subList((i - 27).coerceAtLeast(0), i + 1).mapNotNull { it.extras?.cardioLoad }
        if (rows.size < 21) return null
        val chronic = rows.average().takeIf { it > 0 } ?: return null
        val acute = days.subList((i - 6).coerceAtLeast(0), i + 1).mapNotNull { it.extras?.cardioLoad }.takeIf { it.isNotEmpty() }?.average() ?: return null
        return Load(acute, 0.8 * chronic, 1.3 * chronic)
    }

    /** Mean recovery of the seven days ending on [i]. */
    fun recoveryWeek(days: List<DaySummary>, i: Int): Double? =
        days.subList((i - 6).coerceAtLeast(0), i + 1).mapNotNull { it.recovery?.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    /** Mean recovery of the seven days before [i] (three or more), for "+6 vs week". */
    fun recoveryWeekBefore(days: List<DaySummary>, i: Int): Double? =
        days.subList((i - 7).coerceAtLeast(0), i).mapNotNull { it.recovery?.toDouble() }.takeIf { it.size >= 3 }?.average()

    fun recoveryWord(r: Int): String = when { r >= 67 -> "High recovery"; r >= 34 -> "Medium recovery"; else -> "Low recovery" }

    fun hm(min: Number): String { val m = min.toDouble().roundToInt(); return "${m / 60}h ${String.format("%02d", m % 60)}m" }

    private fun clock(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { String.format("%02d:%02d", it.hour, it.minute) }

    /** The day so far in one paragraph, from its own numbers. */
    fun daySentence(days: List<DaySummary>, i: Int, today: Boolean): String {
        val d = days[i]
        val parts = listOfNotNull(
            d.sleep?.let { s -> "You slept ${hm(s.minutesAsleep)}" + (d.extras?.deviceSleepScore?.let { " and the strap scored the night $it" } ?: "") },
            d.recovery?.let { "recovery is $it%" },
            d.strain?.let { "strain ${if (today) "has reached" else "finished at"} ${String.format("%.1f", it)}" },
        )
        val week = recoveryWeek(days, i)?.let { "Your week averages ${it.roundToInt()}% recovery." }
        val load = loadRange(days, i)?.let { "Your 7-day load is ${it.acute.roundToInt()}, ${it.words.lowercase()} (${it.low.roundToInt()}–${it.high.roundToInt()})." }
        return listOfNotNull(parts.takeIf { it.isNotEmpty() }?.joinToString("; ")?.plus("."), week, load).joinToString(" ")
    }

    /** Which parts were at, above or below your usual, and the sleep against your need. */
    fun recoverySentence(d: DaySummary): String {
        val x = d.extras ?: return ""
        fun side(key: String, word: String, higherBetter: Boolean): String? {
            val p = x.recoveryParts.firstOrNull { it.key == key } ?: return null
            val b = p.baseline?.takeIf { it > 0 } ?: return null
            val rel = (p.value - b) / b
            val how = when { abs(rel) < 0.03 -> "at your usual"; (rel > 0) == higherBetter -> "better than usual"; else -> "worse than usual" }
            return "$word is $how"
        }
        val parts = listOfNotNull(side("hrv", "HRV", true), side("rhr", "resting heart rate", false), side("rr", "breathing rate", false))
        val sleep = d.sleep?.let { s -> x.sleepNeedMin?.let { "You slept ${hm(s.minutesAsleep)} of your ${it / 60}h${if (it % 60 != 0) " ${it % 60}m" else ""} need." } }
        return listOfNotNull(parts.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar { it.uppercase() }?.plus("."), sleep).joinToString(" ")
    }

    // ── Sleep
    data class SleepHealth(
        val durationOk: Boolean, val efficiencyOk: Boolean, val timingOk: Boolean, val regularityOk: Boolean?,
        val asleepMin: Int, val inBedMin: Int, val efficiencyPct: Double, val midpoint: LocalTime, val sri: Double?,
    ) {
        val met: Int get() = listOf(durationOk, efficiencyOk, timingOk, regularityOk == true).count { it }
        val of: Int get() = if (regularityOk == null) 3 else 4
    }

    /** The four sleep-health dimensions: 7–9 h asleep, ≥ 85 % efficiency, midpoint 02:00–04:00, regularity (SRI) ≥ 70. */
    fun sleepHealth(d: DaySummary): SleepHealth? {
        val s = d.sleep ?: return null
        val inBed = ((s.endMs - s.startMs) / 60_000).toInt().takeIf { it > 0 } ?: return null
        val eff = d.extras?.sleepEfficiency ?: (s.minutesAsleep * 100.0 / inBed).coerceAtMost(100.0)
        val mid = Instant.ofEpochMilli((s.startMs + s.endMs) / 2).atZone(ZoneId.systemDefault()).toLocalTime()
        val sri = d.extras?.sleepRegularity
        return SleepHealth(
            s.minutesAsleep in 420..540, eff >= 85, mid >= LocalTime.of(2, 0) && mid <= LocalTime.of(4, 0), sri?.let { it >= 70 },
            s.minutesAsleep, inBed, eff, mid, sri,
        )
    }

    /** Zepp's bands for the strap's own score. */
    fun scoreWord(score: Int): String = when { score >= 90 -> "Excellent"; score >= 80 -> "Good"; score >= 60 -> "Fair"; else -> "Poor" }

    /**
     * The night's headline and why (Ridge's SleepHeadline rules): short (< 6 h) and broken (≥ 30 min
     * awake after falling asleep, or 6+ wake-ups) first, then the strap's score, then light sleep.
     */
    fun headline(d: DaySummary): Pair<String, String?> {
        val s = d.sleep ?: return "No sleep recorded" to null
        val sorted = s.stages.sortedBy { it.start }
        val asleepBlocks = sorted.filter { it.stage != Stage.Awake }
        var waso = 0L; var wakeups = 0; var prevAwake = false
        if (asleepBlocks.isNotEmpty()) {
            val onset = asleepBlocks.first().start; val last = asleepBlocks.last().end
            for (b in sorted) {
                val awake = b.stage == Stage.Awake && b.start >= onset && b.end <= last
                if (awake) { waso += b.end - b.start; if (!prevAwake) wakeups++ }
                prevAwake = awake
            }
        }
        val wasoMin = (waso / 60_000).toInt()
        val deepRem = if (s.minutesAsleep > 0 && s.stages.isNotEmpty()) (s.minutesIn(Stage.Deep) + s.minutesIn(Stage.Rem)) / s.minutesAsleep.toDouble() else null
        val short = s.minutesAsleep < 360
        val broken = wasoMin >= 30 || wakeups >= 6
        val score = d.extras?.deviceSleepScore
        val woke = "Awake for $wasoMin min after falling asleep" + if (wakeups == 1) ", once." else ", over $wakeups wake-ups."
        return when {
            short && broken -> "Short, broken night" to "Under 6 hours asleep. $woke"
            short -> "Short night" to "Under 6 hours asleep."
            broken -> "Broken sleep" to woke
            score != null && score >= 90 -> "Great night" to null
            score != null && score >= 80 -> "Good night" to null
            deepRem != null && deepRem < 0.25 -> "Light sleep" to "Deep and REM were ${(deepRem * 100).roundToInt()} % of your sleep; most adults spend 40–50 % there."
            else -> "Okay night" to null
        }
    }

    /** The night in plain words. */
    fun nightSentence(d: DaySummary): String {
        val s = d.sleep ?: return ""
        val need = d.extras?.sleepNeedMin
        return buildList {
            val bed = clock(s.startMs); val wake = clock(s.endMs)
            if (need != null) {
                val gap = s.minutesAsleep - need
                add("You slept ${hm(s.minutesAsleep)} between $bed and $wake, ${abs(gap)} minutes ${if (gap >= 0) "over" else "under"} your need.")
            } else add("You slept ${hm(s.minutesAsleep)} between $bed and $wake.")
            if (s.stages.isNotEmpty()) add("Deep sleep came to ${hm(s.minutesIn(Stage.Deep))} and REM to ${hm(s.minutesIn(Stage.Rem))}.")
            d.extras?.sleepDebtMin?.let { add(if (it > 0) "Over 14 nights you are ${hm(it)} short." else "You carry no sleep debt over 14 nights.") }
        }.joinToString(" ")
    }

    // ── Biological age from habits (Ridge's BioAge: resting HR, steps, VO2 max, regularity, short sleep, strength)
    data class Habit(val key: BioAge.Term, val title: String, val shownYears: Double, val figureLabel: String, val figure: String, val aim: String?)
    data class HabitsAge(val low: Int, val high: Int, val age: Double, val chronological: Double, val early: Boolean, val fitnessCapped: Boolean, val habits: List<Habit>)

    /** "Takes off about 1 year", "adds half a year", "barely moves it". */
    fun pull(shown: Double): String {
        val a = abs(shown)
        if (a < 0.25) return "Barely moves it"
        val n = if (a < 0.75) 0.5 else a.roundToInt().toDouble()
        val amount = if (n == 0.5) "half a year" else "about ${n.toInt()} ${if (n == 1.0) "year" else "years"}"
        return (if (shown > 0) "Adds " else "Takes off ") + amount
    }

    fun habitsAge(days: List<DaySummary>, age: Int?, male: Boolean?, strengthMinPerWeek: Double, vo2max: Double?): HabitsAge? {
        if (age == null || male == null || age !in 20..79) return null
        val rhr = days.mapNotNull { it.restingHr?.toDouble() }
        val steps = days.mapNotNull { (it.extras?.steps ?: it.steps)?.toDouble()?.takeIf { s -> s > 500 } }
        val sri = days.mapNotNull { it.extras?.sleepRegularity }
        val asleep = days.mapNotNull { it.sleep?.minutesAsleep?.toDouble() }
        if (rhr.size < 21 || steps.size < 14 || asleep.size < 14) return null
        val inputs = BioAge.Inputs(
            age = age.toDouble(), sex = if (male) "male" else "female",
            sleepingRhr = median(rhr)!!, steps = steps.average(), sri = sri.takeIf { it.isNotEmpty() }?.average() ?: 80.9,
            asleepMin = asleep.average(), strengthMinPerWeek = strengthMinPerWeek, vo2max = vo2max,
        )
        val e = BioAge.estimate(inputs, early = days.size < BioAge.WINDOW_DAYS)
        val i = e.inputs
        val habits = e.parts.mapNotNull { p ->
            when (p.term) {
                BioAge.Term.RESTING_HR -> Habit(p.term, "Resting heart rate", p.shown, "Nightly low, median", "${i.sleepingRhr.roundToInt()} bpm", null)
                BioAge.Term.STEPS -> Habit(p.term, "Steps", p.shown, "A day, average", String.format("%,d", i.steps.roundToInt()), "8,000–10,000")
                BioAge.Term.VO2MAX -> i.vo2max?.let { Habit(p.term, "Fitness · VO₂ max", p.shown, "Your estimate", String.format("%.1f", it), null) }
                BioAge.Term.REGULARITY -> if (sri.isEmpty()) null else Habit(p.term, "Sleep regularity", p.shown, "SRI, average", "${i.sri.roundToInt()}", "70 or more")
                BioAge.Term.SHORT_SLEEP -> Habit(p.term, "Time asleep", p.shown, "A night, average", hm(i.asleepMin), "at least 6h 30m")
                BioAge.Term.STRENGTH -> Habit(p.term, "Strength training", p.shown, "Strength and cross workouts", "${i.strengthMinPerWeek.roundToInt()} min a week", "40 min a week")
            }
        }
        return HabitsAge(e.low.roundToInt(), e.high.roundToInt(), e.age, e.chronological, e.early, e.fitnessCapped, habits)
    }
}

package app.helio.logic

import app.helio.data.SessionKind
import app.helio.data.Workout
import app.helio.data.WorkoutType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-by-month (or quarter-by-quarter) trend for one sport, optionally for one kind of session
 * (pads vs sparring, match vs practice), so each is compared with itself.
 */
object SportProgress {
    enum class Period { Month, Quarter }

    data class Bucket(val label: String, val sessions: Int, val values: Map<String, Double?>)

    /** One trend line. higherIsBetter = null when up isn't good or bad (e.g. intensity). */
    data class Spec(val key: String, val title: String, val sub: String, val higherIsBetter: Boolean?, val fmt: (Double) -> String)

    fun buckets(
        all: List<Workout>, sport: WorkoutType, kind: SessionKind?, kindOf: (Workout) -> SessionKind?,
        period: Period, measure: (List<Workout>) -> Map<String, Double?>, today: LocalDate = LocalDate.now(),
    ): List<Bucket> {
        val zone = ZoneId.systemDefault()
        val ws = all.filter { it.type == sport && (kind == null || kindOf(it) == kind) }.distinctBy { it.start }
        fun keyOf(d: LocalDate) = if (period == Period.Month) d.year * 12 + d.monthValue - 1 else d.year * 4 + (d.monthValue - 1) / 3
        val nowKey = keyOf(today)
        val count = if (period == Period.Month) 6 else 4
        return (count - 1 downTo 0).map { back ->
            val key = nowKey - back
            val inB = ws.filter { keyOf(Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()) == key }
            val label = if (period == Period.Month) java.time.Month.of(key % 12 + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
            else "Q${key % 4 + 1} '${(key / 4) % 100}"
            Bucket(label, inB.size, if (inB.isEmpty()) emptyMap() else measure(inB))
        }
    }

    private fun List<Double>.avgOrNull() = if (isEmpty()) null else average()
    private fun signed(v: Double, unit: String) = "${if (v >= 0) "+" else "−"}${kotlin.math.abs(v).toInt()}$unit"

    // ── Muay Thai
    val muayThaiSpecs = listOf(
        Spec("rec", "REST RECOVERY", "bpm drop in the first minute of rest", true) { "−${it.toInt()}" },
        Spec("red", "TIME IN THE RED", "minutes at 90 %+ per session", null) { String.format("%.1f", it) },
        Spec("fade", "FADE", "last round vs best round", true) { signed(it, "%") },
        Spec("strain", "STRAIN", "average per session", null) { String.format("%.1f", it) },
    )

    fun muayThaiMeasure(hrMax: Double): (List<Workout>) -> Map<String, Double?> = { ws ->
        val ra = ws.mapNotNull { RoundAnalysis.analyse(it, hrMax) }
        val red = 0.9 * hrMax
        mapOf(
            "rec" to ra.mapNotNull { it.avgDrop?.toDouble() }.avgOrNull(),
            "red" to ws.map { w ->
                var sec = 0L
                for (k in 1 until w.hr.size) if (w.hr[k].second >= red) sec += ((w.hr[k].first - w.hr[k - 1].first) / 1000).coerceIn(0, 30)
                sec / 60.0
            }.avgOrNull(),
            "fade" to ra.mapNotNull { it.fadePct?.toDouble() }.avgOrNull(),
            "strain" to ws.map { Strain.fromHeartRate(it.hr, 60.0, hrMax) }.avgOrNull(),
        )
    }

    // ── Tennis
    val tennisSpecs = listOf(
        Spec("rec", "RECOVERY BETWEEN POINTS", "bpm drop in the 30 s after a hard effort", true) { "−${it.toInt()}" },
        Spec("eff", "HARD EFFORTS", "per hour", null) { it.toInt().toString() },
        Spec("match", "MATCH INTENSITY", "share of time at 80 %+ of max", null) { "${it.toInt()}%" },
        Spec("hold", "HELD TO THE END", "last third vs first third", true) { signed(it, "%") },
        Spec("strain", "STRAIN", "average per session", null) { String.format("%.1f", it) },
    )

    fun tennisMeasure(hrMax: Double): (List<Workout>) -> Map<String, Double?> = { ws ->
        val ta = ws.mapNotNull { TennisAnalysis.analyse(it, hrMax) }
        mapOf(
            "rec" to ta.mapNotNull { it.recovery30?.toDouble() }.avgOrNull(),
            "eff" to ta.map { it.perHour }.avgOrNull(),
            "match" to ta.map { it.matchPct.toDouble() }.avgOrNull(),
            "hold" to ta.mapNotNull { it.holdPct?.toDouble() }.avgOrNull(),
            "strain" to ws.map { Strain.fromHeartRate(it.hr, 60.0, hrMax) }.avgOrNull(),
        )
    }

    fun specsFor(t: WorkoutType) = when (t) { WorkoutType.MuayThai -> muayThaiSpecs; WorkoutType.Tennis -> tennisSpecs; else -> emptyList() }
    fun measureFor(t: WorkoutType, hrMax: Double) = when (t) { WorkoutType.Tennis -> tennisMeasure(hrMax); else -> muayThaiMeasure(hrMax) }
}

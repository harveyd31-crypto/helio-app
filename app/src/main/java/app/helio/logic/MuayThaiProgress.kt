package app.helio.logic

import app.helio.data.MuayThaiKind
import app.helio.data.Workout
import app.helio.data.WorkoutType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-by-month (or quarter-by-quarter) Muay Thai trend, optionally for one kind of session, so
 * pads/bag and sparring are each compared with themselves.
 */
object MuayThaiProgress {
    enum class Period { Month, Quarter }

    data class Bucket(
        val label: String,
        val sessions: Int,
        /** Average heart-rate drop in the first minute of rest (timed sessions). */
        val recovery: Int?,
        /** Minutes at ≥ 90 % of max per session. */
        val redMin: Double?,
        /** Last round vs best round, % (timed sessions). */
        val fade: Int?,
        val strain: Double?,
    )

    fun buckets(
        all: List<Workout>, kind: MuayThaiKind?, kindOf: (Workout) -> MuayThaiKind?,
        hrMax: Double, period: Period, today: LocalDate = LocalDate.now(),
    ): List<Bucket> {
        val zone = ZoneId.systemDefault()
        val mt = all.filter { it.type == WorkoutType.MuayThai && (kind == null || kindOf(it) == kind) }
            .distinctBy { it.start }
        fun keyOf(d: LocalDate) = if (period == Period.Month) d.year * 12 + d.monthValue - 1 else d.year * 4 + (d.monthValue - 1) / 3
        val nowKey = keyOf(today)
        val count = if (period == Period.Month) 6 else 4
        return (count - 1 downTo 0).map { back ->
            val key = nowKey - back
            val inB = mt.filter { keyOf(Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()) == key }
            val label = if (period == Period.Month) {
                java.time.Month.of(key % 12 + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
            } else "Q${key % 4 + 1} '${(key / 4) % 100}"
            val ra = inB.mapNotNull { RoundAnalysis.analyse(it, hrMax) }
            val red = 0.9 * hrMax
            val redMins = inB.map { w ->
                var sec = 0L
                for (k in 1 until w.hr.size) if (w.hr[k].second >= red) sec += ((w.hr[k].first - w.hr[k - 1].first) / 1000).coerceIn(0, 30)
                sec / 60.0
            }
            Bucket(
                label, inB.size,
                ra.mapNotNull { it.avgDrop }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                redMins.takeIf { it.isNotEmpty() }?.average(),
                ra.mapNotNull { it.fadePct }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                inB.takeIf { it.isNotEmpty() }?.map { Strain.fromHeartRate(it.hr, 60.0, hrMax) }?.average(),
            )
        }
    }
}

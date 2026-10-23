package app.helio.logic

import app.helio.data.Workout

/**
 * Round-by-round breakdown of a timed session (Muay Thai, bag work, circuits).
 *
 * - Intensity: average and peak heart rate per round, and seconds at ≥ 90 % of max ("red zone").
 * - Recovery: heart-rate drop in the first 60 s of each rest (peak at the bell → 60 s later). A bigger
 *   drop means faster recovery; it shrinks as fatigue builds and grows as conditioning improves.
 * - Fade: last round's average heart rate against your best round. Holding within a few percent means
 *   you kept the output to the final bell.
 */
object RoundAnalysis {
    data class Round(val index: Int, val start: Long, val end: Long, val avgHr: Int, val peakHr: Int, val redS: Long, val drop60: Int?)
    data class Result(val rounds: List<Round>, val fadePct: Int?, val bestRound: Int, val avgDrop: Int?)

    fun analyse(w: Workout, hrMax: Double): Result? {
        if (w.rounds.isEmpty() || w.hr.size < 4) return null
        val red = 0.9 * hrMax
        val rounds = w.rounds.mapIndexedNotNull { i, (a, b) ->
            val inside = w.hr.filter { it.first in a..b }
            if (inside.isEmpty()) return@mapIndexedNotNull null
            var redS = 0L
            for (k in 1 until inside.size) if (inside[k].second >= red) redS += ((inside[k].first - inside[k - 1].first) / 1000).coerceIn(0, 30)
            val nextStart = w.rounds.getOrNull(i + 1)?.first ?: w.end
            val drop = if (nextStart - b >= 55_000) {
                val peak = w.hr.filter { it.first in (b - 20_000)..(b + 5_000) }.maxOfOrNull { it.second }
                val later = w.hr.minByOrNull { kotlin.math.abs(it.first - (b + 60_000)) }?.takeIf { kotlin.math.abs(it.first - (b + 60_000)) < 10_000 }
                if (peak != null && later != null) (peak - later.second).coerceAtLeast(0) else null
            } else null
            Round(i + 1, a, b, inside.map { it.second }.average().toInt(), inside.maxOf { it.second }, redS, drop)
        }
        if (rounds.isEmpty()) return null
        val best = rounds.maxBy { it.avgHr }
        val fade = if (rounds.size >= 3) ((rounds.last().avgHr - best.avgHr) * 100.0 / best.avgHr).toInt() else null
        val drops = rounds.mapNotNull { it.drop60 }
        return Result(rounds, fade, best.index, if (drops.isEmpty()) null else drops.average().toInt())
    }
}

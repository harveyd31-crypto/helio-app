package app.helio.logic

import app.helio.data.Workout

/**
 * Tennis from heart rate alone. Tennis is stop-start: 5–20 s points, ~20 s between points, 90 s at
 * changeovers. With heart rate every ~5 s we can't see single shots, but we can see the hard spells
 * (long rallies, tough games) and how fast you come down between them.
 *
 * - Hard efforts: heart-rate peaks at ≥ 80 % of max that rose at least 8 bpm in the 45 s before.
 * - Recovery between points: how far heart rate falls in the 30 s after each hard effort.
 * - Match intensity: share of the session at ≥ 80 % of max.
 * - Held to the end: last third of the session vs the first third (after a 10-min warm-up).
 * - 15-minute blocks: average and peak heart rate and share of hard time, to see when you dipped.
 */
object TennisAnalysis {
    data class Effort(val t: Long, val peak: Int, val drop30: Int?)
    data class Block(val startMin: Int, val avgHr: Int, val peakHr: Int, val hardPct: Int)
    data class Result(
        val efforts: List<Effort>,
        val perHour: Double,
        val recovery30: Int?,
        val matchPct: Int,
        val holdPct: Int?,
        val avgPctMax: Int,
        val longestHardS: Long,
        val blocks: List<Block>,
    )

    fun analyse(w: Workout, hrMax: Double): Result? {
        val hr = w.hr
        if (hr.size < 60) return null
        val hard = 0.8 * hrMax
        val sm = hr.indices.map { i -> (hr[(i - 1).coerceAtLeast(0)].second + hr[i].second + hr[(i + 1).coerceAtMost(hr.lastIndex)].second) / 3.0 }

        val efforts = mutableListOf<Effort>()
        var last = Long.MIN_VALUE
        for (i in 9 until hr.size) {
            val v = sm[i]
            if (v < hard) continue
            val lo = (i - 6).coerceAtLeast(0); val hi = (i + 6).coerceAtMost(hr.lastIndex)
            if ((lo..hi).any { sm[it] > v }) continue
            val rise = v - (i - 9 until i).minOf { sm[it] }
            if (rise < 8) continue
            if (hr[i].first - last < 30_000) continue
            last = hr[i].first
            val after = (i + 1..(i + 6).coerceAtMost(hr.lastIndex)).toList()
            val drop = if (after.size >= 5) (v - after.minOf { sm[it] }).toInt().coerceAtLeast(0) else null
            efforts += Effort(hr[i].first, v.toInt(), drop)
        }

        var total = 0L; var hardS = 0L; var run = 0L; var longest = 0L
        for (k in 1 until hr.size) {
            val dt = ((hr[k].first - hr[k - 1].first) / 1000).coerceIn(0, 30)
            total += dt
            if (hr[k].second >= hard) { hardS += dt; run += dt; longest = maxOf(longest, run) } else run = 0
        }
        val hours = (w.end - w.start) / 3_600_000.0

        val play = hr.filter { it.first >= w.start + 10 * 60_000 }
        val hold = if (play.size >= 30) {
            val third = play.size / 3
            val a = play.take(third).map { it.second }.average()
            val b = play.takeLast(third).map { it.second }.average()
            ((b - a) * 100 / a).toInt()
        } else null

        val blocks = (0 until ((w.end - w.start) / 900_000L + 1).toInt()).mapNotNull { b ->
            val from = w.start + b * 900_000L
            val inB = hr.filter { it.first >= from && it.first < from + 900_000L }
            if (inB.size < 12) null
            else Block(b * 15, inB.map { it.second }.average().toInt(), inB.maxOf { it.second }, inB.count { it.second >= hard } * 100 / inB.size)
        }

        val drops = efforts.mapNotNull { it.drop30 }
        return Result(
            efforts = efforts,
            perHour = if (hours > 0) efforts.size / hours else 0.0,
            recovery30 = if (drops.isEmpty()) null else drops.average().toInt(),
            matchPct = if (total > 0) (hardS * 100 / total).toInt() else 0,
            holdPct = hold,
            avgPctMax = (hr.map { it.second }.average() * 100 / hrMax).toInt(),
            longestHardS = longest,
            blocks = blocks,
        )
    }
}

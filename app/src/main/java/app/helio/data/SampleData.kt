package app.helio.data

import app.helio.logic.Strain
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.sin

/** Demo days shown until Health Connect has data. Deterministic, so screenshots stay stable. */
object SampleData {
    private fun sampleSleep(date: LocalDate, bed: LocalTime, wake: LocalTime, asleep: Int, s: Int): SleepInfo {
        val zone = java.time.ZoneId.systemDefault()
        val bedDate = if (bed.hour >= 12) date.minusDays(1) else date
        val start = bedDate.atTime(bed).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(wake).atZone(zone).toInstant().toEpochMilli()
        val blocks = mutableListOf<StageBlock>()
        var t = start
        fun add(min: Int, st: Stage) { if (min <= 0 || t >= end) return; val e = minOf(end, t + min * 60_000L); blocks += StageBlock(t, e, st); t = e }
        add(12, Stage.Light)
        var cycle = 0
        while (t < end) {
            add(listOf(42, 30, 16, 6, 0, 0)[cycle.coerceAtMost(5)] + (s % 4) * 2, Stage.Deep)
            add(22 + (cycle * 3 + s) % 10, Stage.Light)
            add(listOf(10, 18, 24, 30, 34, 36)[cycle.coerceAtMost(5)], Stage.Rem)
            if ((cycle + s) % 3 == 0) add(3 + s % 4, Stage.Awake)
            add(14, Stage.Light)
            cycle++
        }
        val hr = mutableListOf<Pair<Long, Int>>()
        val resp = mutableListOf<Pair<Long, Double>>()
        var k = 0
        var m = start
        while (m < end) {
            val st = blocks.firstOrNull { m >= it.start && m < it.end }?.stage ?: Stage.Light
            val base = when (st) { Stage.Deep -> 50; Stage.Light -> 54; Stage.Rem -> 59; Stage.Awake -> 64 }
            hr += m to (base + 3 * sin(k * 0.9 + s) + 2 * sin(k * 2.3)).toInt()
            resp += m to 14.6 + (if (st == Stage.Rem) 0.9 else 0.0) + 0.4 * sin(k * 0.7 + s)
            m += 3 * 60_000L; k++
        }
        return SleepInfo(
            minutesAsleep = asleep, bed = bed, wake = wake,
            bedDayLabel = bedDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
            startMs = start, endMs = end, stages = blocks, hr = hr, resp = resp,
        )
    }

    private val names = listOf("Muay Thai", "Run", "Tennis", "Cross", "Run")

    fun days(today: LocalDate = LocalDate.now(), count: Int = 120): List<DaySummary> =
        (count - 1 downTo 0).map { back -> day(today.minusDays(back.toLong()), back) }

    /** An activity with a believable heart-rate trace: warm-up, work with rounds/rallies, cool-down. */
    private fun sampleActivity(date: LocalDate, name: String, start: LocalTime, minutes: Long, s: Int): Activity {
        val zone = java.time.ZoneId.systemDefault()
        val t0 = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        val n = (minutes * 12).toInt()
        if (name == "Muay Thai") return sampleMuayThai(t0, start, minutes, s)
        if (name == "Tennis") return sampleTennis(t0, start, minutes, s)
        val (base, swing, period) = when (name) {
            "Tennis" -> Triple(138.0, 12.0, 14.0)
            "Cross" -> Triple(152.0, 14.0, 20.0)
            else -> Triple(148.0, 4.0, 60.0)
        }
        val hr = (0..n).map { k ->
            val warm = (k / 60.0).coerceAtMost(1.0)
            val cool = ((n - k) / 36.0).coerceAtMost(1.0)
            val v = 95 + (base - 95) * warm * cool + swing * sin(k * 2 * Math.PI / period) * warm * cool + ((k * 37 + s) % 11) - 5
            (t0 + k * 5000L) to v.toInt()
        }
        val strain = Strain.fromHeartRate(hr, 56.0, 188.0)
        return Activity(name, (strain * 10).toInt() / 10.0, start, start.plusMinutes(minutes), t0, t0 + n * 5000L, hr)
    }

    /**
     * Tennis: 10-min warm-up rally, then games of points (5–25 s of play, ~20 s between points) with a
     * 90 s changeover every two games. Recovery between points slowly improves over the months.
     */
    private fun sampleTennis(t0: Long, start: LocalTime, minutes: Long, s: Int): Activity {
        val end = t0 + minutes * 60_000L
        val rnd = java.util.Random(s.toLong() * 7919)
        val back = s - 3
        val hr = mutableListOf<Pair<Long, Int>>()
        var bpm = 95.0
        var t = t0
        var phaseEnd = t0 + 10 * 60_000L
        var playing = true
        var point = 0
        while (t <= end) {
            if (t >= phaseEnd && t >= t0 + 10 * 60_000L) {
                if (playing) { playing = false; point++; phaseEnd = t + if (point % 12 == 0) 90_000L else 20_000L }
                else { playing = true; phaseEnd = t + (5 + rnd.nextInt(if (rnd.nextInt(6) == 0) 30 else 14)) * 1000L }
            }
            val late = (t - t0).toDouble() / (end - t0)
            val target = when {
                t < t0 + 10 * 60_000L -> 112.0 + 18 * (t - t0) / 600_000.0
                playing -> 168.0 + (s % 4) - 3 * late
                else -> 132.0 + back * 0.06
            }
            bpm += (target - bpm) * (if (target > bpm) 0.45 else 0.30 - back * 0.0008)
            hr += t to (bpm + rnd.nextInt(5) - 2).toInt()
            t += 5000L
        }
        val strain = Strain.fromHeartRate(hr, 56.0, 188.0)
        val kind = listOf("Match", "Practice", "Match", "Lesson")[s % 4]
        return Activity("Tennis", (strain * 10).toInt() / 10.0, start, start.plusMinutes(minutes), t0, end, hr, emptyList(), kind)
    }

    /** Warm-up, then 3:00 rounds with 1:00 rest (heart rate climbs each round, drops in the rest, fades a little late on), cool-down. */
    private fun sampleMuayThai(t0: Long, start: LocalTime, minutes: Long, s: Int): Activity {
        val warm = 8 * 60_000L
        val count = ((minutes - 14) / 4).toInt().coerceIn(3, 8)
        val rounds = (0 until count).map { r -> (t0 + warm + r * 240_000L) to (t0 + warm + r * 240_000L + 180_000L) }
        val end = rounds.last().second + 6 * 60_000L
        val hr = mutableListOf<Pair<Long, Int>>()
        var bpm = 92.0
        var t = t0
        var k = 0
        while (t <= end) {
            val r = rounds.indexOfFirst { t >= it.first && t < it.second }
            val target = when {
                t < t0 + warm -> 105.0 + 25.0 * (t - t0) / warm
                r >= 0 -> 168.0 + (s % 5) + r * 1.5 - (if (r >= count - 2) 4.0 else 0.0) + 4 * sin(k * 0.8)
                t < rounds.last().second + 60_000 -> 124.0 + (s - 3) * 0.12
                else -> 100.0
            }
            bpm += (target - bpm) * (if (target > bpm) 0.16 else 0.13 - (s - 3) * 0.0004)
            hr += t to (bpm + ((k * 37 + s) % 7) - 3).toInt()
            t += 5000L; k++
        }
        val strain = Strain.fromHeartRate(hr, 56.0, 188.0)
        val st = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val kind = listOf("Pads", "Sparring", "Pads", "Both")[s % 4]
        return Activity("Muay Thai", (strain * 10).toInt() / 10.0, start, st, t0, end, hr, rounds, kind)
    }

    private fun sampleExtras(date: LocalDate, recovery: Int, sleepPerf: Int, s: Int): StrapExtras {
        val zone = java.time.ZoneId.systemDefault()
        val t0 = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val wakeMin = 7 * 60 + 10
        var c = 30.0 + s % 10
        val curve = (0 until 144).map { k ->
            val m = k * 10
            c += when {
                m < wakeMin -> 0.006 * 10 * (100 - c) * 0.9
                m in 17 * 60..18 * 60 + 30 -> -2.2
                m in 14 * 60..15 * 60 -> -0.9
                else -> -0.45
            }
            c = c.coerceIn(0.0, 100.0)
            (t0 + m * 60_000L) to c
        }
        val nowIdx = if (date == LocalDate.now()) (java.time.LocalTime.now().toSecondOfDay() / 600).coerceIn(1, 143) else 143
        val shown = curve.take(nowIdx + 1)
        val hrvSub = (recovery + 6 * sin(s * 1.9)).toInt().coerceIn(5, 99)
        return StrapExtras(
            charge = shown.last().second.toInt(), chargeAtWake = curve[wakeMin / 10].second.toInt(),
            chargeHigh = shown.maxOf { it.second }.toInt(), chargeLow = shown.minOf { it.second }.toInt(),
            chargeRecharged = curve[wakeMin / 10].second - curve.first().second + 8, chargeActivity = 18.0 + s % 9, chargeStress = 6.0 + s % 5, chargeLiving = 24.0,
            chargeCurve = shown,
            recoveryParts = listOf(
                RecoveryPart("hrv", hrvSub, 0.42, 40.0 + recovery / 3, 58.0),
                RecoveryPart("rhr", (recovery - 4 * sin(s * 1.1)).toInt().coerceIn(5, 99), 0.28, 62.0 - recovery / 10, 56.0),
                RecoveryPart("sleep", sleepPerf, 0.20, sleepPerf * 4.8, null),
                RecoveryPart("rr", (60 + 15 * sin(s * 0.8)).toInt(), 0.10, 14.8, 15.0),
            ),
            sleepNeedMin = 480, sleepDebtMin = (100 - sleepPerf).coerceAtLeast(0) * 6, sleepRegularity = 72.0 + 8 * sin(s * 0.5),
            sleepEfficiency = 88.0 + 4 * sin(s * 1.4), deviceSleepScore = (70 + 15 * sin(s * 1.7)).toInt(),
            breathingPerHour = 2.1 + 1.2 * sin(s * 1.3).coerceAtLeast(-0.9), breathingUsual = 2.4,
            spo2Night = 95.0 + sin(s * 0.9), spo2Min = 89.0 + 2 * sin(s * 1.2),
            steps = (7200 + 2600 * sin(s * 0.7)).toInt(), stepsUsualByNow = 6400,
            activeCalories = (420 + 160 * sin(s * 0.9)).toInt(), totalCalories = (2280 + 220 * sin(s * 0.9)).toInt(), distanceKm = 5.4,
            pai = (78 + 30 * sin(s * 0.3)).toInt(), paiToday = (12 + 10 * sin(s * 0.9)).toInt().coerceAtLeast(0),
            strapStress = (32 + 18 * sin(s * 1.7)).toInt(),
            illness = if (s % 17 == 0) "Breathing rate and skin temperature were both above your usual last night. Worth an easier day." else null,
            cardioLoad = 140.0 + 40 * sin(s * 0.9), typicalLoad = 150.0,
            skinTempNight = 33.9 + ((s % 7) - 3) / 10.0,
        )
    }

    private fun day(date: LocalDate, seed: Int): DaySummary {
        val s = seed + 3
        val recovery = (62 + 26 * sin(s * 1.3) + 8 * sin(s * 3.7)).toInt().coerceIn(18, 96)
        val strain = (11.5 + 5.5 * sin(s * 0.9 + 1) + 1.5 * sin(s * 2.3)).coerceIn(4.0, 20.5)
        val sleepPerf = (82 + 12 * sin(s * 1.7)).toInt().coerceIn(55, 100)
        val asleep = (300 + sleepPerf * 1.9).toInt()
        val wake = LocalTime.of(7, 0).plusMinutes(((s * 7) % 40).toLong())
        val bed = wake.minusMinutes(asleep.toLong() + 25)
        val actStart = LocalTime.of(17, 30).plusMinutes(((s * 13) % 90).toLong())
        val actLen = 35L + (s * 11) % 50
        val woStart = (actStart.toSecondOfDay() - 4.5 * 3600) / (18.5 * 3600)
        val woEnd = woStart + actLen * 60 / (18.5 * 3600)

        val sleepEnd = ((wake.toSecondOfDay() - 4.5 * 3600) / (18.5 * 3600)).toFloat().coerceIn(0f, 1f)
        val series = (0..180).map { k ->
            val f = k / 180f
            var v = if (f < sleepEnd) 0.95 else 1.4 + 0.5 * f
            if (f in woStart.toFloat()..woEnd.toFloat()) v = 2.6
            v += 0.32 * sin(k * 1.7 + s) + 0.22 * sin(k * 3.1 + 1 + s) + 0.16 * sin(k * 7.3 + 2 * s)
            StressPoint(f, v.coerceIn(0.2, 2.98))
        }

        return DaySummary(
            date = date,
            recovery = recovery,
            hrv = 40 + recovery / 3,
            restingHr = 62 - recovery / 10,
            strain = (strain * 10).toInt() / 10.0,
            strainTarget = Strain.target(recovery),
            sleepPerformance = sleepPerf,
            sleep = sampleSleep(date, bed, wake, asleep, s),
            respRate = 14.6 + (s % 5) * 0.3,
            skinTempDelta = ((s % 7) - 3) / 10.0,
            stressNow = series.last().v,
            stressUpdated = LocalTime.of(22, 30),
            stressSeries = series,
            chartStart = LocalTime.of(4, 30),
            chartEnd = LocalTime.of(23, 0),
            sleepBand = Band(0f, sleepEnd),
            workoutBands = listOf(Band(woStart.toFloat(), woEnd.toFloat().coerceAtMost(1f))),
            activities = listOf(
                sampleActivity(date, names[s % names.size], actStart, actLen, s),
            ),
            steps = (7200 + 2600 * sin(s * 0.7)).toInt(),
            moderateMin = (28 + 18 * sin(s * 1.1)).toInt().coerceAtLeast(0),
            vigorousMin = (8 + 8 * sin(s * 0.9 + 1)).toInt().coerceAtLeast(0),
            strengthMin = if (names[s % names.size] == "Cross") actLen.toInt() else 0,
            maxHr = (168 + 12 * sin(s * 0.9 + 1)).toInt(),
            extras = sampleExtras(date, recovery, sleepPerf, s),
            runs = if (names[s % names.size] == "Run") listOf(
                RunEffort(actLen.toDouble(), 2.85 + 0.12 * sin(s * 2.1) + (35 - seed) * 0.004, 148 + 5 * sin(s * 1.3)),
            ) else emptyList(),
        )
    }
}

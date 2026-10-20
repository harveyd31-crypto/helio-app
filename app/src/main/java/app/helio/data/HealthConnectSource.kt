package app.helio.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.helio.logic.Strain
import app.helio.logic.Stress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.sqrt
import kotlin.reflect.KClass

/**
 * Reads what the Zepp app (or any other app) wrote to Health Connect and turns it into days.
 * All sources are accepted: Pulse's "Fitbit only" filter is exactly what hid the Helio.
 *
 * The recovery and strain formulas here are V1 approximations; the plan is to port Pulse's
 * scoring engine (recovery, strain, sleep) once real Helio data is flowing.
 */
class HealthConnectSource(private val ctx: Context) {

    companion object {
        val HR_PERMISSION = HealthPermission.getReadPermission(HeartRateRecord::class)
        val PERMISSIONS: Set<String> = setOf(
            HR_PERMISSION,
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )

        fun available(ctx: Context): Boolean =
            HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

        private val CHART_START: LocalTime = LocalTime.of(4, 30)
        private val CHART_END: LocalTime = LocalTime.of(23, 0)
        private const val SLOT_MIN = 6L
    }

    private val client by lazy { HealthConnectClient.getOrCreate(ctx) }

    suspend fun hasHeartRate(): Boolean =
        client.permissionController.getGrantedPermissions().contains(HR_PERMISSION)

    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = 5000,
                    pageToken = token,
                ),
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    private suspend fun <T : Record> readSafe(type: KClass<T>, start: Instant, end: Instant): List<T> =
        try { readAll(type, start, end) } catch (e: Exception) { emptyList() }

    suspend fun loadDays(count: Int = 35): List<DaySummary> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val first = today.minusDays((count - 1).toLong())
        val start = first.atStartOfDay(zone).minusHours(12).toInstant()
        val end = Instant.now()

        val hr = readSafe(HeartRateRecord::class, start, end)
            .flatMap { r -> r.samples.map { it.time.toEpochMilli() to it.beatsPerMinute.toInt() } }
            .sortedBy { it.first }
        val rhr = readSafe(RestingHeartRateRecord::class, start, end)
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.map { it.beatsPerMinute.toDouble() }.average() }
        val hrv = readSafe(HeartRateVariabilityRmssdRecord::class, start, end)
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.map { it.heartRateVariabilityMillis }.average() }
        val resp = readSafe(RespiratoryRateRecord::class, start, end)
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.map { it.rate }.average() }
        val sleeps = readSafe(SleepSessionRecord::class, start, end)
            .groupBy { it.endTime.atZone(zone).toLocalDate() }
            .mapValues { (_, v) -> v.maxBy { Duration.between(it.startTime, it.endTime).toMinutes() } }
        val exercises = readSafe(ExerciseSessionRecord::class, start, end)

        data class Raw(val date: LocalDate, val day: DaySummary, val hrv: Double?, val rhr: Double?, val sleepPerf: Int?)

        val raws = (0 until count).map { k ->
            val date = first.plusDays(k.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayHr = slice(hr, dayStart, dayEnd)

            val sleep = sleeps[date]
            val sleepHr = sleep?.let { slice(hr, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }.orEmpty()
            val rest = rhr[date] ?: percentile(sleepHr.map { it.second.toDouble() }, 0.10)
                ?: percentile(dayHr.map { it.second.toDouble() }, 0.05)

            val strain = if (dayHr.size >= 30) Strain.fromHeartRate(dayHr, rest ?: 60.0) else null

            val sleepInfo = sleep?.let { s ->
                val total = Duration.between(s.startTime, s.endTime).toMinutes()
                val awake = s.stages.filter {
                    it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val bed = s.startTime.atZone(zone)
                SleepInfo(
                    minutesAsleep = (total - awake).toInt().coerceAtLeast(0),
                    bed = bed.toLocalTime(),
                    wake = s.endTime.atZone(zone).toLocalTime(),
                    bedDayLabel = bed.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                )
            }
            val sleepPerf = sleepInfo?.let { (it.minutesAsleep / 480.0 * 100).toInt().coerceIn(0, 100) }

            // Stress line on 6-minute slots across 04:30–23:00
            val chartStartMs = date.atTime(CHART_START).atZone(zone).toInstant().toEpochMilli()
            val chartEndMs = date.atTime(CHART_END).atZone(zone).toInstant().toEpochMilli()
            val span = (chartEndMs - chartStartMs).toFloat()
            val dayExercises = exercises.filter { it.endTime.toEpochMilli() > chartStartMs && it.startTime.toEpochMilli() < chartEndMs }
            val daytimeBase = (rest ?: 60.0) + 8.0
            val series = mutableListOf<StressPoint>()
            var t = chartStartMs
            var lastHrTime: Long? = null
            while (t < chartEndMs) {
                val slot = slice(hr, t, t + SLOT_MIN * 60_000)
                if (slot.isNotEmpty()) {
                    val inSleep = sleep != null && t >= sleep.startTime.toEpochMilli() && t < sleep.endTime.toEpochMilli()
                    val mean = slot.map { it.second }.average()
                    val v = Stress.fromHr(mean, if (inSleep) (rest ?: 60.0) else daytimeBase)
                    series += StressPoint((t - chartStartMs) / span, v)
                    lastHrTime = slot.last().first
                }
                t += SLOT_MIN * 60_000
            }
            fun frac(i: Instant) = ((i.toEpochMilli() - chartStartMs) / span).coerceIn(0f, 1f)

            val activities = exercises
                .filter { it.startTime.atZone(zone).toLocalDate() == date }
                .map { e ->
                    val w = slice(hr, e.startTime.toEpochMilli(), e.endTime.toEpochMilli())
                    Activity(
                        name = exerciseName(e),
                        strain = (Strain.fromHeartRate(w, rest ?: 60.0) * 10).toInt() / 10.0,
                        start = e.startTime.atZone(zone).toLocalTime(),
                        end = e.endTime.atZone(zone).toLocalTime(),
                    )
                }

            val day = DaySummary(
                date = date,
                recovery = null,
                hrv = hrv[date]?.toInt(),
                restingHr = rest?.toInt(),
                strain = strain?.let { (it * 10).toInt() / 10.0 },
                strainTarget = null,
                sleepPerformance = sleepPerf,
                sleep = sleepInfo,
                respRate = resp[date],
                skinTempDelta = null,
                stressNow = series.lastOrNull()?.v,
                stressUpdated = lastHrTime?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() },
                stressSeries = series,
                chartStart = CHART_START,
                chartEnd = CHART_END,
                sleepBand = sleep?.let { Band(frac(it.startTime), frac(it.endTime)) },
                workoutBands = dayExercises.map { Band(frac(it.startTime), frac(it.endTime)) },
                activities = activities,
            )
            Raw(date, day, hrv[date], rest, sleepPerf)
        }

        // Recovery: today's HRV and resting HR against the previous 30 days, plus sleep.
        return raws.mapIndexed { i, r ->
            val prior = raws.subList((i - 30).coerceAtLeast(0), i)
            val zHrv = z(r.hrv, prior.mapNotNull { it.hrv })
            val zRhr = z(r.rhr, prior.mapNotNull { it.rhr })
            val sleepTerm = r.sleepPerf?.let { (it - 85) * 0.3 } ?: 0.0
            val recovery = if (r.hrv == null && r.rhr == null && r.sleepPerf == null) null else {
                val score = if (zHrv != null) 55 + 18 * zHrv - 12 * (zRhr ?: 0.0) + sleepTerm
                else 55 - 15 * (zRhr ?: 0.0) + sleepTerm * 1.3
                score.toInt().coerceIn(1, 99)
            }
            r.day.copy(recovery = recovery, strainTarget = Strain.target(recovery))
        }
    }

    private fun slice(hr: List<Pair<Long, Int>>, from: Long, to: Long): List<Pair<Long, Int>> {
        var lo = 0; var hi = hr.size
        while (lo < hi) { val m = (lo + hi) ushr 1; if (hr[m].first < from) lo = m + 1 else hi = m }
        val out = ArrayList<Pair<Long, Int>>()
        var i = lo
        while (i < hr.size && hr[i].first < to) { out += hr[i]; i++ }
        return out
    }

    private fun percentile(xs: List<Double>, p: Double): Double? {
        if (xs.size < 10) return null
        val s = xs.sorted()
        return s[((s.size - 1) * p).toInt()]
    }

    private fun z(x: Double?, base: List<Double>): Double? {
        if (x == null || base.size < 4) return null
        val m = base.average()
        val sd = sqrt(base.sumOf { (it - m) * (it - m) } / base.size).coerceAtLeast(1.0)
        return ((x - m) / sd).coerceIn(-2.5, 2.5)
    }

    private fun exerciseName(e: ExerciseSessionRecord): String = e.title ?: when (e.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
        else -> "Workout"
    }
}

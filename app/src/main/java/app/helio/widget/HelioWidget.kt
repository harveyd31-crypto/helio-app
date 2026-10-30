package app.helio.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import app.helio.MainActivity
import app.helio.data.DaySummary
import app.helio.data.Stage
import app.helio.logic.Strain
import app.helio.logic.WeeklyGoal
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the widget shows, written by the app, the Bluetooth service and the background refresh. */
object WidgetStore {
    private const val PREFS = "helio_widget"

    data class Week(val goal: Double, val cumulative: Double, val pace: Double, val dayIndex: Int, val daily: List<Double?>)

    data class Snapshot(
        val week: Week? = null,
        val strain: Double? = null,
        val target: Double? = null,
        val recovery: Int? = null,
        val sleepScore: Int? = null,
        val activity: String? = null,
        val asleepMin: Int? = null,
        val bedWake: String? = null,
        val stages: List<Triple<Long, Long, Int>> = emptyList(),
        val sleepStart: Long = 0,
        val sleepEnd: Long = 0,
        val deepMin: Int? = null,
        val remMin: Int? = null,
        val lightMin: Int? = null,
        val hrv: Int? = null,
        val rhr: Int? = null,
        val steps: Int? = null,
        val live: Boolean = false,
        val hr: Int? = null,
    )

    /** The last day of [days] plus its week, as the widget draws them. */
    fun saveDays(ctx: Context, days: List<DaySummary>) {
        val d = days.lastOrNull() ?: return
        val g = WeeklyGoal.compute(days, d.date)
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val o = JSONObject().apply {
            put("week", JSONObject().apply {
                put("goal", g.goal); put("cum", g.cumulative); put("pace", g.pace); put("day", g.dayIndex)
                put("daily", JSONArray().apply { g.daily.forEach { put(it ?: JSONObject.NULL) } })
            })
            d.strain?.let { put("strain", it) }
            put("target", Strain.target(d.recovery))
            d.recovery?.let { put("rec", it) }
            (d.extras?.deviceSleepScore ?: d.sleepPerformance)?.let { put("sleep", it) }
            d.activities.lastOrNull()?.let { put("act", "${it.name} ${String.format("%.1f", it.strain)}") }
            d.sleep?.let { s ->
                put("asleep", s.minutesAsleep)
                put("bedWake", "${s.bed.format(hm)}–${s.wake.format(hm)}")
                put("s0", s.startMs); put("s1", s.endMs)
                put("deep", s.minutesIn(Stage.Deep)); put("rem", s.minutesIn(Stage.Rem)); put("light", s.minutesIn(Stage.Light))
                put("stages", JSONArray().apply {
                    s.stages.forEach { b ->
                        val lvl = when (b.stage) { Stage.Awake -> 0; Stage.Rem -> 1; Stage.Light -> 2; Stage.Deep -> 3 }
                        put(JSONArray().put(b.start).put(b.end).put(lvl))
                    }
                })
            }
            d.hrv?.let { put("hrv", it) }
            d.restingHr?.let { put("rhr", it) }
            (d.extras?.steps ?: d.steps)?.let { put("steps", it) }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("day", o.toString()).apply()
    }

    fun saveLive(ctx: Context, hr: Int?, stress: Double?, live: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("hr", hr ?: -1).putBoolean("live", live)
            .putString("updated", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            .apply()
    }

    fun read(ctx: Context): Snapshot {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = try { JSONObject(p.getString("day", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        fun JSONObject.d(k: String) = if (has(k) && !isNull(k)) getDouble(k) else null
        fun JSONObject.i(k: String) = if (has(k) && !isNull(k)) getInt(k) else null
        val w = o.optJSONObject("week")
        return Snapshot(
            week = w?.let { j ->
                val a = j.getJSONArray("daily")
                Week(j.getDouble("goal"), j.getDouble("cum"), j.getDouble("pace"), j.getInt("day"), (0 until a.length()).map { if (a.isNull(it)) null else a.getDouble(it) })
            },
            strain = o.d("strain"), target = o.d("target"), recovery = o.i("rec"), sleepScore = o.i("sleep"),
            activity = o.optString("act").takeIf { it.isNotEmpty() },
            asleepMin = o.i("asleep"), bedWake = o.optString("bedWake").takeIf { it.isNotEmpty() },
            stages = o.optJSONArray("stages")?.let { a -> (0 until a.length()).map { k -> a.getJSONArray(k).let { Triple(it.getLong(0), it.getLong(1), it.getInt(2)) } } } ?: emptyList(),
            sleepStart = o.optLong("s0"), sleepEnd = o.optLong("s1"),
            deepMin = o.i("deep"), remMin = o.i("rem"), lightMin = o.i("light"),
            hrv = o.i("hrv"), rhr = o.i("rhr"), steps = o.i("steps"),
            live = p.getBoolean("live", false), hr = p.getInt("hr", -1).takeIf { it > 0 },
        )
    }

    suspend fun refreshWidgets(ctx: Context) {
        try { HelioWidget().updateAll(ctx) } catch (_: Exception) { }
    }
}

/**
 * The home-screen widget: one image drawn at the widget's exact size, so it looks as designed.
 * Narrow on the cover screen: week, strain, recovery, sleep. Wide on the open screen: the details too.
 */
class HelioWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val ctx = LocalContext.current
        val size: DpSize = LocalSize.current
        val s = WidgetStore.read(ctx)
        val bmp = WidgetRender.render(ctx, s, size.width.value, size.height.value, ctx.resources.displayMetrics.density)
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "Helio: week ${s.week?.cumulative?.let { String.format("%.1f", it) } ?: "—"}, strain ${s.strain ?: "—"}, recovery ${s.recovery ?: "—"}, sleep ${s.sleepScore ?: "—"}",
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        )
    }
}

class HelioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HelioWidget()
}

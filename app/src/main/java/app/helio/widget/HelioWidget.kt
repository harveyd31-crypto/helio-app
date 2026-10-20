package app.helio.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.helio.MainActivity
import app.helio.data.DaySummary
import app.helio.logic.Stress
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the widget shows, written by the app and the Bluetooth service, read by the widget. */
object WidgetStore {
    private const val PREFS = "helio_widget"

    fun saveDay(ctx: Context, d: DaySummary) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("recovery", d.recovery ?: -1)
            .putFloat("strain", (d.strain ?: -1.0).toFloat())
            .putFloat("dayStress", (d.stressNow ?: -1.0).toFloat())
            .apply()
    }

    fun saveLive(ctx: Context, hr: Int?, stress: Double?, live: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("hr", hr ?: -1)
            .putFloat("liveStress", (stress ?: -1.0).toFloat())
            .putBoolean("live", live)
            .putString("updated", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
            .apply()
    }

    data class Snapshot(val recovery: Int?, val strain: Double?, val stress: Double?, val hr: Int?, val live: Boolean, val updated: String?)

    fun read(ctx: Context): Snapshot {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val live = p.getBoolean("live", false)
        val ls = p.getFloat("liveStress", -1f)
        val ds = p.getFloat("dayStress", -1f)
        val stress = if (live && ls >= 0) ls.toDouble() else if (ds >= 0) ds.toDouble() else null
        return Snapshot(
            recovery = p.getInt("recovery", -1).takeIf { it >= 0 },
            strain = p.getFloat("strain", -1f).takeIf { it >= 0 }?.toDouble(),
            stress = stress,
            hr = p.getInt("hr", -1).takeIf { it > 0 && live },
            live = live,
            updated = p.getString("updated", null),
        )
    }

    suspend fun refreshWidgets(ctx: Context) {
        try { HelioWidget().updateAll(ctx) } catch (_: Exception) { }
    }
}

class HelioWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = WidgetStore.read(context)
        provideContent { Content(s) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(s: WidgetStore.Snapshot) {
        val white = ColorProvider(Color.White)
        val muted = ColorProvider(Color(0xFF8E99A4))
        val recColor = when {
            s.recovery == null -> Color(0xFF8E99A4)
            s.recovery >= 67 -> Color(0xFF2FD566)
            s.recovery >= 34 -> Color(0xFFF2D22E)
            else -> Color(0xFFE8384F)
        }
        val stressColor = when {
            s.stress == null -> Color(0xFF8E99A4)
            s.stress < 1 -> Color(0xFF5AA9E6)
            s.stress <= 2 -> Color(0xFF43D17A)
            else -> Color(0xFFF0953A)
        }
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF171E23)).cornerRadius(22.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.live) "● LIVE" else "HELIO", style = TextStyle(color = ColorProvider(if (s.live) Color(0xFFFF453A) else Color(0xFF8E99A4)), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.defaultWeight())
                Text(s.updated ?: "", style = TextStyle(color = muted, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Cell(s.recovery?.let { "$it%" } ?: "—", "RECOVERY", recColor)
                Spacer(GlanceModifier.width(14.dp))
                Cell(s.strain?.let { String.format("%.1f", it) } ?: "—", "STRAIN", Color(0xFF4FB4FF))
                Spacer(GlanceModifier.width(14.dp))
                Cell(s.stress?.let { String.format("%.1f", it) } ?: "—", "STRESS · " + Stress.label(s.stress), stressColor)
            }
            if (s.hr != null) {
                Spacer(GlanceModifier.height(6.dp))
                Text("${s.hr} bpm", style = TextStyle(color = white, fontSize = 13.sp))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Cell(value: String, label: String, color: Color) {
        Column {
            Text(value, style = TextStyle(color = ColorProvider(color), fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Text(label, style = TextStyle(color = ColorProvider(Color(0xFF8E99A4)), fontSize = 10.sp, fontWeight = FontWeight.Medium))
        }
    }
}

class HelioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HelioWidget()
}

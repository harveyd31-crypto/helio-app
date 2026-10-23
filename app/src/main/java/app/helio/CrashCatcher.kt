package app.helio

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on the phone so the next launch can show it (instead of the app just
 * closing). Installed first thing in MainActivity.
 */
object CrashCatcher {
    private const val PREFS = "helio_crash"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last", sw.toString().take(6000)).commit()
            previous?.uncaughtException(t, e)
        }
    }

    /** The last crash, once; cleared after reading. */
    fun take(ctx: Context): String? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = p.getString("last", null) ?: return null
        p.edit().remove("last").apply()
        return s
    }
}

@androidx.compose.runtime.Composable
fun CrashScreen(crash: String, onOpen: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(app.helio.ui.theme.HelioColors.Bg)
            .safeDrawingPadding()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
    ) {
        androidx.compose.material3.Text("Helio crashed last time. Send this to Claude:", color = app.helio.ui.theme.HelioColors.RecYellow)
        androidx.compose.material3.TextButton(onClick = onOpen) { androidx.compose.material3.Text("Open Helio again") }
        androidx.compose.foundation.text.selection.SelectionContainer {
            androidx.compose.material3.Text(crash, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp)
        }
    }
}

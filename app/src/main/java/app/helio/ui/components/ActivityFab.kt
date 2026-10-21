package app.helio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.helio.data.WorkoutType
import app.helio.ui.theme.HelioColors
import app.helio.ui.theme.cap

private val options = listOf(
    WorkoutType.MuayThai to "Muay Thai",
    WorkoutType.Running to "Run",
    WorkoutType.CrossFit to "Cross",
    WorkoutType.Tennis to "Tennis",
)

/**
 * Floating "Start activity" pill, bottom right. Tap: the background dims and the four sports fan
 * out above it, each springing in a beat after the one below. Tap a sport to start recording.
 */
@Composable
fun BoxScope.ActivityFab(onPick: (WorkoutType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scrim by animateFloatAsState(if (open) 0.62f else 0f, tween(220), label = "scrim")
    val turn by animateFloatAsState(if (open) 135f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "turn")

    if (scrim > 0.01f) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false },
        )
    }

    Column(
        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEachIndexed { i, (type, label) ->
            val delay = (options.size - 1 - i) * 45
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(180, delayMillis = delay)) +
                    slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) { it / 2 } +
                    scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f),
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 3 } + scaleOut(tween(150), targetScale = 0.8f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label.uppercase(), style = cap(13.sp, Color.White),
                        modifier = Modifier.background(Color(0xE61B2228), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(52.dp).shadow(10.dp, CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF2B88E0), Color(0xFF1C5FA8))), CircleShape)
                            .border(1.dp, Color(0x33FFFFFF), CircleShape)
                            .clickable { open = false; onPick(type) },
                        contentAlignment = Alignment.Center,
                    ) { SportGlyph(type) }
                }
            }
        }

        Row(
            Modifier.height(56.dp).shadow(16.dp, RoundedCornerShape(28.dp))
                .background(
                    if (open) Brush.linearGradient(listOf(Color(0xFF39434C), Color(0xFF2A3239)))
                    else Brush.linearGradient(listOf(Color(0xFF3A9BF0), Color(0xFF1F67B8))),
                    RoundedCornerShape(28.dp),
                )
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(28.dp))
                .clickable { open = !open }
                .padding(start = 16.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.rotate(turn)) { if (turn > 60f) PlusGlyph() else StopwatchGlyph() }
            Spacer(Modifier.width(10.dp))
            Text(if (open) "CLOSE" else "START ACTIVITY", style = cap(13.sp, Color.White))
        }
    }
}

@Composable
private fun StopwatchGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val st = Stroke(2f * u, cap = StrokeCap.Round)
        drawCircle(Color.White, 8.5f * u, Offset(12f * u, 13.5f * u), style = st)
        drawLine(Color.White, Offset(10f * u, 2.5f * u), Offset(14f * u, 2.5f * u), 2f * u, StrokeCap.Round)
        drawLine(Color.White, Offset(12f * u, 2.5f * u), Offset(12f * u, 5f * u), 2f * u, StrokeCap.Round)
        drawLine(Color.White, Offset(18.5f * u, 5.5f * u), Offset(20f * u, 7f * u), 2f * u, StrokeCap.Round)
        drawLine(Color.White, Offset(12f * u, 13.5f * u), Offset(15.5f * u, 16.5f * u), 2.2f * u, StrokeCap.Round)
    }
}

@Composable
private fun PlusGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawLine(Color.White, Offset(12f * u, 5f * u), Offset(12f * u, 19f * u), 2.2f * u, StrokeCap.Round)
        drawLine(Color.White, Offset(5f * u, 12f * u), Offset(19f * u, 12f * u), 2.2f * u, StrokeCap.Round)
    }
}

/** Simple line icons per sport. */
@Composable
fun SportGlyph(t: WorkoutType, color: Color = Color.White) {
    Canvas(Modifier.size(26.dp)) {
        val u = size.width / 24f
        val st = Stroke(1.9f * u, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 1.9f * u, StrokeCap.Round)
        when (t) {
            WorkoutType.Running -> {
                drawCircle(color, 2f * u, Offset(14f * u, 4f * u), style = st)
                line(8f, 21f, 11f, 15f); line(11f, 15f, 14f, 18f); line(14f, 18f, 14f, 21f)
                line(6f, 12f, 9f, 8f); line(9f, 8f, 13f, 9f); line(13f, 9f, 16f, 13f); line(16f, 13f, 19f, 13f); line(11f, 15f, 12.5f, 9f)
            }
            WorkoutType.MuayThai -> { // boxing glove
                drawRoundRect(color, Offset(6f * u, 4f * u), androidx.compose.ui.geometry.Size(12f * u, 11f * u), androidx.compose.ui.geometry.CornerRadius(5f * u), style = st)
                drawRoundRect(color, Offset(7.5f * u, 15f * u), androidx.compose.ui.geometry.Size(9f * u, 5f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u), style = st)
                line(6f, 10f, 4f, 9f); line(4f, 9f, 4f, 7f); line(4f, 7f, 6f, 6.5f)
            }
            WorkoutType.CrossFit -> { // kettlebell
                drawCircle(color, 6.5f * u, Offset(12f * u, 14.5f * u), style = st)
                drawArc(color, 200f, 140f, false, Offset(7f * u, 2.5f * u), androidx.compose.ui.geometry.Size(10f * u, 9f * u), style = st)
            }
            WorkoutType.Tennis -> { // racket + ball
                drawOval(color, Offset(8f * u, 2.5f * u), androidx.compose.ui.geometry.Size(10f * u, 12f * u), style = st)
                line(11f, 14f, 6f, 21f)
                drawCircle(color, 1.8f * u, Offset(5f * u, 7f * u))
            }
            else -> drawCircle(color, 7f * u, Offset(12f * u, 12f * u), style = st)
        }
    }
}

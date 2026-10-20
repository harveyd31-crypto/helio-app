package app.helio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.helio.R

object HelioColors {
    val Bg = Color(0xFF0E1215)
    val BgTop = Color(0xFF29323A)
    val BgMid = Color(0xFF171E23)
    val Card = Color(0x0DFFFFFF)
    val CardEdge = Color(0x0FFFFFFF)
    val Track = Color(0x14FFFFFF)
    val Divider = Color(0x1AFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val TextSoft = Color(0xFFC4CCD4)
    val TextMuted = Color(0xFF8E99A4)
    val TextDim = Color(0xFF5E6A75)

    val Strain = Color(0xFF4FB4FF)
    val StrainDeep = Color(0xFF1F6FC2)
    val StrainLight = Color(0xFF5FC3FF)
    val StrainBadge = Color(0xFF2E8FE6)
    val SleepBadge = Color(0xFF7F98B2)

    val RecGreen = Color(0xFF2FD566)
    val RecYellow = Color(0xFFF2D22E)
    val RecRed = Color(0xFFE8384F)

    val StressBlue = Color(0xFF5AA9E6)
    val StressGreen = Color(0xFF43D17A)
    val StressYellow = Color(0xFFE6D24A)
    val StressOrange = Color(0xFFF0953A)
    val Live = Color(0xFFFF453A)

    val stressStops = arrayOf(
        0.0f to StressBlue, 0.45f to StressGreen, 0.8f to StressYellow, 1.0f to StressOrange,
    )

    fun recovery(pct: Int?): Color = when {
        pct == null -> TextMuted
        pct >= 67 -> RecGreen
        pct >= 34 -> RecYellow
        else -> RecRed
    }

    fun stress(v: Double?): Color = when {
        v == null -> TextMuted
        v < 1.0 -> StressBlue
        v <= 2.0 -> StressGreen
        else -> StressOrange
    }

    val background = Brush.verticalGradient(0f to BgTop, 0.42f to BgMid, 1f to Bg)
}

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

val InterTight = FontFamily(
    Font(R.font.inter_tight_500, FontWeight.Medium),
    Font(R.font.inter_tight_600, FontWeight.SemiBold),
    Font(R.font.inter_tight_700, FontWeight.Bold),
)

/** Numbers: Inter Tight, fixed-width digits, slightly tight. */
fun num(size: TextUnit, weight: FontWeight = FontWeight.SemiBold, color: Color = HelioColors.Text) = TextStyle(
    fontFamily = InterTight,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

/** Small uppercase labels. */
fun cap(size: TextUnit = 11.sp, color: Color = HelioColors.TextMuted, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    color = color,
    letterSpacing = 0.07.em,
)

/** Body text. */
fun body(size: TextUnit = 14.sp, color: Color = HelioColors.TextSoft, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    color = color,
    lineHeight = size * 1.45f,
)

fun Modifier.card(radius: Dp = 16.dp): Modifier = this
    .background(HelioColors.Card, RoundedCornerShape(radius))
    .border(1.dp, HelioColors.CardEdge, RoundedCornerShape(radius))

@Composable
fun HelioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = HelioColors.Bg,
            surface = HelioColors.Bg,
            primary = HelioColors.Strain,
        ),
        content = content,
    )
}

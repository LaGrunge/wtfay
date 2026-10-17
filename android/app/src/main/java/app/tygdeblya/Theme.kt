package app.tygdeblya

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Ink = Color(0xFF0E0E12)
    val Panel = Color(0xFF17171E)
    val Glass = Color(0xE6141419)
    val Stroke = Color(0x1FFFFFFF)
    val Acid = Color(0xFFC8FF2E)
    val Pink = Color(0xFFFF2E88)
    val Violet = Color(0xFF8B6CFF)
    val Gold = Color(0xFFFFD23F)
    val Text = Color(0xFFF4F4F6)
    val Muted = Color(0x99F4F4F6)
    val Faint = Color(0x4DF4F4F6)

    val PanelShape = RoundedCornerShape(28.dp)
    val PillShape = RoundedCornerShape(50)
    val Border = BorderStroke(1.dp, Stroke)
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Unbounded = FontFamily(
    variable(R.font.unbounded, 500),
    variable(R.font.unbounded, 700),
    variable(R.font.unbounded, 900),
)

val Manrope = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 800),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(900), fontSize = 30.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(700), fontSize = 10.sp, letterSpacing = 1.4.sp),
)

private val colors = darkColorScheme(
    primary = Brand.Acid,
    onPrimary = Brand.Ink,
    secondary = Brand.Pink,
    onSecondary = Color.White,
    tertiary = Brand.Violet,
    background = Brand.Ink,
    onBackground = Brand.Text,
    surface = Brand.Panel,
    onSurface = Brand.Text,
    surfaceContainerLow = Brand.Panel,
    surfaceContainer = Brand.Panel,
    surfaceContainerHigh = Color(0xFF1E1E26),
    onSurfaceVariant = Brand.Muted,
    outline = Brand.Stroke,
)

@Composable
fun TyGdeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

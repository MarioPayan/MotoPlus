package com.kaze.motoplus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.Status

/** Instrumento: monochrome plate, orange actions and separate status bands. */
@Immutable
data class Shop(
    val dark: Boolean,
    val paper: Color, val sheet: Color, val sheet2: Color,
    val ink: Color, val ink2: Color, val line: Color,
    /** Grid and non-zone plate lines. */
    val plate: Color,
    val accent: Color, val onAccent: Color,
    val ok: Color, val warn: Color, val bad: Color,
    /** How strongly a zone's fill takes its status colour. */
    val tint: Float,
) {
    fun status(s: Status) = when (s) { Status.ok -> ok; Status.warn -> warn; Status.bad -> bad; Status.none -> ink2 }
}

private val Dark = Shop(
    dark = true,
    paper = Color(0xFF111415), sheet = Color(0xFF1D2223), sheet2 = Color(0xFF272E2D),
    ink = Color(0xFFF2F5ED), ink2 = Color(0xFFA3AEAA), line = Color(0xFF343C3A), plate = Color(0xFFC6D0C6),
    accent = Color(0xFFFF8B3D), onAccent = Color(0xFF18140E),
    ok = Color(0xFF5FC47E), warn = Color(0xFFF2C14E), bad = Color(0xFFFF5A4F), tint = .06f,
)
private val Light = Shop(
    dark = false,
    paper = Color(0xFFF4F5EF), sheet = Color.White, sheet2 = Color(0xFFE7EBE5),
    ink = Color(0xFF202621), ink2 = Color(0xFF505C55), line = Color(0xFFC9D0C8), plate = Color(0xFF505C55),
    accent = Color(0xFF963900), onAccent = Color.White,
    ok = Color(0xFF2E7D4F), warn = Color(0xFF8A6100), bad = Color(0xFFB3261E), tint = .06f,
)

val LocalShop = staticCompositionLocalOf { Dark }

/** Widgets share the same brand palette; serialized bike accents remain compatible. */
fun shopFor(dark: Boolean): Shop = if (dark) Dark else Light

private val Barlow = FontFamily(
    Font(R.font.barlow_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)
private val PlexMono = FontFamily(Font(R.font.ibmplexmono_regular, FontWeight.Normal), Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold))

/** Condensed display titles and monospaced annotations. */
val Display = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 29.sp, letterSpacing = .7.sp)
val Mono = TextStyle(fontFamily = PlexMono)
/** Small, spaced annotation. */
val Caption = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp)

private val Body = Typography().let { t ->
    fun TextStyle.barlow() = copy(fontFamily = Barlow, fontSize = 18.sp)
    t.copy(
        bodyLarge = t.bodyLarge.barlow(), bodyMedium = t.bodyMedium.barlow(), bodySmall = t.bodySmall.barlow(),
        labelLarge = t.labelLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = .6.sp),
        labelMedium = t.labelMedium.barlow(), labelSmall = t.labelSmall.barlow(),
        titleLarge = t.titleLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = .7.sp),
        titleMedium = t.titleMedium.barlow(), titleSmall = t.titleSmall.barlow(),
        headlineSmall = t.headlineSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun MotoTheme(mode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (mode) { "system" -> isSystemInDarkTheme(); "light" -> false; else -> true }
    val s = shopFor(dark)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = s.accent, onPrimary = s.onAccent, secondaryContainer = s.accent.copy(alpha = .16f), onSecondaryContainer = s.ink,
        background = s.paper, surface = s.sheet, onBackground = s.ink, onSurface = s.ink,
        onSurfaceVariant = s.ink2, outline = s.line, outlineVariant = s.line,
        surfaceContainer = s.sheet, surfaceContainerLow = s.sheet, surfaceContainerHigh = s.sheet2, surfaceContainerHighest = s.sheet2,
    )
    val shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp))
    CompositionLocalProvider(LocalShop provides s) {
        MaterialTheme(colorScheme = scheme, typography = Body, shapes = shapes) {
            // Screens drawn outside a Scaffold still get ink-coloured text.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides s.ink, content = content)
        }
    }
}

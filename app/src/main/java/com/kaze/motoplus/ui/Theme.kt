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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.Accent
import com.kaze.motoplus.domain.Status

/** "Plano técnico": the bike as an engineering drawing. Dark is the main mode, light is a printed blueprint. */
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
    paper = Color(0xFF0A1A2E), sheet = Color(0xFF0F2542), sheet2 = Color(0xFF143058),
    ink = Color(0xFFE2EEFF), ink2 = Color(0xFF8EAED4), line = Color(0xFF22406A), plate = Color(0xFF9FC3EE),
    accent = Color(0xFF5AD1FF), onAccent = Color(0xFF04131F),
    ok = Color(0xFF5EE6A8), warn = Color(0xFFFFC857), bad = Color(0xFFFF5D73), tint = .16f,
)
private val Light = Shop(
    dark = false,
    paper = Color(0xFFF3F7FB), sheet = Color.White, sheet2 = Color(0xFFE8F0F8),
    ink = Color(0xFF0B2545), ink2 = Color(0xFF4A6585), line = Color(0xFFC9D6E5), plate = Color(0xFF1F4E8C),
    accent = Color(0xFF0077B6), onAccent = Color.White,
    ok = Color(0xFF1E8E5A), warn = Color(0xFFB7791F), bad = Color(0xFFD1344B), tint = .10f,
)

/** Accent colour per mode: bright on the dark drawing, deep on the printed one. */
fun Accent.color(dark: Boolean) = when (this) {
    Accent.cyan -> if (dark) Color(0xFF5AD1FF) else Color(0xFF0077B6)
    Accent.violet -> if (dark) Color(0xFFA98BFF) else Color(0xFF6D4AFF)
    Accent.magenta -> if (dark) Color(0xFFF07CF0) else Color(0xFFB5179E)
    Accent.chalk -> if (dark) Color(0xFFF2F5F8) else Color(0xFF0B2545)
}

val Accent.label get() = when (this) { Accent.cyan -> "Cian señal"; Accent.violet -> "Violeta"; Accent.magenta -> "Magenta"; Accent.chalk -> "Tiza" }

val LocalShop = staticCompositionLocalOf { Dark }

@OptIn(ExperimentalTextApi::class)
private val Plex = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.ibmplexsans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)
private val PlexMono = FontFamily(Font(R.font.ibmplexmono_regular, FontWeight.Normal), Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold))

/** Titles and labels in monospaced capitals, like the annotations on a drawing. Callers pass upper-case text. */
val Display = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 2.sp)
val Mono = TextStyle(fontFamily = PlexMono)
/** Small spaced caption: "FIG. 1 · VISTA LATERAL". */
val Caption = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.6.sp)

private val Body = Typography().let { t ->
    fun TextStyle.plex() = copy(fontFamily = Plex)
    t.copy(
        bodyLarge = t.bodyLarge.plex(), bodyMedium = t.bodyMedium.plex(), bodySmall = t.bodySmall.plex(),
        labelLarge = t.labelLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp),
        labelMedium = t.labelMedium.plex(), labelSmall = t.labelSmall.plex(),
        titleLarge = t.titleLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 1.sp),
        titleMedium = t.titleMedium.plex(), titleSmall = t.titleSmall.plex(),
        headlineSmall = t.headlineSmall.copy(fontFamily = PlexMono, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun MotoTheme(accent: Accent = Accent.cyan, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val s = (if (dark) Dark else Light).let { it.copy(accent = accent.color(dark), onAccent = if (dark) Color(0xFF04131F) else Color.White) }
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

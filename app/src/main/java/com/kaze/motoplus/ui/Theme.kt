package com.kaze.motoplus.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import com.kaze.motoplus.R
import com.kaze.motoplus.domain.Status

@Immutable
data class Shop(
    val paper: Color, val sheet: Color, val ink: Color, val ink2: Color, val line: Color,
    val accent: Color, val ok: Color, val warn: Color, val bad: Color,
) {
    fun status(s: Status) = when (s) { Status.ok -> ok; Status.warn -> warn; else -> bad }
}

private val Light = Shop(
    paper = Color(0xFFF3F4F1), sheet = Color.White, ink = Color(0xFF1D2A33), ink2 = Color(0xFF55636C),
    line = Color(0xFFCFD5D3), accent = Color(0xFFD0661F), ok = Color(0xFF2F8F5B), warn = Color(0xFFC98D07), bad = Color(0xFFCF3B2F),
)
private val Dark = Shop(
    paper = Color(0xFF12171A), sheet = Color(0xFF1A2125), ink = Color(0xFFE4E9E7), ink2 = Color(0xFF9AA7AD),
    line = Color(0xFF33403F), accent = Color(0xFFEE8A45), ok = Color(0xFF4CC483), warn = Color(0xFFF0BB3C), bad = Color(0xFFFF6A5C),
)

val LocalShop = staticCompositionLocalOf { Light }

private val Barlow = FontFamily(Font(R.font.barlowcondensed_semibold, FontWeight.SemiBold), Font(R.font.barlowcondensed_bold, FontWeight.Bold))
@OptIn(ExperimentalTextApi::class)
private val Plex = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.ibmplexsans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)
private val PlexMono = FontFamily(Font(R.font.ibmplexmono_regular, FontWeight.Normal), Font(R.font.ibmplexmono_semibold, FontWeight.SemiBold))

/** Condensed bold for headings, like the labels on a service manual. */
val Display = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.5.sp)
val Mono = TextStyle(fontFamily = PlexMono)

private val Body = Typography().let { t ->
    fun TextStyle.plex() = copy(fontFamily = Plex)
    t.copy(
        bodyLarge = t.bodyLarge.plex(), bodyMedium = t.bodyMedium.plex(), bodySmall = t.bodySmall.plex(),
        labelLarge = t.labelLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.4.sp),
        labelMedium = t.labelMedium.plex(), labelSmall = t.labelSmall.plex(),
        titleLarge = t.titleLarge.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold), titleMedium = t.titleMedium.plex(), titleSmall = t.titleSmall.plex(),
        headlineSmall = t.headlineSmall.copy(fontFamily = Barlow, fontWeight = FontWeight.Bold),
    )
}

@Composable
fun MotoTheme(content: @Composable () -> Unit) {
    val s = if (isSystemInDarkTheme()) Dark else Light
    val scheme = (if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()).copy(
        primary = s.accent, onPrimary = if (s == Dark) Color(0xFF1A1206) else Color.White,
        background = s.paper, surface = s.sheet, onBackground = s.ink, onSurface = s.ink,
        onSurfaceVariant = s.ink2, outline = s.line, surfaceContainer = s.sheet, surfaceContainerLow = s.sheet,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalShop provides s) {
        MaterialTheme(colorScheme = scheme, typography = Body, content = content)
    }
}

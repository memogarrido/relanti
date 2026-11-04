package dev.garrido.relanti.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The Autopista palette from the design proposal, light and dark. */
@Immutable
data class RelantiColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val blue: Color,
    val blueInk: Color,
    val blueSoft: Color,
    val yellow: Color,
    val yellowInk: Color,
    val sunset: Color,
    val sunsetSoft: Color,
    val go: Color,
    val goSoft: Color,
    val stop: Color,
    val stopSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val tarmac: Color,
    val led: Color,
    val ledBg: Color,
    val navy: Color,
    val glow: Color,
    val dark: Boolean,
)

val LightPalette = RelantiColors(
    bg = Color(0xFFEEF1F5), surface = Color.White, ink = Color(0xFF1F232A), ink2 = Color(0xFF535A66),
    ink3 = Color(0xFF8A919C), line = Color(0xFFD5DAE2), blue = Color(0xFF1A56B0), blueInk = Color(0xFF14438A),
    blueSoft = Color(0xFFE2EBF8), yellow = Color(0xFFF2B705), yellowInk = Color(0xFF7A5600),
    sunset = Color(0xFFEA5B2A), sunsetSoft = Color(0xFFFCE6DC), go = Color(0xFF1E8E52), goSoft = Color(0xFFDDF2E6),
    stop = Color(0xFFC8233A), stopSoft = Color(0xFFF8DEE2), warn = Color(0xFF8A5A00), warnSoft = Color(0xFFFDF0D2),
    tarmac = Color(0xFF2B2F36), led = Color(0xFFFFB020), ledBg = Color(0xFF16181C), navy = Color(0xFF0F2240),
    glow = Color(0xFFE9F4FF), dark = false,
)

val DarkPalette = LightPalette.copy(
    bg = Color(0xFF111419), surface = Color(0xFF1A1E25), ink = Color(0xFFE9ECF1), ink2 = Color(0xFFAEB5C0),
    ink3 = Color(0xFF7B8390), line = Color(0xFF2C323C), blue = Color(0xFF4F8DF0), blueInk = Color(0xFF8DB6F7),
    blueSoft = Color(0xFF18273F), yellowInk = Color(0xFFF5C02E), sunsetSoft = Color(0xFF3A2219),
    go = Color(0xFF3DBB78), goSoft = Color(0xFF15301F), stop = Color(0xFFF0566B), stopSoft = Color(0xFF3A1A20),
    warn = Color(0xFFF5C02E), warnSoft = Color(0xFF3A2E12), tarmac = Color(0xFF3A3F48), ledBg = Color(0xFF0B0C0E),
    dark = true,
)

val LocalRelanti = staticCompositionLocalOf { LightPalette }

/** The current palette. */
@Composable
fun pal(): RelantiColors = LocalRelanti.current

@Composable
fun RelantiTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.blue, onPrimary = Color.White, secondary = p.yellow, onSecondary = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.blueSoft, onSurfaceVariant = p.ink2, outline = p.line, error = p.stop,
    ) else lightColorScheme(
        primary = p.blue, onPrimary = Color.White, secondary = p.yellow, onSecondary = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.blueSoft, onSurfaceVariant = p.ink2, outline = p.line, error = p.stop,
    )
    CompositionLocalProvider(LocalRelanti provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

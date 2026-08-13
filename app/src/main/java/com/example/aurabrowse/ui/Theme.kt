package com.example.aurabrowse.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.example.aurabrowse.core.Settings

data class AccentOption(val id: String, val label: String, val seed: Color)

val AccentOptions = listOf(
    AccentOption("blue", "blue", Color(0xFF087EDB)),
    AccentOption("green", "green", Color(0xFF1B8A5A)),
    AccentOption("purple", "purple", Color(0xFF7B5AE4)),
    AccentOption("orange", "orange", Color(0xFFE45B32)),
    AccentOption("pink", "pink", Color(0xFFE0257E)),
    AccentOption("red", "red", Color(0xFFD93025)),
    AccentOption("teal", "teal", Color(0xFF0F9D8F))
)

@Composable
fun AuraBrowseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val themePref by settings.theme.collectAsState(initial = "system")
    val accentPref by settings.accent.collectAsState(initial = "blue")
    val dark = when (themePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val seed = AccentOptions.firstOrNull { it.id == accentPref }?.seed ?: Color(0xFF087EDB)
    val colors = when {
        accentPref == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> accentDarkScheme(seed)
        else -> accentLightScheme(seed)
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun accentLightScheme(seed: Color): ColorScheme = androidx.compose.material3.lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = lerp(seed, Color.White, 0.82f),
    onPrimaryContainer = lerp(seed, Color.Black, 0.35f),
    secondary = lerp(seed, Color.Black, 0.15f),
    tertiary = lerp(seed, Color.White, 0.3f)
)

private fun accentDarkScheme(seed: Color): ColorScheme = androidx.compose.material3.darkColorScheme(
    primary = lerp(seed, Color.White, 0.2f),
    onPrimary = Color.Black,
    primaryContainer = lerp(seed, Color.Black, 0.6f),
    onPrimaryContainer = lerp(seed, Color.White, 0.6f),
    secondary = lerp(seed, Color.White, 0.4f),
    tertiary = lerp(seed, Color.Black, 0.3f)
)

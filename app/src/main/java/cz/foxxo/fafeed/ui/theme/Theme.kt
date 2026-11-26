package cz.foxxo.fafeed.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

val FaBlue = Color(0xFF7FB2E5)
val FaOrange = Color(0xFFF5A623)
val RatingGeneral = Color(0xFF4CAF50)
val RatingMature = Color(0xFFFFA000)
val RatingAdult = Color(0xFFE53935)

private val DarkColors = darkColorScheme(
    primary = FaBlue,
    onPrimary = Color(0xFF06121F),
    primaryContainer = Color(0xFF17344D),
    onPrimaryContainer = Color(0xFFD5E7F8),
    secondary = FaOrange,
    onSecondary = Color(0xFF2A1A00),
    background = Color(0xFF0E1216),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF14191F),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1E262E),
    onSurfaceVariant = Color(0xFFAEB9C4),
    outline = Color(0xFF35414C),
)

private val LightColors = lightColorScheme(
    primary = FaBlue,
    onPrimary = Color.White,
    secondary = FaOrange,
    onSecondary = Color(0xFF2A1A00),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF0F4),
    onSurfaceVariant = Color(0xFF49525B),
    outline = Color(0xFFCBD3DC),
)

@Composable
fun FaTheme(
    theme: String = THEME_SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (theme) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

fun ratingColor(rating: String): Color = when (rating) {
    "adult" -> RatingAdult
    "mature" -> RatingMature
    else -> RatingGeneral
}

fun ratingLabel(rating: String): String = when (rating) {
    "adult" -> "ADULT"
    "mature" -> "MATURE"
    else -> "GENERAL"
}

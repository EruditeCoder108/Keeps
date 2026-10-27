package dev.keeps.library.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.keeps.library.Appearance

private val light = lightColorScheme(
    primary = Color(0xFFD90020), onPrimary = Color.White,
    primaryContainer = Color.Black, onPrimaryContainer = Color.White,
    secondary = Color(0xFF626267), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEA), onSecondaryContainer = Color(0xFF252527),
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF626267), outline = Color(0xFF77777D), outlineVariant = Color(0xFFD8D8DC),
)
private val dark = darkColorScheme(
    primary = Color(0xFFFF5367), onPrimary = Color.Black,
    primaryContainer = Color.White, onPrimaryContainer = Color.Black,
    secondary = Color(0xFFB7B7BD), onSecondary = Color(0xFF29292D),
    secondaryContainer = Color(0xFF2A2A2D), onSecondaryContainer = Color(0xFFF5F5F4),
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceContainerLowest = Color(0xFF171719), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242426), surfaceContainerHigh = Color(0xFF303033),
    onSurfaceVariant = Color(0xFFB7B7BD), outline = Color(0xFF88888E), outlineVariant = Color(0xFF414147),
)

private val geist = FontFamily(
    Font(dev.keeps.library.R.font.geist, FontWeight.Normal),
    Font(dev.keeps.library.R.font.geist, FontWeight.Medium),
    Font(dev.keeps.library.R.font.geist, FontWeight.SemiBold),
    Font(dev.keeps.library.R.font.geist, FontWeight.Bold),
)
private val keepsTypography = Typography(
    headlineLarge = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.7).sp),
    titleLarge = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = geist, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = geist, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = geist, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = geist, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = geist, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable fun KeepsTheme(appearance: Appearance = Appearance.SYSTEM, content: @Composable () -> Unit) {
    val isDark = appearance == Appearance.DARK || (appearance == Appearance.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (isDark) dark else light, typography = keepsTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
        KeepsSystemBars()
        content()
    }
}

/** System icons follow the app appearance, including a full-screen viewer's own window. */
@Composable internal fun KeepsSystemBars() {
    val view = LocalView.current
    val context = LocalContext.current
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: context.activity()?.window
        if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

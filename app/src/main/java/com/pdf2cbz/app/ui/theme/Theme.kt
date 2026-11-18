package com.pdf2cbz.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.pdf2cbz.app.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3347B0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF565D74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE1FB),
    onSecondaryContainer = Color(0xFF131B2E),
    tertiary = Color(0xFF006A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF7AF6E3),
    onTertiaryContainer = Color(0xFF00201C),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF191B23),
    surface = Color(0xFFF6F6FB),
    onSurface = Color(0xFF191B23),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF454650),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F1F8),
    surfaceContainer = Color(0xFFEAEBF3),
    surfaceContainerHigh = Color(0xFFE4E5EE),
    surfaceContainerHighest = Color(0xFFDEDFE8),
    outline = Color(0xFF757681),
    outlineVariant = Color(0xFFC5C6D1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBCC4FF),
    onPrimary = Color(0xFF061E88),
    primaryContainer = Color(0xFF1E329A),
    onPrimaryContainer = Color(0xFFDFE2FF),
    secondary = Color(0xFFBEC5DF),
    onSecondary = Color(0xFF282F44),
    secondaryContainer = Color(0xFF3E455B),
    onSecondaryContainer = Color(0xFFDAE1FB),
    tertiary = Color(0xFF5AD9C7),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFF7AF6E3),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2EA),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2EA),
    surfaceVariant = Color(0xFF454650),
    onSurfaceVariant = Color(0xFFC5C6D1),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1C23),
    surfaceContainer = Color(0xFF1F2129),
    surfaceContainerHigh = Color(0xFF292B34),
    surfaceContainerHighest = Color(0xFF343640),
    outline = Color(0xFF8F909B),
    outlineVariant = Color(0xFF454650),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val Pdf2CbzTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val Pdf2CbzShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun Pdf2CbzTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val backgroundArgb = colorScheme.background.toArgb()
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.setBackgroundDrawable(ColorDrawable(backgroundArgb))
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Pdf2CbzTypography,
        shapes = Pdf2CbzShapes,
        content = content
    )
}

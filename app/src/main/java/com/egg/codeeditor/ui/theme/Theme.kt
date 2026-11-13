package com.egg.codeeditor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.egg.extension.api.ThemeRegistry
import com.egg.extension.api.ThemeSpec

private val DarkColorScheme = darkColorScheme(
    primary = ClaudeDarkPrimary,
    onPrimary = ClaudeDarkOnPrimary,
    secondary = ClaudeDarkTextSecondary,
    onSecondary = ClaudeDarkBackground,
    tertiary = ClaudeDarkAccentBlue,
    onTertiary = ClaudeDarkBackground,
    background = ClaudeDarkBackground,
    onBackground = ClaudeDarkTextPrimary,
    surface = ClaudeDarkSurface,
    onSurface = ClaudeDarkTextPrimary,
    surfaceVariant = ClaudeDarkSurfaceVariant,
    onSurfaceVariant = ClaudeDarkTextSecondary,
    outline = ClaudeDarkOutline,
    outlineVariant = ClaudeDarkSurfaceVariant,
    error = Color(0xFFE07161),
    onError = ClaudeDarkBackground,
    primaryContainer = ClaudeDarkActive,
    onPrimaryContainer = ClaudeDarkTextPrimary,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = ClaudeDarkTextPrimary,
    tertiaryContainer = ClaudeDarkHover,
    onTertiaryContainer = ClaudeDarkTextPrimary,
    surfaceContainerLowest = ClaudeDarkBackground,
    surfaceContainerLow = Color(0xFF171615),
    surfaceContainer = ClaudeDarkSurface,
    surfaceContainerHigh = ClaudeDarkSurfaceVariant,
    surfaceContainerHighest = ClaudeDarkActive
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudeDarkPrimary,
    onPrimary = ClaudeLightOnPrimary,
    secondary = ClaudeLightTextSecondary,
    onSecondary = ClaudeLightBackground,
    tertiary = ClaudeLightAccentBlue,
    onTertiary = ClaudeLightOnPrimary,
    background = ClaudeLightBackground,
    onBackground = ClaudeLightTextPrimary,
    surface = ClaudeLightSurface,
    onSurface = ClaudeLightTextPrimary,
    surfaceVariant = ClaudeLightSurfaceVariant,
    onSurfaceVariant = ClaudeLightTextSecondary,
    outline = ClaudeLightOutline,
    outlineVariant = ClaudeLightOutline,
    error = Color(0xFFE07161),
    onError = ClaudeLightOnPrimary,
    primaryContainer = ClaudeLightSurfaceVariant,
    onPrimaryContainer = ClaudeLightTextPrimary,
    secondaryContainer = Color(0xFFEFEFEF),
    onSecondaryContainer = ClaudeLightTextPrimary,
    tertiaryContainer = ClaudeLightSurface,
    onTertiaryContainer = ClaudeLightTextPrimary,
    surfaceContainerLowest = ClaudeLightBackground,
    surfaceContainerLow = ClaudeLightSurface,
    surfaceContainer = ClaudeLightSurfaceVariant,
    surfaceContainerHigh = ClaudeLightOutline,
    surfaceContainerHighest = ClaudeLightSurfaceVariant
)

@Composable
fun EggSystemBarStyle(darkTheme: Boolean, customBg: Int? = null) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val bg = customBg ?: MaterialTheme.colorScheme.background.toArgb()
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = bg
            window.navigationBarColor = bg
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private fun buildPluginScheme(spec: ThemeSpec, base: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    fun c(key: String, fallback: Color): Color =
        spec.colors[key]?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback
    return base.copy(
        primary = c("primary", base.primary),
        onPrimary = c("onPrimary", base.onPrimary),
        secondary = c("secondary", base.secondary),
        onSecondary = c("onSecondary", base.onSecondary),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("onTertiary", base.onTertiary),
        background = c("background", base.background),
        onBackground = c("onBackground", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("onSurface", base.onSurface),
        surfaceVariant = c("surfaceVariant", base.surfaceVariant),
        onSurfaceVariant = c("onSurfaceVariant", base.onSurfaceVariant),
        outline = c("outline", base.outline),
        error = c("error", base.error),
        onError = c("onError", base.onError),
        primaryContainer = c("primaryContainer", base.primaryContainer),
        secondaryContainer = c("secondaryContainer", base.secondaryContainer),
        surfaceContainerLowest = c("surfaceContainerLowest", base.surfaceContainerLowest),
        surfaceContainerLow = c("surfaceContainerLow", base.surfaceContainerLow),
        surfaceContainer = c("surfaceContainer", base.surfaceContainer),
        surfaceContainerHigh = c("surfaceContainerHigh", base.surfaceContainerHigh),
        surfaceContainerHighest = c("surfaceContainerHighest", base.surfaceContainerHighest)
    )
}

@Composable
fun EggTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pluginThemeId: String? = null,
    appFontFamily: FontFamily? = null,
    appFontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val pluginSpec = pluginThemeId?.let { ThemeRegistry.get(it) }
    val colorScheme = if (pluginSpec != null) buildPluginScheme(pluginSpec, baseScheme) else baseScheme

    val scaleTy: (androidx.compose.ui.text.TextStyle) -> androidx.compose.ui.text.TextStyle = { st ->
        if (appFontScale == 1f) st
        else st.copy(
            fontSize = if (st.fontSize.isSpecified) st.fontSize * appFontScale else st.fontSize,
            lineHeight = if (st.lineHeight.isSpecified) st.lineHeight * appFontScale else st.lineHeight
        )
    }
    val baseTy = Typography
    val scaledTy = Typography.copy(
        displayLarge = scaleTy(baseTy.displayLarge),
        displayMedium = scaleTy(baseTy.displayMedium),
        displaySmall = scaleTy(baseTy.displaySmall),
        headlineLarge = scaleTy(baseTy.headlineLarge),
        headlineMedium = scaleTy(baseTy.headlineMedium),
        headlineSmall = scaleTy(baseTy.headlineSmall),
        titleLarge = scaleTy(baseTy.titleLarge),
        titleMedium = scaleTy(baseTy.titleMedium),
        titleSmall = scaleTy(baseTy.titleSmall),
        bodyLarge = scaleTy(baseTy.bodyLarge),
        bodyMedium = scaleTy(baseTy.bodyMedium),
        bodySmall = scaleTy(baseTy.bodySmall),
        labelLarge = scaleTy(baseTy.labelLarge),
        labelMedium = scaleTy(baseTy.labelMedium),
        labelSmall = scaleTy(baseTy.labelSmall)
    )
    val effectiveTypo = if (appFontFamily != null) {
        scaledTy.copy(
            displayLarge = scaledTy.displayLarge.copy(fontFamily = appFontFamily),
            displayMedium = scaledTy.displayMedium.copy(fontFamily = appFontFamily),
            displaySmall = scaledTy.displaySmall.copy(fontFamily = appFontFamily),
            headlineLarge = scaledTy.headlineLarge.copy(fontFamily = appFontFamily),
            headlineMedium = scaledTy.headlineMedium.copy(fontFamily = appFontFamily),
            headlineSmall = scaledTy.headlineSmall.copy(fontFamily = appFontFamily),
            titleLarge = scaledTy.titleLarge.copy(fontFamily = appFontFamily),
            titleMedium = scaledTy.titleMedium.copy(fontFamily = appFontFamily),
            titleSmall = scaledTy.titleSmall.copy(fontFamily = appFontFamily),
            bodyLarge = scaledTy.bodyLarge.copy(fontFamily = appFontFamily),
            bodyMedium = scaledTy.bodyMedium.copy(fontFamily = appFontFamily),
            bodySmall = scaledTy.bodySmall.copy(fontFamily = appFontFamily),
            labelLarge = scaledTy.labelLarge.copy(fontFamily = appFontFamily),
            labelMedium = scaledTy.labelMedium.copy(fontFamily = appFontFamily),
            labelSmall = scaledTy.labelSmall.copy(fontFamily = appFontFamily)
        )
    } else scaledTy

    MaterialTheme(
        colorScheme = colorScheme,
        typography = effectiveTypo,
        content = content
    )
}
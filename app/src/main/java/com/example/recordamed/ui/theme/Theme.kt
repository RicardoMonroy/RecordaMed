package com.example.recordamed.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Trío de colores para una tarjeta de estado (fondo, texto sobre el fondo
 * y un color de acento para íconos/resaltados). Cada instancia ya viene
 * calibrada para el tema claro u oscuro, así las pantallas nunca necesitan
 * colores fijos que se rompan al cambiar de tema.
 */
data class StatusColorTrio(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    /** Color legible cuando [accent] se usa como fondo sólido (p.ej. un botón). */
    val onAccent: Color
)

data class RecordaMedExtendedColors(
    val success: StatusColorTrio,
    val due: StatusColorTrio,
    val voice: StatusColorTrio,
    val missed: StatusColorTrio
)

private val LightExtendedColors = RecordaMedExtendedColors(
    success = StatusColorTrio(SuccessContainerLight, OnSuccessContainerLight, SuccessAccentLight, OnSuccessAccentLight),
    due = StatusColorTrio(DueContainerLight, OnDueContainerLight, DueAccentLight, OnDueAccentLight),
    voice = StatusColorTrio(VoiceContainerLight, OnVoiceContainerLight, VoiceAccentLight, OnVoiceAccentLight),
    missed = StatusColorTrio(MissedContainerLight, OnMissedContainerLight, MissedAccentLight, OnMissedAccentLight)
)

private val DarkExtendedColors = RecordaMedExtendedColors(
    success = StatusColorTrio(SuccessContainerDark, OnSuccessContainerDark, SuccessAccentDark, OnSuccessAccentDark),
    due = StatusColorTrio(DueContainerDark, OnDueContainerDark, DueAccentDark, OnDueAccentDark),
    voice = StatusColorTrio(VoiceContainerDark, OnVoiceContainerDark, VoiceAccentDark, OnVoiceAccentDark),
    missed = StatusColorTrio(MissedContainerDark, OnMissedContainerDark, MissedAccentDark, OnMissedAccentDark)
)

private val LocalRecordaMedExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Punto de acceso a los colores semánticos extendidos, análogo a MaterialTheme. */
object RecordaMedExtras {
    val colors: RecordaMedExtendedColors
        @Composable
        get() = LocalRecordaMedExtendedColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = MedGreenPrimaryDark,
    onPrimary = MedOnPrimaryDark,
    secondary = MedGreenSecondaryDark,
    onSecondary = MedOnSecondaryDark,
    tertiary = MedGreenTertiaryDark,
    background = MedBackgroundDark,
    onBackground = MedOnBackgroundDark,
    surface = MedSurfaceDark,
    onSurface = MedOnSurfaceDark,
    surfaceVariant = MedSurfaceVariantDark,
    onSurfaceVariant = MedOnSurfaceVariantDark,
    outline = MedOutlineDark,
    outlineVariant = MedOutlineVariantDark,
    error = MedErrorDark,
    onError = MedOnErrorDark,
    errorContainer = MedErrorContainerDark,
    onErrorContainer = MedOnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = MedGreenPrimary,
    onPrimary = Color.White,
    secondary = MedGreenSecondary,
    onSecondary = Color.White,
    tertiary = MedGreenTertiary,
    background = MedBackgroundLight,
    onBackground = MedTextPrimary,
    surface = MedSurfaceLight,
    onSurface = MedTextPrimary,
    surfaceVariant = MedSurfaceVariantLight,
    onSurfaceVariant = MedTextSecondary,
    outline = MedOutlineLight,
    outlineVariant = MedOutlineVariantLight,
    error = MedErrorLight,
    onError = MedOnErrorLight,
    errorContainer = MedErrorContainerLight,
    onErrorContainer = MedOnErrorContainerLight
)

@Composable
fun RecordaMedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Usamos la paleta de confianza médica por defecto
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalRecordaMedExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

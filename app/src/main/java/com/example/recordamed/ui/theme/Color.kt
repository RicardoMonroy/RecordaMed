package com.example.recordamed.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// Paleta base — Verde médico de confianza (tema claro)
// ============================================================
val MedGreenPrimary = Color(0xFF1B5E20)
val MedGreenSecondary = Color(0xFF2E7D32)
val MedGreenTertiary = Color(0xFF388E3C)
val MedGreenLight = Color(0xFFE8F5E9)

val MedBackgroundLight = Color(0xFFF9FBF9)
val MedSurfaceLight = Color(0xFFFFFFFF)
val MedSurfaceVariantLight = Color(0xFFF1F5F1)
val MedOutlineLight = Color(0xFF75816F)
val MedOutlineVariantLight = Color(0xFFC1CBBD)

val MedTextPrimary = Color(0xFF1B241C)
val MedTextSecondary = Color(0xFF4A554D)

val MedWarning = Color(0xFFF57F17)
val MedWarningLight = Color(0xFFFFFDE7)

// Roles estándar Material 3 (error) — tema claro
val MedErrorLight = Color(0xFFBA1A1A)
val MedOnErrorLight = Color(0xFFFFFFFF)
val MedErrorContainerLight = Color(0xFFFFDAD6)
val MedOnErrorContainerLight = Color(0xFF410002)

// ============================================================
// Paleta base — Verde médico de confianza (tema oscuro)
// ============================================================
val MedGreenPrimaryDark = Color(0xFF81C784)
val MedGreenSecondaryDark = Color(0xFFA5D6A7)
val MedGreenTertiaryDark = Color(0xFFC8E6C9)

val MedBackgroundDark = Color(0xFF121813)
val MedSurfaceDark = Color(0xFF1B241C)
val MedSurfaceVariantDark = Color(0xFF263328)
val MedOutlineDark = Color(0xFF8B9690)
val MedOutlineVariantDark = Color(0xFF3B4A3E)

val MedOnPrimaryDark = Color(0xFF003912)
val MedOnSecondaryDark = Color(0xFF00391A)
val MedOnBackgroundDark = Color(0xFFE8F5E9)
val MedOnSurfaceDark = Color(0xFFE8F5E9)
val MedOnSurfaceVariantDark = Color(0xFFC2CCC3)

// Roles estándar Material 3 (error) — tema oscuro
val MedErrorDark = Color(0xFFFFB4AB)
val MedOnErrorDark = Color(0xFF690005)
val MedErrorContainerDark = Color(0xFF93000A)
val MedOnErrorContainerDark = Color(0xFFFFDAD6)

// ============================================================
// Colores semánticos extendidos (adaptados a claro/oscuro)
// Se usan para las tarjetas de estado: "toca ahora" (ámbar),
// "todo tomado" (verde éxito) y "voz/sonido" (amarillo cálido).
// Cada trío container/onContainer/accent está calibrado para
// mantener buen contraste tanto en tema claro como oscuro.
// ============================================================

// -- Éxito / completado (verde) --
val SuccessContainerLight = Color(0xFFE8F5E9)
val OnSuccessContainerLight = Color(0xFF1B5E20)
val SuccessAccentLight = Color(0xFF2E7D32)
val OnSuccessAccentLight = Color(0xFFFFFFFF)

val SuccessContainerDark = Color(0xFF1F3A26)
val OnSuccessContainerDark = Color(0xFFA5D6A7)
val SuccessAccentDark = Color(0xFF81C784)
val OnSuccessAccentDark = Color(0xFF003912)

// -- Requiere atención ahora (ámbar/naranja) --
val DueContainerLight = Color(0xFFFFF3E0)
val OnDueContainerLight = Color(0xFF6D3300)
val DueAccentLight = Color(0xFFE65100)
val OnDueAccentLight = Color(0xFFFFFFFF)

val DueContainerDark = Color(0xFF4A2E12)
val OnDueContainerDark = Color(0xFFFFCC80)
val DueAccentDark = Color(0xFFFFAB40)
val OnDueAccentDark = Color(0xFF4A2E12)

// -- Voz / sonido (amarillo cálido) --
val VoiceContainerLight = Color(0xFFFFFDE7)
val OnVoiceContainerLight = Color(0xFF3E2723)
val VoiceAccentLight = Color(0xFFF57F17)
val OnVoiceAccentLight = Color(0xFFFFFFFF)

val VoiceContainerDark = Color(0xFF3A331A)
val OnVoiceContainerDark = Color(0xFFFFE082)
val VoiceAccentDark = Color(0xFFFFCA28)
val OnVoiceAccentDark = Color(0xFF3A331A)

// -- No tomada / caducada (rojo) --
val MissedContainerLight = Color(0xFFFCE8E6)
val OnMissedContainerLight = Color(0xFFB3261E)
val MissedAccentLight = Color(0xFFC62828)
val OnMissedAccentLight = Color(0xFFFFFFFF)

val MissedContainerDark = Color(0xFF4A1515)
val OnMissedContainerDark = Color(0xFFF2B8B5)
val MissedAccentDark = Color(0xFFEF9A9A)
val OnMissedAccentDark = Color(0xFF4A1515)

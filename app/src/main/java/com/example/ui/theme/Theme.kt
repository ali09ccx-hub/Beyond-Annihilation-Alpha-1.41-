package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AfterExtinctionColorScheme =
  darkColorScheme(
    primary = EmberGold,
    onPrimary = EmberDark,
    primaryContainer = EmberDark,
    onPrimaryContainer = EmberGlow,
    secondary = SageMoss,
    onSecondary = NightVoid,
    background = NightVoid,
    onBackground = ParchmentInk,
    surface = ForestSlate,
    onSurface = ParchmentInk,
    surfaceVariant = ForestCard,
    onSurfaceVariant = MutedAsh,
    outline = BorderMoss,
    error = SignalRed,
    onError = ParchmentInk,
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = AfterExtinctionColorScheme,
    typography = Typography,
    content = content,
  )
}

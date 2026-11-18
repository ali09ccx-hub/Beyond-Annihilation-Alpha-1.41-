package com.example

import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.BorderMoss
import com.example.ui.theme.EmberDark
import com.example.ui.theme.EmberGlow
import com.example.ui.theme.EmberGold
import com.example.ui.theme.ForestCard
import com.example.ui.theme.ForestSlate
import com.example.ui.theme.MutedAsh
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NightVoid
import com.example.ui.theme.ParchmentInk
import com.example.ui.theme.SignalRed

class MainActivity : ComponentActivity() {
  private val viewModel: LauncherViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)

    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      var activeWebView by remember { mutableStateOf<WebView?>(null) }

      LaunchedEffect(uiState.keepScreenOn) {
        applyKeepScreenOn(uiState.keepScreenOn)
      }

      LaunchedEffect(uiState.immersiveMode, uiState.inGame) {
        applyImmersiveMode(uiState.immersiveMode)
      }

      MyApplicationTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .background(NightVoid),
          ) {
            if (!uiState.inGame) {
              LauncherSplashScreen(
                keepScreenOn = uiState.keepScreenOn,
                immersiveMode = uiState.immersiveMode,
                onToggleKeepScreenOn = viewModel::setKeepScreenOn,
                onToggleImmersiveMode = viewModel::setImmersiveMode,
                onStartClick = viewModel::startGame,
              )
            } else {
              BackHandler(enabled = true) {
                viewModel.requestExitPrompt()
              }

              GameWebView(
                modifier = Modifier.fillMaxSize(),
                onPageReady = viewModel::onGameLoaded,
                onWebViewCreated = { activeWebView = it },
              )

              AnimatedVisibility(
                visible = uiState.isGameLoading,
                enter = fadeIn(),
                exit = fadeOut(),
              ) {
                GameLoadingOverlay()
              }
            }

            if (uiState.showExitDialog) {
              ExitConfirmationDialog(
                onContinue = viewModel::dismissExitPrompt,
                onReturnHome = {
                  activeWebView = null
                  viewModel.returnToLauncher()
                },
                onConfirmExit = {
                  try {
                    activeWebView?.evaluateJavascript(
                      "if(typeof window.sendNetworkData==='function')window.sendNetworkData('{\"c\":\"leave\"}');",
                      null,
                    )
                  } catch (_: Throwable) {}
                  viewModel.dismissExitPrompt()
                  finish()
                },
              )
            }
          }
        }
      }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      applyImmersiveMode(viewModel.uiState.value.immersiveMode)
    }
  }

  private fun applyKeepScreenOn(enabled: Boolean) {
    if (enabled) {
      window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun applyImmersiveMode(enabled: Boolean) {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (enabled) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }
}

@Composable
private fun LauncherSplashScreen(
  keepScreenOn: Boolean,
  immersiveMode: Boolean,
  onToggleKeepScreenOn: (Boolean) -> Unit,
  onToggleImmersiveMode: (Boolean) -> Unit,
  onStartClick: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    AtmosphericBackdrop(modifier = Modifier.fillMaxSize())

    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .padding(horizontal = 24.dp, vertical = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      val isWideLayout = maxWidth >= 600.dp

      if (isWideLayout) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .widthIn(max = 880.dp)
              .verticalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(32.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
          ) {
            HeroLogoSection(centered = false, onStartClick = onStartClick)
          }

          LauncherSettingsCard(
            modifier = Modifier.weight(0.9f),
            keepScreenOn = keepScreenOn,
            immersiveMode = immersiveMode,
            onToggleKeepScreenOn = onToggleKeepScreenOn,
            onToggleImmersiveMode = onToggleImmersiveMode,
          )
        }
      } else {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .widthIn(max = 480.dp)
              .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          HeroLogoSection(centered = true, onStartClick = onStartClick)

          LauncherSettingsCard(
            modifier = Modifier.fillMaxWidth(),
            keepScreenOn = keepScreenOn,
            immersiveMode = immersiveMode,
            onToggleKeepScreenOn = onToggleKeepScreenOn,
            onToggleImmersiveMode = onToggleImmersiveMode,
          )
        }
      }
    }
  }
}

@Composable
private fun HeroLogoSection(
  centered: Boolean,
  onStartClick: () -> Unit,
) {
  val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start

  Column(
    horizontalAlignment = alignment,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(50),
      color = ForestCard.copy(alpha = 0.85f),
      border = BorderStroke(1.dp, BorderMoss),
    ) {
      Text(
        text = stringResource(R.string.launcher_subtitle),
        style = MaterialTheme.typography.labelSmall,
        color = EmberGlow,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      )
    }

    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.displaySmall,
      color = ParchmentInk,
      textAlign = textAlign,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Button(
      onClick = onStartClick,
      modifier =
        Modifier
          .testTag("start_game_button")
          .widthIn(min = 210.dp)
          .heightIn(min = 54.dp),
      shape = RoundedCornerShape(14.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = EmberGold,
          contentColor = EmberDark,
        ),
    ) {
      Icon(
        imageVector = Icons.Rounded.PlayArrow,
        contentDescription = stringResource(R.string.btn_start),
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.btn_start),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
      )
    }
  }
}

@Composable
private fun LauncherSettingsCard(
  modifier: Modifier = Modifier,
  keepScreenOn: Boolean,
  immersiveMode: Boolean,
  onToggleKeepScreenOn: (Boolean) -> Unit,
  onToggleImmersiveMode: (Boolean) -> Unit,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = ForestSlate.copy(alpha = 0.88f),
    border = BorderStroke(1.dp, BorderMoss),
  ) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
      SettingSwitchRow(
        icon = Icons.Rounded.BrightnessHigh,
        label = stringResource(R.string.setting_keep_screen_on),
        checked = keepScreenOn,
        onCheckedChange = onToggleKeepScreenOn,
        switchTestTag = "keep_screen_on_switch",
      )

      HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = BorderMoss.copy(alpha = 0.7f),
      )

      SettingSwitchRow(
        icon = Icons.Rounded.Fullscreen,
        label = stringResource(R.string.setting_immersive_mode),
        checked = immersiveMode,
        onCheckedChange = onToggleImmersiveMode,
        switchTestTag = "immersive_mode_switch",
      )
    }
  }
}

@Composable
private fun SettingSwitchRow(
  icon: ImageVector,
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  switchTestTag: String,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = EmberGold,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = ParchmentInk,
      modifier = Modifier.weight(1f),
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(switchTestTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = EmberDark,
          checkedTrackColor = EmberGold,
          uncheckedThumbColor = MutedAsh,
          uncheckedTrackColor = ForestCard,
          uncheckedBorderColor = BorderMoss,
        ),
    )
  }
}

@Composable
private fun AtmosphericBackdrop(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height

    // Night forest gradient sky
    drawRect(
      brush =
        Brush.verticalGradient(
          colors = listOf(Color(0xFF0E1814), Color(0xFF0A0F0D), Color(0xFF060A08)),
        ),
    )

    // Amber moon halo
    val moonCenter = Offset(w * 0.22f, h * 0.28f)
    val moonRadius = minOf(w, h) * 0.16f
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              EmberGold.copy(alpha = 0.22f),
              EmberGold.copy(alpha = 0.06f),
              Color.Transparent,
            ),
          center = moonCenter,
          radius = moonRadius * 3.0f,
        ),
      center = moonCenter,
      radius = moonRadius * 3.0f,
    )
    drawCircle(
      color = EmberGlow.copy(alpha = 0.85f),
      radius = moonRadius * 0.42f,
      center = moonCenter,
    )

    // Distant mountain range silhouette
    val ridgePath =
      Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.72f)
        lineTo(w * 0.16f, h * 0.54f)
        lineTo(w * 0.34f, h * 0.68f)
        lineTo(w * 0.56f, h * 0.46f)
        lineTo(w * 0.76f, h * 0.64f)
        lineTo(w * 0.90f, h * 0.52f)
        lineTo(w, h * 0.66f)
        lineTo(w, h)
        close()
      }
    drawPath(path = ridgePath, color = Color(0xFF0F1915))

    // Foreground ridge silhouette
    val foregroundPath =
      Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * 0.84f)
        lineTo(w * 0.24f, h * 0.76f)
        lineTo(w * 0.50f, h * 0.85f)
        lineTo(w * 0.78f, h * 0.74f)
        lineTo(w, h * 0.83f)
        lineTo(w, h)
        close()
      }
    drawPath(path = foregroundPath, color = Color(0xFF080D0B))
  }
}

@Composable
private fun GameLoadingOverlay() {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(NightVoid),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = ParchmentInk,
      )
      CircularProgressIndicator(
        color = EmberGold,
        trackColor = ForestCard,
        modifier = Modifier.size(36.dp),
      )
    }
  }
}

@Composable
private fun ExitConfirmationDialog(
  onContinue: () -> Unit,
  onReturnHome: () -> Unit,
  onConfirmExit: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onContinue,
    containerColor = ForestSlate,
    titleContentColor = ParchmentInk,
    textContentColor = MutedAsh,
    shape = RoundedCornerShape(18.dp),
    title = {
      Text(
        text = stringResource(R.string.exit_dialog_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Text(
        text = stringResource(R.string.exit_dialog_message),
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onReturnHome,
          modifier = Modifier.testTag("exit_home_button"),
          border = BorderStroke(1.dp, BorderMoss),
        ) {
          Text(
            text = stringResource(R.string.exit_dialog_home),
            color = ParchmentInk,
          )
        }
        Button(
          onClick = onConfirmExit,
          modifier = Modifier.testTag("exit_confirm_button"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = SignalRed,
              contentColor = ParchmentInk,
            ),
        ) {
          Text(text = stringResource(R.string.exit_dialog_confirm))
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onContinue,
        modifier = Modifier.testTag("exit_cancel_button"),
      ) {
        Text(
          text = stringResource(R.string.exit_dialog_cancel),
          color = EmberGlow,
        )
      }
    },
  )
}

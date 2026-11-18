package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LauncherUiState(
  val keepScreenOn: Boolean = true,
  val immersiveMode: Boolean = true,
  val inGame: Boolean = false,
  val isGameLoading: Boolean = false,
  val showExitDialog: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
  private val prefs =
    application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _uiState =
    MutableStateFlow(
      LauncherUiState(
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
        immersiveMode = prefs.getBoolean(KEY_IMMERSIVE_MODE, true),
      )
    )
  val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

  fun setKeepScreenOn(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    _uiState.update { it.copy(keepScreenOn = enabled) }
  }

  fun setImmersiveMode(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_IMMERSIVE_MODE, enabled).apply()
    _uiState.update { it.copy(immersiveMode = enabled) }
  }

  fun startGame() {
    _uiState.update {
      it.copy(
        inGame = true,
        isGameLoading = true,
        showExitDialog = false,
      )
    }
  }

  fun onGameLoaded() {
    _uiState.update { it.copy(isGameLoading = false) }
  }

  fun requestExitPrompt() {
    _uiState.update { it.copy(showExitDialog = true) }
  }

  fun dismissExitPrompt() {
    _uiState.update { it.copy(showExitDialog = false) }
  }

  fun returnToLauncher() {
    _uiState.update {
      it.copy(
        inGame = false,
        isGameLoading = false,
        showExitDialog = false,
      )
    }
  }

  private companion object {
    const val PREFS_NAME = "afterx_launcher_prefs"
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    const val KEY_IMMERSIVE_MODE = "immersive_mode"
  }
}

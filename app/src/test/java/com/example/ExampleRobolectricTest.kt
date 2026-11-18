package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name and bundled game assets exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("ما بعد الفناء", context.getString(R.string.app_name))

    val bridgeJs = context.assets.open("www/net-bridge.js").bufferedReader().readText()
    assertTrue(bridgeJs.contains("window"))
    assertTrue(bridgeJs.contains("sendNetworkData"))
    assertTrue(bridgeJs.contains("afterx-"))

    val gameHtml =
      context.assets.open("after-extinction-alpha-1.43_e4f23cc5.html").bufferedReader().readText()
    assertTrue(gameHtml.contains("<title>ما بعد الفناء</title>"))
    assertTrue(gameHtml.contains("Alpha 1.43"))
    assertTrue(gameHtml.contains("window.onNetworkDataReceived"))
  }

  @Test
  fun `launcher viewmodel persists display settings and exit flow`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = LauncherViewModel(app)

    assertTrue(vm.uiState.value.keepScreenOn)
    assertTrue(vm.uiState.value.immersiveMode)
    assertFalse(vm.uiState.value.inGame)

    vm.setKeepScreenOn(false)
    vm.setImmersiveMode(false)
    vm.startGame()
    assertTrue(vm.uiState.value.inGame)
    assertTrue(vm.uiState.value.isGameLoading)

    vm.onGameLoaded()
    assertFalse(vm.uiState.value.isGameLoading)

    vm.requestExitPrompt()
    assertTrue(vm.uiState.value.showExitDialog)

    vm.returnToLauncher()
    assertFalse(vm.uiState.value.inGame)
    assertFalse(vm.uiState.value.showExitDialog)

    val vmReloaded = LauncherViewModel(app)
    assertFalse(vmReloaded.uiState.value.keepScreenOn)
    assertFalse(vmReloaded.uiState.value.immersiveMode)
  }
}

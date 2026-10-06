package com.oblutack.timenote.feature_settings

import androidx.compose.ui.graphics.Color
import com.oblutack.timenote.data.repository.SettingsRepository
import com.oblutack.timenote.feature_settings.presentation.SettingsViewModel
import com.oblutack.timenote.testutil.FakeDataStore
import com.oblutack.timenote.testutil.runAppTest
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {
    private fun newEnv(): Pair<SettingsRepository, SettingsViewModel> {
        val repo = SettingsRepository(FakeDataStore())
        return repo to SettingsViewModel(repo)
    }

    @Test fun togglesAreWrittenToTheRepository() = runAppTest {
        val (repo, vm) = newEnv()
        vm.toggleBackgroundBlur(false)
        vm.toggleHaptics(false)
        vm.toggleMonochromeNodes(false)
        assertEquals(false, repo.enableBackgroundBlurFlow.first())
        assertEquals(false, repo.enableHapticsFlow.first())
        assertEquals(false, repo.useMonochromeNodesFlow.first())
    }

    @Test fun hexColorIsStoredAsOpaqueComposeColor() = runAppTest {
        val (repo, vm) = newEnv()
        vm.addCustomColor("#FF00AA")
        val stored = repo.customColorsFlow.first().single()
        assertEquals(Color(0xFFFF00AA), Color(stored.toULong()))
    }

    @Test fun hexWithoutHashAndLowercaseIsAccepted() = runAppTest {
        val (repo, vm) = newEnv()
        vm.addCustomColor("00ff7f")
        assertEquals(Color(0xFF00FF7F), Color(repo.customColorsFlow.first().single().toULong()))
    }

    @Test fun invalidHexIsIgnored() = runAppTest {
        val (repo, vm) = newEnv()
        vm.addCustomColor("#12")
        vm.addCustomColor("ZZZZZZ")
        vm.addCustomColor("#1234567")
        assertEquals(emptyList(), repo.customColorsFlow.first())
    }

    @Test fun pickedColorsCanBeAddedAndDeleted() = runAppTest {
        val (repo, vm) = newEnv()
        vm.addPickedColor(Color.Red)
        vm.addPickedColor(Color.Blue)
        vm.deleteCustomColor(Color.Red.value.toLong())
        assertEquals(listOf(Color.Blue.value.toLong()), repo.customColorsFlow.first())
    }
}

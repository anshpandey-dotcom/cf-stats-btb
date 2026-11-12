package com.ansh.memyclock

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ansh.memyclock.data.Alarm
import com.ansh.memyclock.data.AlarmStore
import com.ansh.memyclock.data.Repeat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val out = System.getProperty("shots") ?: "build/shots"

    @Before
    fun seed() {
        AlarmStore.init(ApplicationProvider.getApplicationContext())
        if (AlarmStore.alarms.value.isEmpty()) {
            AlarmStore.save(Alarm(hour = 5, minute = 0, repeat = Repeat.DAYS, days = setOf(6, 7), label = "Join Meeting"))
            AlarmStore.save(Alarm(hour = 7, minute = 15, repeat = Repeat.DAYS, days = setOf(6), label = "Click Photos"))
            AlarmStore.save(Alarm(hour = 16, minute = 0, repeat = Repeat.DAILY, enabled = false))
            AlarmStore.save(Alarm(hour = 17, minute = 0, repeat = Repeat.DAILY, label = "School Time!"))
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("$out/$name.png")
    }

    @Test
    fun alarmList() {
        shot("01_alarms")
        rule.onNodeWithText("Join Meeting", substring = true).performClick()
        shot("02_quick_edit")
        rule.onNodeWithText("Additional settings").performClick()
        shot("03_editor")
        rule.onNodeWithText("Speak a voice message").performScrollTo().performClick()
        rule.onNodeWithText("Preview alarm").performScrollTo()
        shot("04_editor_voice")
        rule.onNodeWithText("Repeat").performScrollTo().performClick()
        shot("05_repeat")
    }

    @Test
    fun ringtonePicker() {
        rule.onNodeWithContentDescription("Add alarm").performClick()
        shot("06_new_alarm")
        rule.onNodeWithText("Ringtone").performClick()
        shot("07_ringtone")
    }

    @Test
    fun otherTabs() {
        rule.onNodeWithText("World clock").performClick()
        shot("08_world")
        rule.onNodeWithText("Stopwatch").performClick()
        shot("09_stopwatch")
        rule.onNodeWithText("Timer").performClick()
        shot("10_timer")
        rule.onNodeWithText("Alarm").performClick()
        rule.onNodeWithContentDescription("Settings").performClick()
        shot("11_settings")
    }
}

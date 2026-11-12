package com.ansh.memyclock

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ansh.memyclock.alarm.AlarmService
import com.ansh.memyclock.alarm.RingActivity
import com.ansh.memyclock.alarm.RingKind
import com.ansh.memyclock.alarm.Ringing
import com.ansh.memyclock.data.Alarm
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class RingTest {
    private val compose = createAndroidComposeRule<RingActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            AlarmService.ringing.value = Ringing(
                Alarm(id = 1, hour = 5, label = "Join Meeting", voiceOn = true, spoken = "उठो भाई, मीटिंग जॉइन करनी है"),
                RingKind.ALARM
            )
        }
    }).around(compose)

    @Test
    fun ringScreen() {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Dismiss").assertExists()
        compose.onNodeWithText("Snooze 5 min").assertExists()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/shots/12_ring.png")
    }
}

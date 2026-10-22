package com.findora.app.feature.viewing.ui

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Viewing
import com.findora.app.feature.viewing.ScheduleViewingScreen
import com.findora.app.feature.viewing.ScheduleViewingViewModel
import com.findora.app.feature.viewing.ViewingTestEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp", application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScheduleViewingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = ViewingTestEnv()
    private var backs = 0
    private var dones = 0
    private var viewingLists = 0
    private var signUps = 0

    @Before
    fun bookTomorrowsFirstSlotForSomeoneElse() {
        val tomorrowNine =
            LocalDate
                .of(2026, 10, 27)
                .atTime(9, 0)
                .atZone(ViewingTestEnv.TUNIS)
                .toInstant()
        env.viewings.add(Viewing(0, "someone-else", env.georgie.id, tomorrowNine, 30, "", env.now))
    }

    private fun show(
        vm: ScheduleViewingViewModel = env.scheduleViewModel(),
        darkTheme: Boolean = false,
    ) {
        compose.setContent {
            FindoraTheme(darkTheme = darkTheme) {
                val state by vm.state.collectAsState()
                ScheduleViewingScreen(
                    state = state,
                    onEvent = vm::onEvent,
                    onBack = { backs++ },
                    onDone = { dones++ },
                    onOpenViewings = { viewingLists++ },
                    onCreateAccount = { signUps++ },
                )
            }
        }
    }

    private fun descriptionContains(text: String) =
        SemanticsMatcher("content description contains $text") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty().any {
                it.contains(text)
            }
        }

    @Test
    fun pickADayAndATimeThenConfirm() {
        show()
        compose.onNodeWithText("Schedule a viewing").assertIsDisplayed().assert(isHeading())
        compose.onNodeWithText(env.georgie.title).assertIsDisplayed()
        compose.onNodeWithTag("scheduleConfirm").assertIsNotEnabled()
        compose.onNodeWithText("Select a time").assertIsDisplayed()

        compose.onNodeWithTag("day_1").performClick().assertIsSelected()
        // Tomorrow 09:00 was booked by someone else: shown, announced and disabled.
        compose.onNodeWithTag("slot_0").assertIsNotEnabled().assert(descriptionContains("already booked"))
        compose
            .onNodeWithTag("slot_1")
            .assertIsEnabled()
            .performClick()
            .assertIsSelected()
        compose.onNodeWithTag("scheduleNote").performScrollTo().performTextInput("Ring twice")
        compose.onNodeWithTag("scheduleConfirm").assertIsEnabled().performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasTestTag("scheduleBooked")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Viewing booked").assertIsDisplayed()
        compose.onNodeWithText("We'll remind you an hour before.").assertIsDisplayed()
        val mine =
            env.viewings.all.value
                .last()
        assertEquals("guest", mine.userId)
        assertEquals("Ring twice", mine.note)
        assertEquals(1, env.reminders.scheduled.size)

        compose.onNodeWithTag("bookedMyViewings").performScrollTo().performClick()
        compose.onNodeWithTag("bookedDone").performScrollTo().performClick()
        compose.onNodeWithTag("bookedAddToCalendar").assertIsDisplayed()
        assertEquals(1, viewingLists)
        assertEquals(1, dones)
    }

    @Test
    fun guestsAreOfferedAnAccountAndCanGoBack() {
        show()
        compose.onNodeWithTag("scheduleGuestNote").assertIsDisplayed()
        compose.onNodeWithText("Sign Up").performClick()
        compose.onNodeWithTag("viewingBack").performClick()
        assertEquals(1, signUps)
        assertEquals(1, backs)
    }

    @Test
    fun signedInUsersSeeNoGuestNote() {
        env.auth.signIn()
        show(darkTheme = true)
        compose.onNodeWithTag("scheduleConfirm").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("scheduleGuestNote")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "ar-w411dp-h2400dp")
    fun rendersInArabicRightToLeft() {
        show()
        compose.onNodeWithText("حجز معاينة").assertIsDisplayed()
        compose.onNodeWithText("اختر يومًا").assertIsDisplayed()
        compose.onNodeWithTag("day_0").assertIsSelected()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

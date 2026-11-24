package io.wit117.thoughtweave.feature.schedule

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wit117.thoughtweave.MainActivity
import io.wit117.thoughtweave.R
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S04, POP02 and manual POP03 through the real app and database. */
@RunWith(AndroidJUnit4::class)
class ScheduleFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int) = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
    }

    private fun click(@StringRes id: Int) =
        rule.onNode(hasText(str(id)) and hasClickAction()).performClick()

    private fun titleField() = rule.onAllNodes(hasSetTextAction())[0]

    private fun openNewSchedule(title: String) {
        click(R.string.nav_schedules)
        waitFor(hasText(str(R.string.fab_add_schedule)) and hasClickAction())
        click(R.string.fab_add_schedule)
        waitFor(hasSetTextAction())
        titleField().performTextInput(title)
    }

    /** The start field comes first, so the second date button belongs to the deadline. */
    private fun pickTodayAsDeadline() {
        rule.onAllNodes(hasText(str(R.string.schedule_pick_date)) and hasClickAction())[1].performClick()
        waitFor(hasText(str(R.string.action_ok)) and hasClickAction())
        click(R.string.action_ok)
        waitUntilGone(hasText(str(R.string.action_ok)) and hasClickAction())
    }

    /** The all-day switch carries its label, so TalkBack names it, and tapping the label toggles it. */
    @Test
    fun allDaySwitchIsLabelled() {
        openNewSchedule("schedule-${UUID.randomUUID()}")
        val allDay = rule.onNode(hasText(str(R.string.schedule_all_day)) and isToggleable())
        allDay.assertIsOff()
        allDay.performClick()
        allDay.assertIsOn()
    }

    @Test
    fun createEditAndDelete() {
        val title = "schedule-${UUID.randomUUID()}"
        val edited = "edited-${UUID.randomUUID()}"
        openNewSchedule(title)
        pickTodayAsDeadline()
        click(R.string.action_save)
        waitFor(hasText(title) and hasClickAction())

        rule.onNode(hasText(title) and hasClickAction()).performClick()
        waitFor(hasText(title) and hasSetTextAction())
        titleField().performTextReplacement(edited)
        click(R.string.action_save)
        waitFor(hasText(edited) and hasClickAction())

        rule.onNode(hasText(edited) and hasClickAction()).performClick()
        waitFor(hasText(edited) and hasSetTextAction())
        rule.onNodeWithContentDescription(str(R.string.action_delete)).performClick()
        waitFor(hasText(str(R.string.dialog_delete_schedule_title)))
        click(R.string.action_delete)
        waitFor(hasText(str(R.string.fab_add_schedule)) and hasClickAction())
        waitUntilGone(hasText(edited))
    }

    @Test
    fun missingDeadlineBlocksSaveAndShowsError() {
        openNewSchedule("schedule-${UUID.randomUUID()}")

        click(R.string.action_save)

        waitFor(hasText(str(R.string.schedule_error_deadline_required)))
    }

    @Test
    fun unsavedScheduleAsksBeforeLeaving() {
        openNewSchedule("schedule-${UUID.randomUUID()}")

        rule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        waitFor(hasText(str(R.string.dialog_discard_title)))
        click(R.string.action_discard)

        waitFor(hasText(str(R.string.fab_add_schedule)) and hasClickAction())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

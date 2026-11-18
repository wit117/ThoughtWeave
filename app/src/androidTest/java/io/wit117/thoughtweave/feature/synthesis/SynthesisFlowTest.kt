package io.wit117.thoughtweave.feature.synthesis

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.wit117.thoughtweave.MainActivity
import io.wit117.thoughtweave.R
import java.util.UUID
import io.wit117.thoughtweave.feature.aimodel.ModelStateRule
import io.wit117.thoughtweave.feature.aimodel.setAgreementAccepted
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S03, POP01 and S02-C through the real app, using the debug fake summary. */
@RunWith(AndroidJUnit4::class)
class SynthesisFlowTest {
    @get:Rule(order = 0)
    val modelRule = ModelStateRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = setAgreementAccepted(true)

    private fun str(@StringRes id: Int) = rule.activity.getString(id)

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun clickable(@StringRes id: Int) = rule.onNode(hasText(str(id)) and hasClickAction())

    private fun createMemo(content: String? = null): String {
        val title = "memo-${UUID.randomUUID()}"
        clickable(R.string.fab_new_memo).performClick()
        waitFor(hasSetTextAction())
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(title)
        rule.onAllNodes(hasSetTextAction())[1].performTextInput(content ?: "content of $title")
        clickable(R.string.action_save).performClick()
        // The editor's title field also holds this text, so exclude it to wait for the list.
        waitFor(hasText(title) and !hasSetTextAction())
        return title
    }

    /** Creates two memos, selects them on S03 and returns their titles. */
    private fun selectTwoMemos(): Pair<String, String> {
        val first = createMemo()
        val second = createMemo()
        clickable(R.string.nav_ai_merge).performClick()
        waitFor(hasText(first))
        clickable(R.string.fab_ai_synthesize).assertIsNotEnabled()
        rule.onNode(hasText(first)).performClick()
        rule.onNode(hasText(second)).performClick()
        rule.waitForIdle()
        return first to second
    }

    private fun waitForSummary() {
        waitFor(hasText("DEBUG FAKE", substring = true))
        waitFor(hasText(str(R.string.action_save)) and hasClickAction() and isEnabled())
    }

    @Test
    fun synthesizeSavesNewMemoAndClearsSelection() {
        val (first, _) = selectTwoMemos()
        rule.onNode(hasText(first)).assertIsSelected()
        clickable(R.string.fab_ai_synthesize).assertIsEnabled().performClick()
        waitForSummary()

        val resultTitle = "result-${UUID.randomUUID()}"
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(resultTitle)
        clickable(R.string.action_save).performClick()

        waitFor(hasText(str(R.string.title_memo_list)))
        waitFor(hasText(resultTitle))

        clickable(R.string.nav_ai_merge).performClick()
        waitFor(hasText(first))
        rule.onNode(hasText(first)).assertIsNotSelected()
        clickable(R.string.fab_ai_synthesize).assertIsNotEnabled()
    }

    @Test
    fun leavingResultKeepsSelection() {
        val (first, _) = selectTwoMemos()
        clickable(R.string.fab_ai_synthesize).performClick()
        waitForSummary()

        rule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        waitFor(hasText(str(R.string.dialog_discard_title)))
        clickable(R.string.action_discard).performClick()

        waitFor(hasText(first))
        rule.onNode(hasText(first)).assertIsSelected()
        clickable(R.string.fab_ai_synthesize).assertIsEnabled()
    }

    /** The fake summary keeps none of the memos' facts, so S02-C lists them as missing. */
    @Test
    fun summaryMissingAmountAndTimeIsFlagged() {
        val first = createMemo("예산은 1인당 15만원")
        val second = createMemo("오후 3시 회의")
        clickable(R.string.nav_ai_merge).performClick()
        waitFor(hasText(first))
        rule.onNode(hasText(first)).performClick()
        rule.onNode(hasText(second)).performClick()
        clickable(R.string.fab_ai_synthesize).performClick()
        waitForSummary()

        // The notice, not the content field, whose original-items list also holds these facts.
        val prefix = str(R.string.summary_facts_missing).substringBefore("%1\$s")
        val notice = hasText(prefix, substring = true) and !hasSetTextAction()
        waitFor(notice and hasText("15만원", substring = true) and hasText("오후 3시", substring = true))
    }

    @Test
    fun longPressPreviewLeavesSelectionUntouched() {
        val (first, second) = selectTwoMemos()
        rule.onNode(hasText(first)).performClick()
        rule.onNode(hasText(first)).assertIsNotSelected()

        rule.onNode(hasText(first)).performTouchInput { longClick() }
        val content = "content of $first"
        rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText(content)).fetchSemanticsNodes().size == 2 }

        Espresso.pressBack()
        rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText(content)).fetchSemanticsNodes().size == 1 }
        assertEquals(1, rule.onAllNodes(hasText(content)).fetchSemanticsNodes().size)
        rule.onNode(hasText(first)).assertIsNotSelected()
        rule.onNode(hasText(second)).assertIsSelected()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

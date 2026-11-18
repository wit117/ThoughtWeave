package io.wit117.thoughtweave.feature.synthesis

import androidx.lifecycle.SavedStateHandle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import io.wit117.thoughtweave.domain.model.AgreementStatus
import io.wit117.thoughtweave.domain.model.AiLanguagePlan
import io.wit117.thoughtweave.testFactCheck
import io.wit117.thoughtweave.testLanguageResolver
import io.wit117.thoughtweave.domain.model.AiModelStatus
import io.wit117.thoughtweave.domain.model.KeyFact
import io.wit117.thoughtweave.domain.model.Memo
import io.wit117.thoughtweave.domain.repository.AgreementRepository
import io.wit117.thoughtweave.domain.repository.AiModelRepository
import io.wit117.thoughtweave.domain.repository.MemoSynthesisException
import io.wit117.thoughtweave.domain.repository.MemoSynthesisRepository
import io.wit117.thoughtweave.domain.usecase.AiAvailabilityUseCase
import io.wit117.thoughtweave.feature.memo.FakeMemoRepository
import io.wit117.thoughtweave.feature.memo.editor.MemoDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSynthesis(
    var result: () -> Flow<String> = { flowOf("sum", "mary") },
) : MemoSynthesisRepository {
    var received: List<Memo>? = null

    override fun summarize(memos: List<Memo>, languages: AiLanguagePlan): Flow<String> {
        received = memos
        return result()
    }
}

private class FakeAgreement : AgreementRepository {
    val status = MutableStateFlow(AgreementStatus.ACCEPTED)
    override fun currentStatus() = status.value
    override fun observeStatus(): Flow<AgreementStatus> = status
    override fun accept() {
        status.value = AgreementStatus.ACCEPTED
    }

    override fun withdraw() {
        status.value = AgreementStatus.NOT_ACCEPTED
    }
}

private class FakeAiModel : AiModelRepository {
    override val status = MutableStateFlow<AiModelStatus>(AiModelStatus.Ready)
    override fun refresh() = Unit
    override fun retry() = Unit
    override fun redownload() = Unit
    override fun showConfirmation(launcher: ActivityResultLauncher<IntentSenderRequest>) = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class SynthesisViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val memos = FakeMemoRepository()
    private val handle = SavedStateHandle()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val agreement = FakeAgreement()

    private val model = FakeAiModel()

    private fun availability() = AiAvailabilityUseCase(agreement, model)

    private fun selection() = SynthesisViewModel(memos, availability(), agreement, handle)

    private fun SynthesisViewModel.loaded() = uiState.value as SynthesisUiState.Loaded

    private fun result(ids: List<Long>, synthesis: MemoSynthesisRepository = FakeSynthesis()) =
        SynthesisResultViewModel(ids, memos, synthesis, testLanguageResolver(), testFactCheck(), handle)

    @Test
    fun needsTwoSelectedMemosAndAi() = runTest(dispatcher) {
        val a = memos.add("a")
        val b = memos.add("b")
        val viewModel = selection()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }

        viewModel.toggleSelection(a.id)
        assertFalse(viewModel.loaded().canSynthesize)
        viewModel.toggleSelection(b.id)
        assertTrue(viewModel.loaded().canSynthesize)
        assertEquals(listOf(a.id, b.id), viewModel.loaded().selectedIds)

        viewModel.toggleSelection(a.id)
        assertEquals(listOf(b.id), viewModel.loaded().selectedIds)
    }

    @Test
    fun consentGatesActionAndAcceptingEnablesIt() = runTest(dispatcher) {
        val a = memos.add("a")
        val b = memos.add("b")
        agreement.withdraw()
        val viewModel = selection()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        viewModel.toggleSelection(a.id)
        viewModel.toggleSelection(b.id)
        assertFalse(viewModel.loaded().aiAvailability.consentGiven)
        assertFalse(viewModel.loaded().canSynthesize)

        viewModel.acceptAgreement()

        assertTrue(viewModel.loaded().aiAvailability.isReady)
        assertTrue(viewModel.loaded().canSynthesize)
    }

    @Test
    fun modelDownloadAndConsentAreIndependent() {
        agreement.withdraw()
        model.status.value = AiModelStatus.Downloading(40)

        val availability = availability().current()

        assertEquals(AiModelStatus.Downloading(40), availability.model)
        assertFalse(availability.consentGiven)
        assertFalse(availability.isModelUsable)
        assertFalse(availability.isReady)
    }

    @Test
    fun modelNotReadyDisablesActionEvenWithConsent() = runTest(dispatcher) {
        val a = memos.add("a")
        val b = memos.add("b")
        model.status.value = AiModelStatus.NotDownloaded
        val viewModel = selection()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        viewModel.toggleSelection(a.id)
        viewModel.toggleSelection(b.id)

        assertFalse(viewModel.loaded().canSynthesize)
    }

    @Test
    fun deletedMemoLeavesSelection_andClearEmptiesIt() = runTest(dispatcher) {
        val a = memos.add("a")
        val b = memos.add("b")
        val viewModel = selection()
        backgroundScope.launch(dispatcher) { viewModel.uiState.collect {} }
        viewModel.toggleSelection(a.id)
        viewModel.toggleSelection(b.id)

        memos.deleteMemo(a.id)
        assertEquals(listOf(b.id), viewModel.loaded().selectedIds)

        viewModel.clearSelection()
        assertTrue(viewModel.loaded().selectedIds.isEmpty())
    }

    @Test
    fun streamsChunksThenBecomesEditable() {
        val chunks = Channel<String>(Channel.UNLIMITED)
        val a = memos.add("a")
        val viewModel = result(listOf(a.id), FakeSynthesis(result = { chunks.receiveAsFlow() }))

        chunks.trySend("sum")
        assertEquals(SynthesisPhase.GENERATING, viewModel.uiState.value.phase)
        assertEquals("sum", viewModel.uiState.value.draft.content)
        assertFalse(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.canSave)

        chunks.trySend("mary")
        chunks.close()
        assertEquals(SynthesisPhase.READY, viewModel.uiState.value.phase)
        assertEquals("summary", viewModel.uiState.value.draft.content)
        assertTrue(viewModel.uiState.value.isDirty)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun passesSourcesInSelectionOrder() {
        val a = memos.add("a")
        val b = memos.add("b")
        val synthesis = FakeSynthesis()

        result(listOf(b.id, a.id), synthesis)

        assertEquals(listOf(b.id, a.id), synthesis.received?.map { it.id })
    }

    @Test
    fun saveStoresEditedResultWithSources() = runTest(dispatcher) {
        val a = memos.add("a")
        val b = memos.add("b")
        val viewModel = result(listOf(a.id, b.id))
        viewModel.onTitleChange("title")
        viewModel.onContentChange("edited")

        viewModel.save()

        assertEquals(SynthesisResultEffect.Saved, viewModel.effects.first())
        assertEquals(listOf(a.id, b.id), memos.lastSynthesisSources)
        assertEquals("edited", memos.memos.value.last().content)
    }

    @Test
    fun saveFailureKeepsResult() = runTest(dispatcher) {
        val a = memos.add("a")
        val viewModel = result(listOf(a.id))
        memos.failWrites = true

        viewModel.save()

        assertEquals(SynthesisResultEffect.SaveFailed, viewModel.effects.first())
        assertEquals("summary", viewModel.uiState.value.draft.content)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun generationFailureCanBeRetried() {
        val a = memos.add("a")
        val synthesis = FakeSynthesis(result = { flow { throw MemoSynthesisException("failed") } })
        val viewModel = result(listOf(a.id), synthesis)
        assertEquals(SynthesisPhase.FAILED, viewModel.uiState.value.phase)

        synthesis.result = { flowOf("ok") }
        viewModel.retry()

        assertEquals(SynthesisPhase.READY, viewModel.uiState.value.phase)
        assertEquals("ok", viewModel.uiState.value.draft.content)
    }

    @Test
    fun blankResultAndMissingSourceAreFailures() {
        val a = memos.add("a")
        assertEquals(
            SynthesisPhase.FAILED,
            result(listOf(a.id), FakeSynthesis(result = { flowOf("  ") })).uiState.value.phase,
        )
        assertEquals(SynthesisPhase.FAILED, result(listOf(99)).uiState.value.phase)
    }

    @Test
    fun finishedResultIsRestoredWithoutRegenerating() {
        val a = memos.add("a")
        result(listOf(a.id)).onContentChange("edited")
        val synthesis = FakeSynthesis()

        val restored = result(listOf(a.id), synthesis).uiState.value

        assertEquals(SynthesisPhase.READY, restored.phase)
        assertEquals(MemoDraft("", "edited"), restored.draft)
        assertEquals(null, synthesis.received)
    }

    @Test
    fun changedDateIsFlaggedAndClearsOnceFixed() {
        // The reported case: "12.2" became "12일 후" in the summary. Today is 2026-11-09 (testFactCheck).
        val a = memos.add("할 일", "12.2까지 기획서 제출")
        val b = memos.add("예산", "총 예산 1200만원")
        val viewModel = result(listOf(a.id, b.id), FakeSynthesis(result = { flowOf("기획서는 12일 후 제출, 예산 1200만원") }))

        val check = viewModel.uiState.value.factCheck
        assertEquals(listOf("12.2"), check?.missing?.map { it.wording })
        assertTrue(check!!.added.isEmpty())

        viewModel.onContentChange("기획서는 12월 2일까지 제출, 예산 1200만원")

        assertEquals(null, viewModel.uiState.value.factCheck)
    }

    @Test
    fun dateTurnedIntoAWeekdayIsFlaggedBothWays() {
        // "11.25" became "다음 주 수요일" (2026-11-18 from 2026-11-09), so one date is missing and one added.
        val a = memos.add("회의", "11.25 오후 2시 회의")
        val b = memos.add("메모", "장소는 3층")
        val viewModel = result(listOf(a.id, b.id), FakeSynthesis(result = { flowOf("다음 주 수요일 오후 2시 3층 회의") }))

        val check = viewModel.uiState.value.factCheck!!
        assertEquals(listOf(KeyFact.Date(java.time.LocalDate.of(2026, 11, 25))), check.missing.map { it.fact })
        assertEquals(listOf(KeyFact.Date(java.time.LocalDate.of(2026, 11, 18))), check.added.map { it.fact })
        assertEquals(listOf("다음 주 수요일"), check.added.map { it.wording })
    }

    @Test
    fun droppedTimeAndAmountAreFlagged() {
        // Benchmark ko-4 and ko-1: the time and the budget were left out of the summary.
        val a = memos.add("치과", "12월 9일(수) 오후 3시 치과 예약")
        val b = memos.add("예산", "예산은 1인당 15만원")
        val viewModel = result(listOf(a.id, b.id), FakeSynthesis(result = { flowOf("12월 9일(수) 치과 예약, 1인당 예산") }))

        assertEquals(listOf("오후 3시", "15만원"), viewModel.uiState.value.factCheck?.missing?.map { it.wording })

        viewModel.onContentChange("12월 9일(수) 15:00 치과 예약, 1인당 예산 150,000원")

        assertEquals(null, viewModel.uiState.value.factCheck)
    }

    @Test
    fun sameDateInAnotherFormatIsNotFlagged() {
        val a = memos.add("발표", "12.2 발표")
        val b = memos.add("장소", "본관")
        val viewModel = result(listOf(a.id, b.id), FakeSynthesis(result = { flowOf("12월 2일 본관에서 발표") }))

        assertEquals(null, viewModel.uiState.value.factCheck)
    }
}

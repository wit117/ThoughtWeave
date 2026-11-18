package io.wit117.thoughtweave.feature.synthesis

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.wit117.thoughtweave.domain.model.AppLanguage
import io.wit117.thoughtweave.domain.model.Memo
import io.wit117.thoughtweave.domain.usecase.CheckSummaryFactsUseCase
import io.wit117.thoughtweave.domain.usecase.ResolveAiLanguageUseCase
import io.wit117.thoughtweave.domain.usecase.SummaryFactCheck
import io.wit117.thoughtweave.domain.repository.MemoRepository
import io.wit117.thoughtweave.domain.repository.MemoSynthesisException
import io.wit117.thoughtweave.domain.repository.MemoSynthesisRepository
import io.wit117.thoughtweave.feature.memo.editor.MemoDraft
import io.wit117.thoughtweave.feature.memo.editor.MemoDraftStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SynthesisPhase { GENERATING, READY, FAILED }

data class SynthesisResultUiState(
    val phase: SynthesisPhase = SynthesisPhase.GENERATING,
    val draft: MemoDraft = MemoDraft(),
    val isBusy: Boolean = false,
    val isInputTooLong: Boolean = false,
    /** Set when the chosen output language could not be used: (written in, requested). */
    val untranslated: Pair<AppLanguage, AppLanguage>? = null,
    /** Dates, times, amounts and counts that differ between the sources and the summary; updated as the user edits. */
    val factCheck: SummaryFactCheck? = null,
) {
    /** A finished summary exists only in memory until saved, so leaving must be confirmed. */
    val isDirty: Boolean get() = phase == SynthesisPhase.READY
    val canSave: Boolean get() = phase == SynthesisPhase.READY && !isBusy && draft.hasContent
}

sealed interface SynthesisResultEffect {
    data object Saved : SynthesisResultEffect
    data object SaveFailed : SynthesisResultEffect
}

/** S02-C – AI 합성 결과 편집. */
@HiltViewModel(assistedFactory = SynthesisResultViewModel.Factory::class)
class SynthesisResultViewModel @AssistedInject constructor(
    @Assisted private val sourceMemoIds: List<Long>,
    private val memoRepository: MemoRepository,
    private val synthesisRepository: MemoSynthesisRepository,
    private val resolveLanguage: ResolveAiLanguageUseCase,
    private val checkFacts: CheckSummaryFactsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sourceMemoIds: List<Long>): SynthesisResultViewModel
    }

    private val draftStore = MemoDraftStore(savedStateHandle)

    /** Kept to re-check dates as the user edits. Absent after process death, which skips the check. */
    private var sources: List<Memo> = emptyList()
    private var summaryLanguage: AppLanguage? = null

    private val _uiState = MutableStateFlow(SynthesisResultUiState())
    val uiState: StateFlow<SynthesisResultUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<SynthesisResultEffect>(Channel.BUFFERED)
    val effects: Flow<SynthesisResultEffect> = effectChannel.receiveAsFlow()

    init {
        val restored = draftStore.restore()
        if (restored != null) {
            _uiState.value = SynthesisResultUiState(SynthesisPhase.READY, restored)
        } else {
            generate()
        }
    }

    fun retry() {
        if (_uiState.value.phase == SynthesisPhase.FAILED) generate()
    }

    private fun generate() {
        _uiState.value = SynthesisResultUiState(SynthesisPhase.GENERATING)
        viewModelScope.launch {
            var isTooLong = false
            val succeeded = try {
                streamSummary(loadSources())
                _uiState.value.draft.content.isNotBlank()
            } catch (e: MemoSynthesisException) {
                isTooLong = e.isInputTooLong
                false
            } catch (_: SQLiteException) {
                false
            }
            if (succeeded) {
                _uiState.update { it.copy(phase = SynthesisPhase.READY, factCheck = factCheckFor(it.draft)) }
                draftStore.persist(_uiState.value.draft)
            } else {
                _uiState.value = SynthesisResultUiState(SynthesisPhase.FAILED, isInputTooLong = isTooLong)
            }
        }
    }

    private suspend fun loadSources(): List<Memo> = sourceMemoIds.map { id ->
        memoRepository.getMemo(id) ?: throw MemoSynthesisException("Source memo $id no longer exists")
    }

    private fun factCheckFor(draft: MemoDraft): SummaryFactCheck? {
        val language = summaryLanguage ?: return null
        // Only what the AI wrote: the original-items list repeats every source fact.
        val aiText = synthesisRepository.aiPart(draft.content, language)
        return checkFacts(sources, draft.title + "\n" + aiText, language).takeIf { it.hasProblems }
    }

    private suspend fun streamSummary(sources: List<Memo>) {
        val languages = resolveLanguage(sources.joinToString("\n") { it.title + "\n" + it.content })
        this.sources = sources
        summaryLanguage = languages.output
        _uiState.update { state ->
            state.copy(untranslated = languages.unavailableOutput?.let { languages.input to it })
        }
        synthesisRepository.summarize(sources, languages).collect { chunk ->
            _uiState.update { it.copy(draft = it.draft.copy(content = it.draft.content + chunk)) }
        }
    }

    fun onTitleChange(title: String) = updateDraft { it.copy(title = title) }

    fun onContentChange(content: String) = updateDraft { it.copy(content = content) }

    private fun updateDraft(transform: (MemoDraft) -> MemoDraft) {
        if (_uiState.value.phase != SynthesisPhase.READY) return
        _uiState.update {
            val draft = transform(it.draft)
            it.copy(draft = draft, factCheck = factCheckFor(draft))
        }
        draftStore.persist(_uiState.value.draft)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val effect = try {
                memoRepository.createSynthesizedMemo(
                    title = state.draft.title,
                    content = state.draft.content,
                    sourceMemoIds = sourceMemoIds,
                )
                draftStore.clear()
                SynthesisResultEffect.Saved
            } catch (_: IllegalArgumentException) {
                SynthesisResultEffect.SaveFailed
            } catch (_: SQLiteException) {
                SynthesisResultEffect.SaveFailed
            }
            _uiState.update { it.copy(isBusy = false) }
            effectChannel.send(effect)
        }
    }
}

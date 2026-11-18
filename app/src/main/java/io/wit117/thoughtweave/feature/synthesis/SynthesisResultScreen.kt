package io.wit117.thoughtweave.feature.synthesis

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wit117.thoughtweave.R
import io.wit117.thoughtweave.core.ui.component.ConfirmDialog
import io.wit117.thoughtweave.feature.memo.editor.MemoEditorContent
import kotlinx.coroutines.launch

/** S02-C – AI 합성 결과 편집. Source memos are stored with the result but never shown. */
@Composable
fun SynthesisResultScreen(
    viewModel: SynthesisResultViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.memo_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SynthesisResultEffect.Saved -> onSaved()
                SynthesisResultEffect.SaveFailed -> launch {
                    snackbarHostState.showSnackbar(saveFailedMessage)
                }
            }
        }
    }

    MemoEditorContent(
        screenTitle = stringResource(R.string.title_ai_result),
        draft = state.draft,
        isLoading = false,
        isDirty = state.isDirty,
        canSave = state.canSave,
        snackbarHostState = snackbarHostState,
        onTitleChange = viewModel::onTitleChange,
        onContentChange = viewModel::onContentChange,
        onSave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
        isReadOnly = state.phase != SynthesisPhase.READY,
        notices = listOfNotNull(
            state.untranslated?.let { (writtenIn, requested) ->
                stringResource(R.string.ai_translation_unavailable, requested.nativeName, writtenIn.nativeName)
            },
            // Quoted as written: the source's wording for what is missing, the summary's for what was added.
            state.factCheck?.missing?.takeIf { it.isNotEmpty() }?.let { facts ->
                stringResource(R.string.summary_facts_missing, facts.joinToString { it.wording })
            },
            state.factCheck?.added?.takeIf { it.isNotEmpty() }?.let { facts ->
                stringResource(R.string.summary_facts_added, facts.joinToString { it.wording })
            },
        ),
        showProgress = state.phase == SynthesisPhase.GENERATING,
    )

    if (state.phase == SynthesisPhase.FAILED) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_synthesis_failed_title),
            message = stringResource(
                if (state.isInputTooLong) {
                    R.string.dialog_synthesis_too_long_message
                } else {
                    R.string.dialog_synthesis_failed_message
                },
            ),
            confirmText = stringResource(R.string.action_retry),
            dismissText = stringResource(R.string.action_close),
            onConfirm = viewModel::retry,
            onDismiss = onBack,
        )
    }
}

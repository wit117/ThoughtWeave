package io.wit117.thoughtweave.feature.schedule.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.wit117.thoughtweave.R
import io.wit117.thoughtweave.core.ui.component.ConfirmDialog
import io.wit117.thoughtweave.core.ui.component.DateTimeField
import io.wit117.thoughtweave.core.ui.component.UnavailableScreen
import io.wit117.thoughtweave.core.ui.component.WordAwareLineBreak
import io.wit117.thoughtweave.domain.usecase.ScheduleValidationError
import kotlinx.coroutines.launch

/** POP02 일정 상세 and POP03 일정 추출 및 생성 (manual mode). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    viewModel: ScheduleEditorViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showExtractionFailed by rememberSaveable { mutableStateOf(false) }

    val saveFailedMessage = stringResource(R.string.schedule_save_failed)
    val deleteFailedMessage = stringResource(R.string.schedule_delete_failed)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ScheduleEditorEffect.Close -> onFinished()
                ScheduleEditorEffect.SaveFailed -> launch { snackbarHostState.showSnackbar(saveFailedMessage) }
                ScheduleEditorEffect.DeleteFailed -> launch { snackbarHostState.showSnackbar(deleteFailedMessage) }
                ScheduleEditorEffect.ExtractionFailed -> showExtractionFailed = true
            }
        }
    }

    val screenTitle = stringResource(if (state.isExisting) R.string.title_schedule else R.string.title_new_schedule)

    if (state.isUnavailable) {
        UnavailableScreen(
            screenTitle = screenTitle,
            title = stringResource(R.string.schedule_unavailable_title),
            message = stringResource(R.string.schedule_unavailable_message),
            onBack = onBack,
        )
        return
    }

    BackHandler(enabled = state.isDirty) { showDiscardDialog = true }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { if (state.isDirty) showDiscardDialog = true else onBack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isExisting && !state.isLoading) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isBusy) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                if (state.isExtracting) Text(stringResource(R.string.schedule_extracting))
            }
        } else {
            ScheduleForm(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
            )
        }
    }

    if (showExtractionFailed) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.dialog_extraction_failed_title)) },
            text = {
                Text(
                    text = stringResource(R.string.dialog_extraction_failed_message),
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = WordAwareLineBreak),
                )
            },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_discard_title),
            message = stringResource(R.string.dialog_discard_message),
            confirmText = stringResource(R.string.action_discard),
            dismissText = stringResource(R.string.action_keep_editing),
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_schedule_title),
            message = stringResource(R.string.dialog_delete_schedule_message),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun ScheduleForm(
    state: ScheduleEditorUiState,
    viewModel: ScheduleEditorViewModel,
    modifier: Modifier,
) {
    val fields = state.fields
    val titleError = ScheduleValidationError.TITLE_REQUIRED in state.errors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = fields.title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_title_label)) },
            singleLine = true,
            isError = titleError,
            supportingText = if (titleError) {
                { Text(stringResource(R.string.schedule_error_title_required)) }
            } else {
                null
            },
        )
        OutlinedTextField(
            value = fields.description,
            onValueChange = viewModel::onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_description_label)) },
            minLines = 3,
        )
        // The whole row is the switch, so TalkBack reads "종일, switch, on" and the label is a touch target too.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = fields.isAllDay, role = Role.Switch, onValueChange = viewModel::onAllDayChange),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.schedule_all_day))
            Switch(checked = fields.isAllDay, onCheckedChange = null)
        }
        DateTimeField(
            label = stringResource(R.string.schedule_start_label),
            date = fields.startDate,
            time = fields.startTime,
            showTime = !fields.isAllDay,
            onDateChange = viewModel::onStartDateChange,
            onTimeChange = viewModel::onStartTimeChange,
            onClear = viewModel::onClearStart,
            errorText = if (ScheduleValidationError.START_AFTER_DEADLINE in state.errors) {
                stringResource(R.string.schedule_error_start_after_deadline)
            } else {
                null
            },
        )
        DateTimeField(
            label = stringResource(R.string.schedule_deadline_label),
            date = fields.endDate,
            time = fields.endTime,
            showTime = !fields.isAllDay,
            onDateChange = viewModel::onEndDateChange,
            onTimeChange = viewModel::onEndTimeChange,
            errorText = if (ScheduleValidationError.DEADLINE_REQUIRED in state.errors) {
                stringResource(R.string.schedule_error_deadline_required)
            } else {
                null
            },
        )
    }
}

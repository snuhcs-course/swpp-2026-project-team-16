package com.example.runtime.ui.mypage

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtime.R
import com.example.runtime.data.remote.RunningStyle
import com.example.runtime.data.repository.CoachRepository
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.common.toUiText
import com.example.runtime.ui.route.RunType
import com.example.runtime.ui.route.formatPaceClock
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val MAX_STYLE_NOTE_LENGTH = 200

enum class RunPurpose(val apiValue: String, @StringRes val label: Int) {
    FITNESS("fitness", R.string.purpose_fitness),
    RACE("race", R.string.purpose_race),
    WEIGHT("weight", R.string.purpose_weight),
}

val COMFORTABLE_PACE_OPTIONS = listOf(330, 360, 390, 420, 450)
val WEEKLY_RUN_OPTIONS = listOf(1, 2, 3, 4, 5, 6)

sealed interface StyleLoadState {
    data object Loading : StyleLoadState
    data object Ready : StyleLoadState
    data class Failed(val message: UiText) : StyleLoadState
}

class RunningStyleViewModel(
    private val repository: CoachRepository = CoachRepository(),
) : ViewModel() {
    var style by mutableStateOf(RunningStyle())
        private set
    var loadState by mutableStateOf<StyleLoadState>(StyleLoadState.Loading)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<UiText?>(null)
        private set

    fun load() {
        loadState = StyleLoadState.Loading
        viewModelScope.launch {
            loadState = try {
                style = repository.runningStyle()
                StyleLoadState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StyleLoadState.Failed(e.toUiText())
            }
        }
    }

    fun togglePurpose(purpose: RunPurpose) {
        style = style.copy(purpose = purpose.apiValue.takeIf { it != style.purpose })
    }

    fun toggleRunType(type: RunType) {
        val types = style.runTypes
        style = style.copy(runTypes = if (type.apiValue in types) types - type.apiValue else types + type.apiValue)
    }

    fun togglePace(seconds: Int) {
        style = style.copy(comfortablePaceSeconds = seconds.takeIf { it != style.comfortablePaceSeconds })
    }

    fun toggleWeeklyRuns(count: Int) {
        style = style.copy(weeklyRuns = count.takeIf { it != style.weeklyRuns })
    }

    fun onNoteChange(text: String) {
        style = style.copy(note = text.take(MAX_STYLE_NOTE_LENGTH))
    }

    fun save(onSaved: () -> Unit) {
        if (isSaving) return
        isSaving = true
        saveError = null
        viewModelScope.launch {
            try {
                style = repository.saveRunningStyle(style.copy(note = style.note.trim()))
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = e.toUiText()
            } finally {
                isSaving = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunningStyleScreen(viewModel: RunningStyleViewModel, onBack: () -> Unit) {
    val messages = LocalAppMessages.current
    val savedMessage = stringResource(R.string.running_style_saved)

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.running_style_title), style = MaterialTheme.typography.titleLarge)
        }

        when (val state = viewModel.loadState) {
            StyleLoadState.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            is StyleLoadState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message.asString(), textAlign = TextAlign.Center)
                TextButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry)) }
            }
            StyleLoadState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                Text(
                    stringResource(R.string.running_style_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val style = viewModel.style
                StyleField(stringResource(R.string.running_style_purpose)) {
                    RunPurpose.entries.forEach { purpose ->
                        StyleChip(stringResource(purpose.label), style.purpose == purpose.apiValue) { viewModel.togglePurpose(purpose) }
                    }
                }
                StyleField(stringResource(R.string.running_style_run_types)) {
                    RunType.entries.forEach { type ->
                        StyleChip(stringResource(type.label), type.apiValue in style.runTypes) { viewModel.toggleRunType(type) }
                    }
                }
                StyleField(stringResource(R.string.running_style_pace)) {
                    COMFORTABLE_PACE_OPTIONS.forEach { seconds ->
                        StyleChip(
                            stringResource(R.string.pace_per_km, formatPaceClock(seconds)),
                            style.comfortablePaceSeconds == seconds,
                        ) { viewModel.togglePace(seconds) }
                    }
                }
                StyleField(stringResource(R.string.running_style_weekly)) {
                    WEEKLY_RUN_OPTIONS.forEach { count ->
                        StyleChip(
                            if (count == WEEKLY_RUN_OPTIONS.last()) stringResource(R.string.weekly_runs_or_more, count)
                            else stringResource(R.string.weekly_runs, count),
                            style.weeklyRuns == count,
                        ) { viewModel.toggleWeeklyRuns(count) }
                    }
                }
                Column {
                    Text(stringResource(R.string.running_style_note), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.s))
                    OutlinedTextField(
                        value = style.note,
                        onValueChange = viewModel::onNoteChange,
                        placeholder = { Text(stringResource(R.string.running_style_note_placeholder)) },
                        supportingText = {
                            Text(
                                "${style.note.length}/$MAX_STYLE_NOTE_LENGTH",
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = doneKeyboardOptions(),
                        keyboardActions = hideKeyboardActions(),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                viewModel.saveError?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                AccentButton(
                    text = stringResource(R.string.action_save),
                    enabled = !viewModel.isSaving,
                    onClick = {
                        viewModel.save {
                            messages?.show(savedMessage)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleField(title: String, chips: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { chips() }
    }
}

@Composable
private fun StyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

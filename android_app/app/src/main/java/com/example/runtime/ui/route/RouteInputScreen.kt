package com.example.runtime.ui.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.GlassSurface
import com.example.runtime.ui.common.LOCATION_PERMISSIONS
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.doneKeyboardOptions
import com.example.runtime.ui.common.hideKeyboardActions
import com.example.runtime.ui.common.currentLocation
import com.example.runtime.ui.common.hasLocationPermission
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.launch

private val SHEET_PEEK_HEIGHT = 148.dp
private val MAP_BOTTOM_PADDING = 340.dp

// 4. Route Input Screen (화면 4)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteInputScreen(routeViewModel: RouteViewModel, onGenerate: () -> Unit, onStartFree: () -> Unit = {}) {
    val language = LocalConfiguration.current.locales[0].language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }
    val currentLocationName = stringResource(R.string.current_location)
    val mapPointName = stringResource(R.string.map_point)
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    LaunchedEffect(language) {
        routeViewModel.onLanguageChange(language)
    }

    fun fillCurrentLocation() {
        isLocating = true
        scope.launch {
            val location = context.currentLocation()
            isLocating = false
            if (location == null) {
                routeViewModel.showError(UiText.Resource(R.string.error_location_unavailable))
            } else {
                routeViewModel.selectPoint(routeViewModel.startField, currentLocationName, location.longitude, location.latitude)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            fillCurrentLocation()
        } else {
            routeViewModel.showError(UiText.Resource(R.string.error_location_permission))
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetShadowElevation = 8.dp,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l)
            ) {
                Text(stringResource(R.string.route_input_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (routeViewModel.guidance != RunGuidance.FREE) {
                    Text(
                        stringResource(R.string.route_input_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.m))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RunGuidance.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = routeViewModel.guidance == option,
                            onClick = { routeViewModel.changeGuidance(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, RunGuidance.entries.size),
                            label = { Text(stringResource(option.label)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.s))
                Text(
                    stringResource(
                        when (routeViewModel.guidance) {
                            RunGuidance.FREE -> R.string.guidance_free_hint
                            RunGuidance.ROUTE -> R.string.guidance_route_hint
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.m))
                if (routeViewModel.guidance == RunGuidance.FREE) {
                    PaceMakerSection(routeViewModel, showPlanBuilder = false)
                    Spacer(modifier = Modifier.height(Spacing.m))
                    AccentButton(
                        text = stringResource(R.string.action_start_free_run),
                        icon = R.drawable.ic_directions_run,
                        onClick = onStartFree,
                        modifier = Modifier.fillMaxWidth()
                    )
                    return@Column
                }
                PlaceSearchField(
                    field = routeViewModel.startField,
                    label = stringResource(R.string.label_start_point),
                    onInputChange = { routeViewModel.onPlaceInputChange(routeViewModel.startField, it) },
                    onSelect = { routeViewModel.selectPlace(routeViewModel.startField, it) },
                    trailingAction = {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = {
                                if (context.hasLocationPermission()) fillCurrentLocation()
                                else permissionLauncher.launch(LOCATION_PERMISSIONS)
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_my_location),
                                    contentDescription = stringResource(R.string.use_current_location)
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                PlaceSearchField(
                    field = routeViewModel.endField,
                    label = stringResource(R.string.label_end_point),
                    onInputChange = { routeViewModel.onPlaceInputChange(routeViewModel.endField, it) },
                    onSelect = { routeViewModel.selectPlace(routeViewModel.endField, it) }
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.label_distance),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = routeViewModel.distanceInput,
                        onValueChange = { routeViewModel.distanceInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        suffix = { Text(stringResource(R.string.unit_km), style = MaterialTheme.typography.titleMedium) },
                        keyboardOptions = doneKeyboardOptions(KeyboardType.Decimal),
                        keyboardActions = hideKeyboardActions(),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.width(140.dp)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.m))
                PaceMakerSection(routeViewModel, showPlanBuilder = true)
                Spacer(modifier = Modifier.height(Spacing.m))
                Text(stringResource(R.string.label_preferences), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.preferences_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.s))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    RoutePreference.entries.forEach { preference ->
                        val selected = preference in routeViewModel.preferences
                        FilterChip(
                            selected = selected,
                            onClick = { routeViewModel.togglePreference(preference) },
                            label = { Text(stringResource(preference.label)) },
                            leadingIcon = if (selected) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = routeViewModel.preferenceText,
                    onValueChange = routeViewModel::onPreferenceTextChange,
                    label = { Text(stringResource(R.string.label_preference_text)) },
                    placeholder = { Text(stringResource(R.string.preference_text_placeholder)) },
                    supportingText = {
                        Text(
                            "${routeViewModel.preferenceText.length}/$MAX_PREFERENCE_TEXT_LENGTH",
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = doneKeyboardOptions(),
                    keyboardActions = hideKeyboardActions(),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                routeViewModel.error?.let {
                    Spacer(modifier = Modifier.height(Spacing.s))
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(Spacing.l))
                AccentButton(
                    text = stringResource(R.string.action_generate),
                    icon = R.drawable.ic_directions_run,
                    onClick = {
                        if (routeViewModel.prepare(language)) onGenerate()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlacePickerMap(
                start = routeViewModel.startField.selected,
                end = routeViewModel.endField.selected,
                onPick = { point ->
                    val field = if (routeViewModel.pickTarget == PickTarget.START) routeViewModel.startField else routeViewModel.endField
                    routeViewModel.selectPoint(field, mapPointName, point.longitude, point.latitude)
                },
                contentPadding = PaddingValues(
                    top = 72.dp,
                    bottom = if (scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) MAP_BOTTOM_PADDING else SHEET_PEEK_HEIGHT
                ),
                modifier = Modifier.fillMaxSize()
            )
            if (routeViewModel.guidance != RunGuidance.FREE) MapPickBar(
                target = routeViewModel.pickTarget,
                onTargetChange = { routeViewModel.pickTarget = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(Spacing.m)
            )
        }
    }
}

@Composable
private fun MapPickBar(target: PickTarget, onTargetChange: (PickTarget) -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.s)
        ) {
            Icon(
                painterResource(R.drawable.ic_add_location_alt),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(stringResource(R.string.map_pick_label), style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(Spacing.s))
            FilterChip(
                selected = target == PickTarget.START,
                onClick = { onTargetChange(PickTarget.START) },
                label = { Text(stringResource(R.string.map_pick_start)) },
                shape = MaterialTheme.shapes.extraLarge
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            FilterChip(
                selected = target == PickTarget.END,
                onClick = { onTargetChange(PickTarget.END) },
                label = { Text(stringResource(R.string.map_pick_end)) },
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun PaceMakerSection(routeViewModel: RouteViewModel, showPlanBuilder: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = routeViewModel.paceMakerEnabled,
                onCheckedChange = routeViewModel::onPaceMakerEnabledChange,
            )
            Column {
                Text(stringResource(R.string.pace_maker_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.pace_maker_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!routeViewModel.paceMakerEnabled) return@Column

        Spacer(modifier = Modifier.height(Spacing.s))
        Text(stringResource(R.string.pace_maker_range), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                R.string.pace_range_per_km,
                "${formatPaceClock(routeViewModel.paceMinSeconds)}–${formatPaceClock(routeViewModel.paceMaxSeconds)}",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        RangeSlider(
            value = routeViewModel.paceMinSeconds.toFloat()..routeViewModel.paceMaxSeconds.toFloat(),
            onValueChange = routeViewModel::updatePaceRange,
            valueRange = 120f..600f,
            steps = 47,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            startThumb = { PaceRangeThumb() },
            endThumb = { PaceRangeThumb() },
            track = { state ->
                SliderDefaults.Track(
                    rangeSliderState = state,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            },
        )
        Text(
            stringResource(R.string.pace_maker_range_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        Text(stringResource(R.string.pace_maker_alerts), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(routeViewModel.alertOnSlow, routeViewModel::onAlertOnSlowChange)
                Text(stringResource(R.string.pace_maker_alert_slow), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(routeViewModel.alertOnFast, routeViewModel::onAlertOnFastChange)
                Text(stringResource(R.string.pace_maker_alert_fast), style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = !routeViewModel.alertOnSlow && !routeViewModel.alertOnFast,
                    onCheckedChange = { if (it) {
                        routeViewModel.onAlertOnSlowChange(false)
                        routeViewModel.onAlertOnFastChange(false)
                    } },
                )
                Text(stringResource(R.string.pace_maker_alert_none), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!showPlanBuilder) return@Column
        Spacer(modifier = Modifier.height(Spacing.s))
        var goalFocused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = routeViewModel.goalText,
            onValueChange = routeViewModel::onGoalTextChange,
            label = { Text(stringResource(R.string.pace_maker_segments_label)) },
            placeholder = if (goalFocused && routeViewModel.goalText.isEmpty()) {
                { Text(stringResource(R.string.pace_maker_segments_placeholder)) }
            } else null,
            supportingText = {
                Text(
                    "${routeViewModel.goalText.length}/$MAX_GOAL_TEXT_LENGTH",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = doneKeyboardOptions(),
            keyboardActions = hideKeyboardActions(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().onFocusChanged { goalFocused = it.isFocused },
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        val loading = routeViewModel.planState == PlanState.Loading
        OutlinedButton(
            onClick = routeViewModel::requestPlan,
            enabled = routeViewModel.goalText.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(Spacing.s))
            }
            Text(stringResource(if (routeViewModel.runPlan == null) R.string.goal_make_plan else R.string.goal_remake_plan))
        }
        (routeViewModel.planState as? PlanState.Failed)?.let {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(it.message.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        routeViewModel.runPlan?.let { plan ->
            Spacer(modifier = Modifier.height(Spacing.s))
            RunPlanCard(
                plan = plan,
                title = stringResource(R.string.goal_plan_title),
                onUpdatePaceSegment = routeViewModel::updatePaceSegment,
                onRemovePaceSegment = routeViewModel::removePaceSegment,
                onAddPaceSegment = routeViewModel::addPaceSegment,
                footer = {
                    Text(
                        stringResource(R.string.goal_plan_applied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = routeViewModel::clearPlan, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.goal_clear_plan))
                    }
                },
            )
        }
    }
}

@Composable
private fun PaceRangeThumb() {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
    )
}

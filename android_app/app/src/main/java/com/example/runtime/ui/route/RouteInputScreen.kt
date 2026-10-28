package com.example.runtime.ui.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.GlassSurface
import com.example.runtime.ui.common.LOCATION_PERMISSIONS
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.currentLocation
import com.example.runtime.ui.common.hasLocationPermission
import com.example.runtime.ui.theme.Spacing
import kotlinx.coroutines.launch

private val SHEET_PEEK_HEIGHT = 96.dp
private val MAP_BOTTOM_PADDING = 340.dp

// 4. Route Input Screen (화면 4)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteInputScreen(routeViewModel: RouteViewModel, onGenerate: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }
    val currentLocationName = stringResource(R.string.current_location)
    val mapPointName = stringResource(R.string.map_point)
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Expanded)
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
                Text(
                    stringResource(R.string.route_input_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.l))
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.width(140.dp)
                    )
                }
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
                contentPadding = PaddingValues(top = 72.dp, bottom = MAP_BOTTOM_PADDING),
                modifier = Modifier.fillMaxSize()
            )
            MapPickBar(
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

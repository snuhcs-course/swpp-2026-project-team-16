package com.example.runtime.ui.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.LOCATION_PERMISSIONS
import com.example.runtime.ui.common.UiText
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.currentLocation
import com.example.runtime.ui.common.hasLocationPermission
import kotlinx.coroutines.launch

// 4. Route Input Screen (화면 4)
@Composable
fun RouteInputScreen(routeViewModel: RouteViewModel, onGenerate: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }
    val currentLocationName = stringResource(R.string.current_location)
    val mapPointName = stringResource(R.string.map_point)

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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
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
        Spacer(modifier = Modifier.height(16.dp))
        PlaceSearchField(
            field = routeViewModel.endField,
            label = stringResource(R.string.label_end_point),
            onInputChange = { routeViewModel.onPlaceInputChange(routeViewModel.endField, it) },
            onSelect = { routeViewModel.selectPlace(routeViewModel.endField, it) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.map_pick_label), modifier = Modifier.weight(1f))
            FilterChip(
                selected = routeViewModel.pickTarget == PickTarget.START,
                onClick = { routeViewModel.pickTarget = PickTarget.START },
                label = { Text(stringResource(R.string.map_pick_start)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = routeViewModel.pickTarget == PickTarget.END,
                onClick = { routeViewModel.pickTarget = PickTarget.END },
                label = { Text(stringResource(R.string.map_pick_end)) }
            )
        }
        PlacePickerMap(
            start = routeViewModel.startField.selected,
            end = routeViewModel.endField.selected,
            onPick = { point ->
                val field = if (routeViewModel.pickTarget == PickTarget.START) routeViewModel.startField else routeViewModel.endField
                routeViewModel.selectPoint(field, mapPointName, point.longitude, point.latitude)
            },
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.label_distance), fontSize = 18.sp)
            OutlinedTextField(
                value = routeViewModel.distanceInput,
                onValueChange = { routeViewModel.distanceInput = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(100.dp)
            )
            Text(stringResource(R.string.unit_km), fontSize = 18.sp)
        }
        routeViewModel.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it.asString(), color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (routeViewModel.prepare(language)) onGenerate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_generate))
        }
    }
}

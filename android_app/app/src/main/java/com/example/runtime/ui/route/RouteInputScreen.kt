package com.example.runtime.ui.route

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.asString

// 4. Route Input Screen (화면 4)
@Composable
fun RouteInputScreen(routeViewModel: RouteViewModel, onGenerate: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        PlaceSearchField(
            field = routeViewModel.startField,
            label = stringResource(R.string.label_start_point),
            onInputChange = { routeViewModel.onPlaceInputChange(routeViewModel.startField, it) },
            onSelect = { routeViewModel.selectPlace(routeViewModel.startField, it) }
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

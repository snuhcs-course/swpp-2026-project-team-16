package com.example.runtime.ui.route

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.asString

// 4. Route Input Screen (화면 4)
@Composable
fun RouteInputScreen(routeViewModel: RouteViewModel, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = routeViewModel.startPointInput,
            onValueChange = { routeViewModel.startPointInput = it },
            label = { Text(stringResource(R.string.label_start_point)) },
            // TODO: I was considering geocoding here, but open to other methods
            modifier = Modifier.fillMaxWidth()
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
                if (routeViewModel.prepare()) onGenerate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.action_generate))
        }
    }
}

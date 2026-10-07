package com.example.runtime.ui.route

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R

// 5. Generating Screen (화면 5)
@Composable
fun GeneratingScreen(routeViewModel: RouteViewModel, onComplete: () -> Unit, onFailure: () -> Unit) {
    LaunchedEffect(Unit) {
        routeViewModel.generate(onComplete, onFailure)
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.generating_route), fontSize = 20.sp)
    }
}

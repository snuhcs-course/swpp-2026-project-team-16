package com.example.runtime.ui.route

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Text("generating route", fontSize = 20.sp)
    }
}

package com.example.runtime.ui.mypage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.ui.common.formatDistance
import com.example.runtime.ui.route.RouteCanvas

@Composable
fun SavedRouteDetailScreen(myPageViewModel: MyPageViewModel, routeId: Int, onBack: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(routeId) {
        myPageViewModel.loadDetail(routeId)
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val route = myPageViewModel.detail
        if (route == null) {
            myPageViewModel.detailError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            } ?: CircularProgressIndicator()
            return@Column
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.LightGray)
        ) {
            RouteCanvas(lines = route.route, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "distance : ${formatDistance(route.distance)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("saved : ${route.savedAt.take(10)}", color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("LLM briefing:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(route.briefing)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Button(
                onClick = { showDeleteDialog = true },
                enabled = !myPageViewModel.isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (myPageViewModel.isDeleting) "Deleting..." else "Delete")
            }
        }
        myPageViewModel.detailError?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete route?") },
            text = { Text("This removes the route from My Page.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    myPageViewModel.delete(routeId, onBack)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

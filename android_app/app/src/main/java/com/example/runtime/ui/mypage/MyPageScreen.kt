package com.example.runtime.ui.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.ui.common.formatDistance

// 7. My Page Screen (화면 7)
@Composable
fun MyPageScreen(myPageViewModel: MyPageViewModel, onRouteClick: (Int) -> Unit) {
    LaunchedEffect(Unit) {
        myPageViewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("≡", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(myPageViewModel.username ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        myPageViewModel.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (myPageViewModel.isLoading && myPageViewModel.routes.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myPageViewModel.routes.isEmpty() && myPageViewModel.error == null) {
            Text("No saved routes yet.", color = Color.Gray)
        }
        LazyColumn {
            items(myPageViewModel.routes, key = { it.id }) { route ->
                Card(
                    onClick = { onRouteClick(route.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = formatDistance(route.distance),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = route.briefing,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = route.savedAt.take(10), color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

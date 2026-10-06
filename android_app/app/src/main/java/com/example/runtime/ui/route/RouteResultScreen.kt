package com.example.runtime.ui.route

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

// 6. Route Result Screen (화면 6)
@Composable
fun RouteResultScreen(routeViewModel: RouteViewModel) {
    val generated = routeViewModel.generated ?: return
    val saveState = routeViewModel.saveState

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 지도
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            // TODO : map 연동해서 생성된 route 렌더링
            RouteCanvas(lines = generated.route.route, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "distance : ${formatDistance(generated.route.distance)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // LLM Briefing
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("LLM briefing:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(generated.route.briefing)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { /* TODO : Share 링크 공유 기능 */ }) {
                Text("Share")
            }
            Button(
                onClick = { routeViewModel.save() },
                enabled = saveState == SaveState.Idle || saveState is SaveState.Failed
            ) {
                Text(
                    when (saveState) {
                        SaveState.Saving -> "Saving..."
                        SaveState.Saved -> "Saved"
                        else -> "Save"
                    }
                )
            }
        }
        if (saveState is SaveState.Failed) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(saveState.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

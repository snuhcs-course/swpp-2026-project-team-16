package com.example.runtime.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.asString
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            // TODO : map 연동해서 생성된 route 렌더링
            RouteCanvas(lines = generated.route.route, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.distance_value, formatDistance(generated.route.distance)),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        if (generated.route.isShortestPath) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.shortest_path_notice, formatDistance(generated.route.distance)),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // LLM Briefing
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.llm_briefing), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.action_share))
            }
            Button(
                onClick = { routeViewModel.save() },
                enabled = saveState == SaveState.Idle || saveState is SaveState.Failed
            ) {
                Text(
                    stringResource(
                        when (saveState) {
                            SaveState.Saving -> R.string.saving
                            SaveState.Saved -> R.string.saved
                            else -> R.string.action_save
                        }
                    )
                )
            }
        }
        if (saveState is SaveState.Failed) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(saveState.message.asString(), color = MaterialTheme.colorScheme.error)
        }
    }
}

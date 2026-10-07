package com.example.runtime.ui.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.common.LanguageMenu
import com.example.runtime.ui.common.asString
import com.example.runtime.ui.common.formatDistance

// 7. My Page Screen (화면 7)
@Composable
fun MyPageScreen(myPageViewModel: MyPageViewModel, onRouteClick: (Int) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(myPageViewModel.username ?: "", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_language),
                            contentDescription = stringResource(R.string.language)
                        )
                    }
                    LanguageMenu(expanded = showMenu, onDismiss = { showMenu = false })
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        myPageViewModel.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (myPageViewModel.isLoading && myPageViewModel.routes.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myPageViewModel.routes.isEmpty() && myPageViewModel.error == null) {
            Text(stringResource(R.string.no_saved_routes), color = Color.Gray)
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

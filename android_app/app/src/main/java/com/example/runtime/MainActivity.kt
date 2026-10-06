package com.example.runtime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RunTimeApp()
                }
            }
        }
    }
}

// 1. Main Navigation & Screen Manager
@Composable
fun RunTimeApp() {
    var currentScreen by remember { mutableStateOf("splash") }

    Scaffold(
        bottomBar = {
            // Splash(화면2) 랑  Generating(화면5) 빼고 아래 메뉴바 표시
            if (currentScreen != "splash" && currentScreen != "generating" && currentScreen != "login") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == "route_input" || currentScreen == "route_result",
                        onClick = { currentScreen = "route_input" },
                        icon = { Text("Route", fontWeight = FontWeight.Bold) },
                        label = { Text("Generate") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "MyPage",
                        onClick = { currentScreen = "MyPage" },
                        icon = { Text("My", fontWeight = FontWeight.Bold) },
                        label = { Text("My Page") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                "splash" -> SplashScreen(onNext = { currentScreen = "login" })
                "login" -> LoginScreen(onLoginSuccess = { currentScreen = "route_input" })
                "route_input" -> RouteInputScreen(onGenerate = { currentScreen = "generating" })
                "generating" -> GeneratingScreen(onComplete = { currentScreen = "route_result" })
                "route_result" -> RouteResultScreen()
                "MyPage" -> MyPageScreen()
            }
        }
    }
}

// 2. Splash Screen (화면 2)
@Composable
fun SplashScreen(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // 2초 후 자동으로 로그인 화면 이동
        // TODO: health check
        onNext()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("RunTime", fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}

// 3. Login Screen (화면 3)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text("login:") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Pass:") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                //TODO: Auth API 연동
                onLoginSuccess()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log-in")
        }
    }
}

// 4. Route Input Screen (화면 4)
@Composable
fun RouteInputScreen(onGenerate: () -> Unit) {
    var startPoint by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = startPoint,
            onValueChange = { startPoint = it },
            label = { Text("Start point (e.g. Samgakji)") },
            // TODO: I was considering geocoding here, but open to other methods 
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("distance : ", fontSize = 18.sp)
            OutlinedTextField(
                value = distance,
                onValueChange = { distance = it },
                modifier = Modifier.width(100.dp)
            )
            Text(" km", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                // TODO : user 입력 startpoint, distance -> backend 전달
                onGenerate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate")
        }
    }
}

// 5. Generating Screen (화면 5)
@Composable
fun GeneratingScreen(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        // TODO : 백엔드 API 요청 후 success response 받으면 onComplete() 호출로 변경
        delay(2000) // 일단은 2초 후 자동으로 결과 화면으로 이동
        onComplete()
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

// 6. Route Result Screen (화면 6)
@Composable
fun RouteResultScreen() {
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
            Text("[ Map Route View ]", color = Color.DarkGray)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("distance : 5km", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // LLM Briefing
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("LLM briefing:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Good weather to run. Blah Blah Blah")
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
            Button(onClick = { /* TODO : mypage 저장 기능(서버 db에 경로 저장?) */ }) {
                Text("Save")
            }
        }
    }
}

// 7. My Page Screen (화면 7)
@Composable
fun MyPageScreen() {
    //TODO : 서버에서 route list 불러오기로 대체되지 않을까
    val savedRoutes = listOf("Route 1", "Route 2", "Route 3")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("≡", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("SoonYoung", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn {
            items(savedRoutes) { route ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = route,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

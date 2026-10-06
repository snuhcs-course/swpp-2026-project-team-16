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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtime.ui.auth.AuthViewModel
import com.example.runtime.ui.route.RouteCanvas
import com.example.runtime.ui.route.RouteViewModel
import com.example.runtime.ui.route.SaveState
import com.example.runtime.ui.route.formatDistance
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
    val authViewModel: AuthViewModel = viewModel()
    val routeViewModel: RouteViewModel = viewModel()

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
                "splash" -> SplashScreen(
                    authViewModel = authViewModel,
                    onNext = { loggedIn -> currentScreen = if (loggedIn) "route_input" else "login" }
                )
                "login" -> LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = { currentScreen = "route_input" }
                )
                "route_input" -> RouteInputScreen(
                    routeViewModel = routeViewModel,
                    onGenerate = { currentScreen = "generating" }
                )
                "generating" -> GeneratingScreen(
                    routeViewModel = routeViewModel,
                    onComplete = { currentScreen = "route_result" },
                    onFailure = { currentScreen = "route_input" }
                )
                "route_result" -> RouteResultScreen(routeViewModel = routeViewModel)
                "MyPage" -> MyPageScreen()
            }
        }
    }
}

// 2. Splash Screen (화면 2)
@Composable
fun SplashScreen(authViewModel: AuthViewModel, onNext: (Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // 2초 후 자동으로 로그인 화면 이동
        // TODO: health check
        authViewModel.restoreSession(onNext)
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
fun LoginScreen(authViewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    var id by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    val uiState = authViewModel.uiState

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = id,
            onValueChange = { id = it },
            label = { Text("login:") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isRegister) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email:") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Pass:") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        uiState.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (isRegister) {
                    authViewModel.register(id, email, pass, onLoginSuccess)
                } else {
                    authViewModel.login(id, pass, onLoginSuccess)
                }
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isRegister) "Sign up" else "Log-in")
            }
        }
        TextButton(
            onClick = {
                isRegister = !isRegister
                authViewModel.clearError()
            },
            enabled = !uiState.isLoading
        ) {
            Text(if (isRegister) "Already have an account? Log in" else "Create an account")
        }
    }
}

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
                value = routeViewModel.distanceInput,
                onValueChange = { routeViewModel.distanceInput = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(100.dp)
            )
            Text(" km", fontSize = 18.sp)
        }
        routeViewModel.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (routeViewModel.prepare()) onGenerate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate")
        }
    }
}

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

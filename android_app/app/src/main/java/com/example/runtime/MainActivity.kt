package com.example.runtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtime.ui.auth.AuthViewModel
import com.example.runtime.ui.auth.LoginScreen
import com.example.runtime.ui.mypage.MyPageScreen
import com.example.runtime.ui.mypage.MyPageViewModel
import com.example.runtime.ui.mypage.SavedRouteDetailScreen
import com.example.runtime.ui.route.GeneratingScreen
import com.example.runtime.ui.route.RouteInputScreen
import com.example.runtime.ui.route.RouteResultScreen
import com.example.runtime.ui.route.RouteViewModel
import com.example.runtime.ui.splash.SplashScreen
import com.example.runtime.ui.theme.RunTimeTheme

class MainActivity : ComponentActivity() {

    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocalNetworkIfNeeded()
        setContent {
            RunTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RunTimeApp()
                }
            }
        }
    }

    private fun requestLocalNetworkIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestLocalNetwork.launch(permission)
        }
    }
}

// 1. Main Navigation & Screen Manager
@Composable
fun RunTimeApp() {
    var currentScreen by remember { mutableStateOf("splash") }
    val authViewModel: AuthViewModel = viewModel()
    val routeViewModel: RouteViewModel = viewModel()
    val myPageViewModel: MyPageViewModel = viewModel()
    var selectedRouteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        bottomBar = {
            // Splash(화면2) 랑  Generating(화면5) 빼고 아래 메뉴바 표시
            if (currentScreen != "splash" && currentScreen != "generating" && currentScreen != "login") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == "route_input" || currentScreen == "route_result",
                        onClick = { currentScreen = "route_input" },
                        icon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) },
                        label = { Text("Route") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "MyPage" || currentScreen == "saved_detail",
                        onClick = { currentScreen = "MyPage" },
                        icon = { Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null) },
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
                "MyPage" -> MyPageScreen(
                    myPageViewModel = myPageViewModel,
                    onRouteClick = { routeId ->
                        selectedRouteId = routeId
                        currentScreen = "saved_detail"
                    }
                )
                "saved_detail" -> selectedRouteId?.let { routeId ->
                    SavedRouteDetailScreen(
                        myPageViewModel = myPageViewModel,
                        routeId = routeId,
                        onBack = { currentScreen = "MyPage" }
                    )
                }
            }
        }
    }
}

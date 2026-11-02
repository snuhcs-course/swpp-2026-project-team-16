package com.example.runtime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtime.data.remote.SessionEvents
import com.example.runtime.ui.auth.AuthViewModel
import com.example.runtime.ui.auth.LoginPromptDialog
import com.example.runtime.ui.auth.LoginScreen
import com.example.runtime.ui.auth.SignUpScreen
import com.example.runtime.ui.common.LocalAnimatedVisibilityScope
import com.example.runtime.ui.common.LocalSharedTransitionScope
import com.example.runtime.ui.common.AppMessages
import com.example.runtime.ui.common.LocalAppMessages
import com.example.runtime.ui.community.CommunityScreen
import com.example.runtime.ui.community.CommunityViewModel
import com.example.runtime.ui.community.RunShareAction
import com.example.runtime.ui.community.SharedRouteDetailScreen
import com.example.runtime.ui.mypage.GuestMyPageScreen
import com.example.runtime.ui.mypage.MyPageScreen
import com.example.runtime.ui.mypage.MyPageViewModel
import com.example.runtime.ui.mypage.SavedRouteDetailScreen
import com.example.runtime.ui.route.GeneratingScreen
import com.example.runtime.ui.route.RouteInputScreen
import com.example.runtime.ui.route.RunNavigationScreen
import com.example.runtime.ui.route.RouteResultScreen
import com.example.runtime.ui.route.RouteViewModel
import com.example.runtime.ui.route.RunTarget
import com.example.runtime.ui.route.RunViewModel
import com.example.runtime.ui.splash.SplashScreen
import com.example.runtime.ui.theme.RunTimeTheme
import com.example.runtime.ui.theme.ThemeMode
import com.example.runtime.ui.theme.ThemeStorage
import com.example.runtime.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocalNetworkIfNeeded()
        val themeStorage = ThemeStorage(applicationContext)
        setContent {
            val themeMode by themeStorage.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val dark = themeMode.isDark()
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RunTimeTheme(themeMode) {
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RunTimeApp() {
    var currentScreen by rememberSaveable { mutableStateOf("splash") }
    val authViewModel: AuthViewModel = viewModel()
    val routeViewModel: RouteViewModel = viewModel()
    val myPageViewModel: MyPageViewModel = viewModel()
    val runViewModel: RunViewModel = viewModel()
    val communityViewModel: CommunityViewModel = viewModel()
    var selectedRouteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedSharedRouteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var sharedDetailReturnScreen by rememberSaveable { mutableStateOf("community") }
    var runReturnScreen by rememberSaveable { mutableStateOf("route_result") }
    var signUpEmail by rememberSaveable { mutableStateOf("") }
    var loginReturnScreen by rememberSaveable { mutableStateOf("route_input") }
    var saveAfterLogin by rememberSaveable { mutableStateOf(false) }
    var loginPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val appMessages = remember { AppMessages(snackbarHostState, appScope) }
    val sharedMessage = stringResource(R.string.route_shared_message)

    fun openAuth(screen: String, returnTo: String, save: Boolean) {
        loginReturnScreen = returnTo
        saveAfterLogin = save
        signUpEmail = ""
        authViewModel.clearError()
        currentScreen = screen
    }

    fun onAuthenticated() {
        myPageViewModel.reset()
        currentScreen = loginReturnScreen
        if (saveAfterLogin) {
            saveAfterLogin = false
            routeViewModel.save()
        }
    }

    LaunchedEffect(Unit) {
        SessionEvents.expired.collect {
            if (currentScreen in setOf("splash", "login", "signup")) return@collect
            authViewModel.onSessionExpired {
                routeViewModel.reset()
                myPageViewModel.reset()
                selectedRouteId = null
                loginReturnScreen = "route_input"
                saveAfterLogin = false
                currentScreen = "login"
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Splash(화면2) 랑  Generating(화면5) 빼고 아래 메뉴바 표시
            if (currentScreen !in setOf("splash", "generating", "login", "signup", "run_navigation")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentScreen == "route_input" || currentScreen == "route_result",
                        onClick = { currentScreen = "route_input" },
                        icon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_route)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "community" || currentScreen == "community_detail",
                        onClick = { currentScreen = "community" },
                        icon = { Icon(painterResource(R.drawable.ic_groups), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_community)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "MyPage" || currentScreen == "saved_detail",
                        onClick = { currentScreen = "MyPage" },
                        icon = { Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_my_page)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalAppMessages provides appMessages) {
            SharedTransitionLayout(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.97f))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "screen"
                    ) { screen ->
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (screen in FULL_BLEED_SCREENS) Modifier else Modifier.statusBarsPadding())
                            ) {
                                when (screen) {
                                    "splash" -> SplashScreen(
                                        authViewModel = authViewModel,
                                        onNext = { currentScreen = "route_input" }
                                    )
                                    "login" -> LoginScreen(
                                        authViewModel = authViewModel,
                                        onLoginSuccess = ::onAuthenticated,
                                        onSignUpClick = {
                                            signUpEmail = ""
                                            currentScreen = "signup"
                                        },
                                        onGoogleSignUpRequired = { email ->
                                            signUpEmail = email
                                            currentScreen = "signup"
                                        },
                                        onNotNow = {
                                            saveAfterLogin = false
                                            currentScreen = loginReturnScreen
                                        }
                                    )
                                    "signup" -> SignUpScreen(
                                        authViewModel = authViewModel,
                                        initialEmail = signUpEmail,
                                        onSignUpSuccess = ::onAuthenticated,
                                        onLogInClick = { currentScreen = "login" }
                                    )
                                    "route_input" -> RouteInputScreen(
                                        routeViewModel = routeViewModel,
                                        onGenerate = {
                                            if (!authViewModel.isLoggedIn && authViewModel.isGuestTrialUsed) {
                                                loginPrompt = LOGIN_PROMPT_TRIAL
                                            } else {
                                                currentScreen = "generating"
                                            }
                                        }
                                    )
                                    "generating" -> GeneratingScreen(
                                        routeViewModel = routeViewModel,
                                        onComplete = {
                                            authViewModel.markGuestTrialUsed()
                                            currentScreen = "route_result"
                                        },
                                        onFailure = { currentScreen = "route_input" }
                                    )
                                    "route_result" -> RouteResultScreen(
                                        routeViewModel = routeViewModel,
                                        onStartRun = {
                                            routeViewModel.generated?.let { generated ->
                                                runViewModel.start(
                                                    RunTarget(
                                                        lines = generated.route.route,
                                                        distanceMeters = generated.route.distance,
                                                        routeId = routeViewModel.savedRouteId(generated.temporaryRouteId),
                                                        temporaryRouteId = generated.temporaryRouteId,
                                                        startName = routeViewModel.pendingStart?.name.orEmpty()
                                                    )
                                                )
                                                runReturnScreen = "route_result"
                                                currentScreen = "run_navigation"
                                            }
                                        },
                                        onSave = {
                                            if (authViewModel.isLoggedIn) routeViewModel.save() else loginPrompt = LOGIN_PROMPT_SAVE
                                        }
                                    )
                                    "run_navigation" -> runViewModel.target?.let { target ->
                                        RunNavigationScreen(
                                            lines = target.lines,
                                            routeDistanceMeters = target.distanceMeters,
                                            onFinish = { currentScreen = runReturnScreen },
                                            onRunFinished = { distanceMeters, elapsedSeconds ->
                                                if (authViewModel.isLoggedIn) runViewModel.recordRun(distanceMeters, elapsedSeconds)
                                            },
                                            summaryAction = {
                                                RunShareAction(
                                                    runViewModel = runViewModel,
                                                    isLoggedIn = authViewModel.isLoggedIn,
                                                    onShared = { sharedRouteId ->
                                                        appMessages.show(sharedMessage)
                                                        selectedSharedRouteId = sharedRouteId
                                                        sharedDetailReturnScreen = "community"
                                                        currentScreen = "community_detail"
                                                    },
                                                    onOpenShared = { sharedRouteId ->
                                                        selectedSharedRouteId = sharedRouteId
                                                        sharedDetailReturnScreen = "community"
                                                        currentScreen = "community_detail"
                                                    }
                                                )
                                            },
                                        )
                                    } ?: run {
                                        currentScreen = "route_input"
                                    }
                                    "community" -> CommunityScreen(
                                        communityViewModel = communityViewModel,
                                        onRouteClick = { sharedRouteId ->
                                            selectedSharedRouteId = sharedRouteId
                                            sharedDetailReturnScreen = "community"
                                            currentScreen = "community_detail"
                                        }
                                    )
                                    "community_detail" -> selectedSharedRouteId?.let { sharedRouteId ->
                                        SharedRouteDetailScreen(
                                            communityViewModel = communityViewModel,
                                            sharedRouteId = sharedRouteId,
                                            onBack = { currentScreen = sharedDetailReturnScreen },
                                            onRun = { shared ->
                                                runViewModel.start(
                                                    RunTarget(
                                                        lines = shared.route,
                                                        distanceMeters = shared.distance,
                                                        routeId = shared.routeId
                                                    )
                                                )
                                                runReturnScreen = "community_detail"
                                                currentScreen = "run_navigation"
                                            },
                                            onSave = {
                                                if (authViewModel.isLoggedIn) communityViewModel.save()
                                                else loginPrompt = LOGIN_PROMPT_SAVE_SHARED
                                            },
                                            isLoggedIn = authViewModel.isLoggedIn,
                                            onLogInToReview = { loginPrompt = LOGIN_PROMPT_REVIEW }
                                        )
                                    }
                                    "MyPage" -> if (!authViewModel.isLoggedIn) GuestMyPageScreen(
                                        onLogIn = { openAuth("login", returnTo = "MyPage", save = false) },
                                        onSignUp = { openAuth("signup", returnTo = "MyPage", save = false) }
                                    ) else MyPageScreen(
                                        myPageViewModel = myPageViewModel,
                                        onRouteClick = { routeId ->
                                            selectedRouteId = routeId
                                            currentScreen = "saved_detail"
                                        },
                                        onSharedRouteClick = { sharedRouteId ->
                                            selectedSharedRouteId = sharedRouteId
                                            sharedDetailReturnScreen = "MyPage"
                                            currentScreen = "community_detail"
                                        },
                                        onCreateRoute = { currentScreen = "route_input" },
                                        onLogout = {
                                            authViewModel.logout {
                                                routeViewModel.reset()
                                                myPageViewModel.reset()
                                                selectedRouteId = null
                                                currentScreen = "route_input"
                                            }
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
                }
            }

            loginPrompt?.let { reason ->
                LoginPromptDialog(
                    message = when (reason) {
                        LOGIN_PROMPT_SAVE -> R.string.login_prompt_save
                        LOGIN_PROMPT_SAVE_SHARED -> R.string.login_prompt_save_shared
                        LOGIN_PROMPT_REVIEW -> R.string.login_prompt_review
                        else -> R.string.login_prompt_trial_used
                    },
                    onLogIn = {
                        loginPrompt = null
                        when (reason) {
                            LOGIN_PROMPT_SAVE -> openAuth("login", returnTo = "route_result", save = true)
                            LOGIN_PROMPT_SAVE_SHARED, LOGIN_PROMPT_REVIEW ->
                                openAuth("login", returnTo = "community_detail", save = false)
                            else -> openAuth("login", returnTo = "route_input", save = false)
                        }
                    },
                    onDismiss = { loginPrompt = null }
                )
            }
        }
    }
}

private val FULL_BLEED_SCREENS = setOf("route_input", "generating", "route_result")
private const val LOGIN_PROMPT_TRIAL = "trial"
private const val LOGIN_PROMPT_SAVE = "save"
private const val LOGIN_PROMPT_SAVE_SHARED = "save_shared"
private const val LOGIN_PROMPT_REVIEW = "review"

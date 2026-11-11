package com.example.runtime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
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
import com.example.runtime.ui.board.BoardViewModel
import com.example.runtime.ui.board.PostDetailScreen
import com.example.runtime.ui.board.PostEditorScreen
import com.example.runtime.ui.group.CreateGroupScreen
import com.example.runtime.ui.group.GroupChatScreen
import com.example.runtime.ui.group.GroupDetailScreen
import com.example.runtime.ui.group.GroupRouteChoice
import com.example.runtime.ui.group.GroupsViewModel
import com.example.runtime.ui.mypage.SavedRouteDetailScreen
import com.example.runtime.ui.route.GeneratingScreen
import com.example.runtime.ui.route.RouteInputScreen
import com.example.runtime.ui.route.RunNavigationScreen
import com.example.runtime.ui.route.RunReportScreen
import com.example.runtime.ui.mypage.RunningStyleScreen
import com.example.runtime.ui.profile.UserProfileScreen
import com.example.runtime.data.push.NotificationNavigation
import com.example.runtime.data.push.NotificationTarget
import com.example.runtime.data.push.Push
import com.example.runtime.ui.group.GroupInvitesScreen
import com.example.runtime.ui.calendar.CalendarScreen
import com.example.runtime.ui.calendar.CalendarViewModel
import com.example.runtime.ui.notifications.NotificationsScreen
import com.example.runtime.ui.notifications.NotificationsViewModel
import com.example.runtime.ui.notifications.SettingsScreen
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import com.example.runtime.ui.profile.UserProfileViewModel
import com.example.runtime.ui.mypage.RunningStyleViewModel
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NotificationNavigation.fromIntent(intent)?.let(NotificationNavigation::open)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocalNetworkIfNeeded()
        NotificationNavigation.fromIntent(intent)?.let(NotificationNavigation::open)
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
    val runningStyleViewModel: RunningStyleViewModel = viewModel()
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val profileStack = remember { mutableStateListOf<String>() }
    var profileReturnScreen by rememberSaveable { mutableStateOf("community") }
    var groupReturnScreen by rememberSaveable { mutableStateOf("community") }
    var postReturnScreen by rememberSaveable { mutableStateOf("community") }
    var invitesReturnScreen by rememberSaveable { mutableStateOf("community") }
    val communityViewModel: CommunityViewModel = viewModel()
    val groupsViewModel: GroupsViewModel = viewModel()
    val boardViewModel: BoardViewModel = viewModel()
    var selectedRouteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedSharedRouteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var sharedDetailReturnScreen by rememberSaveable { mutableStateOf("community") }
    var runReturnScreen by rememberSaveable { mutableStateOf("route_result") }
    var selectedGroupId by rememberSaveable { mutableStateOf<Int?>(null) }
    var groupRouteChoice by remember { mutableStateOf<GroupRouteChoice?>(null) }
    var groupCreateReturnScreen by rememberSaveable { mutableStateOf("community") }
    var selectedPostId by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingPostId by rememberSaveable { mutableStateOf<Int?>(null) }
    val groupCreatedMessage = stringResource(R.string.group_created_message)
    var signUpEmail by rememberSaveable { mutableStateOf("") }
    var loginReturnScreen by rememberSaveable { mutableStateOf("route_input") }
    var saveAfterLogin by rememberSaveable { mutableStateOf(false) }
    var loginPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationsViewModel: NotificationsViewModel = viewModel()
    val calendarViewModel: CalendarViewModel = viewModel()
    var notificationsReturnScreen by rememberSaveable { mutableStateOf("MyPage") }
    var settingsReturnScreen by rememberSaveable { mutableStateOf("MyPage") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pendingNotification by NotificationNavigation.target.collectAsState()

    fun clearUserState() {
        routeViewModel.reset()
        myPageViewModel.reset()
        groupsViewModel.reset()
        boardViewModel.reset()
        communityViewModel.reset()
        notificationsViewModel.reset()
        calendarViewModel.reset()
        selectedRouteId = null
    }

    LaunchedEffect(authViewModel.isLoggedIn) {
        if (authViewModel.isLoggedIn) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Push.register(context)
        }
    }
    val appMessages = remember { AppMessages(snackbarHostState, appScope) }
    val sharedMessage = stringResource(R.string.route_shared_message)
    val accountDeletedMessage = stringResource(R.string.account_deleted_message)

    fun openAuth(screen: String, returnTo: String, save: Boolean) {
        loginReturnScreen = returnTo
        saveAfterLogin = save
        signUpEmail = ""
        authViewModel.clearError()
        currentScreen = screen
    }

    fun openUser(username: String) {
        if (currentScreen != "user_profile") {
            profileReturnScreen = currentScreen
            profileStack.clear()
        }
        if (profileStack.lastOrNull() != username) profileStack.add(username)
        currentScreen = "user_profile"
    }

    fun closeUser() {
        profileStack.removeLastOrNull()
        if (profileStack.isEmpty()) currentScreen = profileReturnScreen
    }

    LaunchedEffect(pendingNotification, currentScreen, authViewModel.isLoggedIn) {
        val target = pendingNotification ?: return@LaunchedEffect
        if (currentScreen in setOf("splash", "login", "signup") || !authViewModel.isLoggedIn) return@LaunchedEffect
        NotificationNavigation.consume()
        target.notificationId?.let { notificationsViewModel.markRead(it) }
        val from = currentScreen
        when (target.kind) {
            "follow" -> target.actor?.let(::openUser)
            "group_invite" -> {
                invitesReturnScreen = from
                currentScreen = "group_invites"
            }
            "group_join", "group_message" -> target.groupId?.let { groupId ->
                selectedGroupId = groupId
                groupReturnScreen = from
                currentScreen = if (target.kind == "group_message") "group_chat" else "group_detail"
            }
            "post_like", "post_comment" -> target.postId?.let { postId ->
                selectedPostId = postId
                postReturnScreen = from
                currentScreen = "post_detail"
            }
            "route_review" -> target.sharedRouteId?.let { sharedRouteId ->
                selectedSharedRouteId = sharedRouteId
                sharedDetailReturnScreen = from
                currentScreen = "community_detail"
            }
        }
    }

    fun openCreateGroup(route: GroupRouteChoice?, returnTo: String) {
        groupRouteChoice = route
        groupCreateReturnScreen = returnTo
        currentScreen = "group_create"
    }

    fun onAuthenticated() {
        myPageViewModel.reset()
        groupsViewModel.reset()
        boardViewModel.reset()
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
                groupsViewModel.reset()
                boardViewModel.reset()
                communityViewModel.reset()
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
            if (currentScreen !in setOf("splash", "generating", "login", "signup", "run_navigation", "group_chat", "group_create", "post_detail", "post_editor")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentScreen == "route_input" || currentScreen == "route_result",
                        onClick = { currentScreen = "route_input" },
                        icon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_route)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == "calendar",
                        onClick = { currentScreen = "calendar" },
                        icon = { Icon(painterResource(R.drawable.ic_calendar), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_calendar)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen in setOf("community", "community_detail", "group_detail", "group_chat", "group_create", "post_detail", "post_editor", "user_profile", "group_invites"),
                        onClick = { currentScreen = "community" },
                        icon = { Icon(painterResource(R.drawable.ic_groups), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_community)) }
                    )
                    NavigationBarItem(
                        selected = currentScreen in setOf("MyPage", "saved_detail", "running_style"),
                        onClick = { currentScreen = "MyPage" },
                        icon = { Icon(painterResource(R.drawable.ic_account_circle), contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_my_page)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalAppMessages provides appMessages) {
            val bottomPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            SharedTransitionLayout(modifier = Modifier.padding(bottomPadding).consumeWindowInsets(bottomPadding)) {
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
                                                        startName = routeViewModel.pendingStart?.name.orEmpty(),
                                                        plan = routeViewModel.runPlan
                                                    )
                                                )
                                                runReturnScreen = "route_result"
                                                currentScreen = "run_navigation"
                                            }
                                        },
                                        onSave = {
                                            if (authViewModel.isLoggedIn) routeViewModel.save() else loginPrompt = LOGIN_PROMPT_SAVE
                                        },
                                        isLoggedIn = authViewModel.isLoggedIn,
                                        onLogInToShare = { loginPrompt = LOGIN_PROMPT_SHARE_ROUTE },
                                        onShared = { sharedRouteId ->
                                            appMessages.show(sharedMessage)
                                            selectedSharedRouteId = sharedRouteId
                                            sharedDetailReturnScreen = "route_result"
                                            currentScreen = "community_detail"
                                        },
                                        onOpenShared = { sharedRouteId ->
                                            selectedSharedRouteId = sharedRouteId
                                            sharedDetailReturnScreen = "route_result"
                                            currentScreen = "community_detail"
                                        }
                                    )
                                    "run_navigation" -> runViewModel.target?.let { target ->
                                        val result = runViewModel.result
                                        if (result == null) {
                                            RunNavigationScreen(
                                                lines = target.lines,
                                                routeDistanceMeters = target.distanceMeters,
                                                onFinish = { currentScreen = runReturnScreen },
                                                onRunFinished = { runResult ->
                                                    runViewModel.finish(runResult, authViewModel.isLoggedIn)
                                                },
                                            )
                                        } else {
                                            BackHandler { currentScreen = runReturnScreen }
                                            RunReportScreen(
                                                result = result,
                                                plan = target.plan,
                                                isLoggedIn = authViewModel.isLoggedIn,
                                                recordState = runViewModel.recordState,
                                                reportState = runViewModel.reportState,
                                                runRating = runViewModel.runRating,
                                                onRate = runViewModel::rateRun,
                                                onRetryReport = runViewModel::loadReport,
                                                onPlanNextRun = { plan ->
                                                    routeViewModel.applyPlan(plan)
                                                    currentScreen = "route_input"
                                                },
                                                onDone = { currentScreen = runReturnScreen },
                                                shareAction = {
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
                                        }
                                    } ?: run {
                                        currentScreen = "route_input"
                                    }
                                    "community" -> CommunityScreen(
                                        communityViewModel = communityViewModel,
                                        groupsViewModel = groupsViewModel,
                                        boardViewModel = boardViewModel,
                                        isLoggedIn = authViewModel.isLoggedIn,
                                        onRouteClick = { sharedRouteId ->
                                            selectedSharedRouteId = sharedRouteId
                                            sharedDetailReturnScreen = "community"
                                            currentScreen = "community_detail"
                                        },
                                        onGroupClick = { groupId ->
                                            selectedGroupId = groupId
                                            groupReturnScreen = "community"
                                            currentScreen = "group_detail"
                                        },
                                        onCreateGroup = { openCreateGroup(null, returnTo = "community") },
                                        onPostClick = { postId ->
                                            selectedPostId = postId
                                            postReturnScreen = "community"
                                            currentScreen = "post_detail"
                                        },
                                        onWritePost = {
                                            editingPostId = null
                                            currentScreen = "post_editor"
                                        },
                                        onLogInRequired = {
                                            loginPrompt = if (communityViewModel.selectedTab == 2) LOGIN_PROMPT_BOARD else LOGIN_PROMPT_GROUP
                                        },
                                        onOpenInvites = {
                                            invitesReturnScreen = "community"
                                            currentScreen = "group_invites"
                                        }
                                    )
                                    "post_detail" -> selectedPostId?.let { postId ->
                                        PostDetailScreen(
                                            boardViewModel = boardViewModel,
                                            postId = postId,
                                            isLoggedIn = authViewModel.isLoggedIn,
                                            onBack = { currentScreen = postReturnScreen },
                                            onEdit = {
                                                editingPostId = postId
                                                currentScreen = "post_editor"
                                            },
                                            onLogInRequired = { loginPrompt = LOGIN_PROMPT_POST },
                                            onOpenUser = ::openUser
                                        )
                                    }
                                    "post_editor" -> PostEditorScreen(
                                        boardViewModel = boardViewModel,
                                        editingPostId = editingPostId,
                                        onBack = { currentScreen = if (editingPostId == null) "community" else "post_detail" },
                                        onSaved = { postId ->
                                            selectedPostId = postId
                                            currentScreen = "post_detail"
                                        }
                                    )
                                    "group_detail" -> selectedGroupId?.let { groupId ->
                                        GroupDetailScreen(
                                            groupsViewModel = groupsViewModel,
                                            groupId = groupId,
                                            isLoggedIn = authViewModel.isLoggedIn,
                                            onBack = { currentScreen = groupReturnScreen },
                                            onRun = { group ->
                                                runViewModel.start(
                                                    RunTarget(
                                                        lines = group.route,
                                                        distanceMeters = group.routeDistance,
                                                        routeId = group.routeId,
                                                        startName = group.startName,
                                                        groupId = group.id
                                                    )
                                                )
                                                runReturnScreen = "group_detail"
                                                currentScreen = "run_navigation"
                                            },
                                            onOpenChat = { currentScreen = "group_chat" },
                                            onLogInToJoin = { loginPrompt = LOGIN_PROMPT_GROUP_DETAIL },
                                            onOpenUser = ::openUser
                                        )
                                    }
                                    "group_chat" -> selectedGroupId?.let { groupId ->
                                        GroupChatScreen(
                                            groupsViewModel = groupsViewModel,
                                            groupId = groupId,
                                            onBack = { currentScreen = "group_detail" }
                                        )
                                    }
                                    "group_create" -> CreateGroupScreen(
                                        groupsViewModel = groupsViewModel,
                                        initialRoute = groupRouteChoice,
                                        onBack = { currentScreen = groupCreateReturnScreen },
                                        onCreated = { groupId ->
                                            appMessages.show(groupCreatedMessage)
                                            selectedGroupId = groupId
                                            groupReturnScreen = "community"
                                            communityViewModel.selectedTab = 1
                                            currentScreen = "group_detail"
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
                                            onLogInToReview = { loginPrompt = LOGIN_PROMPT_REVIEW },
                                            onCreateGroup = { shared ->
                                                if (!authViewModel.isLoggedIn) {
                                                    loginPrompt = LOGIN_PROMPT_GROUP_SHARED
                                                } else {
                                                    openCreateGroup(
                                                        GroupRouteChoice(shared.routeId, shared.title, shared.distance),
                                                        returnTo = "community_detail"
                                                    )
                                                }
                                            },
                                            onOpenUser = ::openUser
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
                                        onRunAgain = { run ->
                                            runViewModel.start(
                                                RunTarget(
                                                    lines = run.route,
                                                    distanceMeters = run.routeDistance,
                                                    routeId = run.routeId,
                                                    startName = run.startName
                                                )
                                            )
                                            runReturnScreen = "MyPage"
                                            currentScreen = "run_navigation"
                                        },
                                        onCreateRoute = { currentScreen = "route_input" },
                                        onOpenRunningStyle = { currentScreen = "running_style" },
                                        onOpenMyProfile = ::openUser,
                                        onOpenInvites = {
                                            invitesReturnScreen = "MyPage"
                                            currentScreen = "group_invites"
                                        },
                                        onOpenNotifications = {
                                            notificationsReturnScreen = "MyPage"
                                            currentScreen = "notifications"
                                        },
                                        onOpenSettings = {
                                            settingsReturnScreen = "MyPage"
                                            currentScreen = "settings"
                                        },
                                        onLogout = {
                                            appScope.launch {
                                                Push.unregister(context)
                                                authViewModel.logout {
                                                    clearUserState()
                                                    currentScreen = "route_input"
                                                }
                                            }
                                        }
                                    )
                                    "user_profile" -> profileStack.lastOrNull()?.let { username ->
                                        UserProfileScreen(
                                            viewModel = userProfileViewModel,
                                            username = username,
                                            isLoggedIn = authViewModel.isLoggedIn,
                                            onBack = ::closeUser,
                                            onOpenUser = ::openUser,
                                            onRouteClick = { sharedRouteId ->
                                                selectedSharedRouteId = sharedRouteId
                                                sharedDetailReturnScreen = "user_profile"
                                                currentScreen = "community_detail"
                                            },
                                            onGroupClick = { groupId ->
                                                selectedGroupId = groupId
                                                groupReturnScreen = "user_profile"
                                                currentScreen = "group_detail"
                                            },
                                            onPostClick = { postId ->
                                                selectedPostId = postId
                                                postReturnScreen = "user_profile"
                                                currentScreen = "post_detail"
                                            },
                                            onLogInRequired = { loginPrompt = LOGIN_PROMPT_FOLLOW }
                                        )
                                    } ?: run {
                                        currentScreen = profileReturnScreen
                                    }
                                    "group_invites" -> GroupInvitesScreen(
                                        groupsViewModel = groupsViewModel,
                                        onBack = { currentScreen = invitesReturnScreen },
                                        onOpenGroup = { groupId ->
                                            selectedGroupId = groupId
                                            groupReturnScreen = "group_invites"
                                            currentScreen = "group_detail"
                                        }
                                    )
                                    "calendar" -> CalendarScreen(
                                        viewModel = calendarViewModel,
                                        isLoggedIn = authViewModel.isLoggedIn,
                                        onLogIn = { openAuth("login", returnTo = "calendar", save = false) },
                                        onGroupClick = { groupId ->
                                            selectedGroupId = groupId
                                            groupReturnScreen = "calendar"
                                            currentScreen = "group_detail"
                                        }
                                    )
                                    "notifications" -> NotificationsScreen(
                                        viewModel = notificationsViewModel,
                                        onBack = { currentScreen = notificationsReturnScreen },
                                        onOpen = { item ->
                                            NotificationNavigation.open(
                                                NotificationTarget(
                                                    kind = item.kind,
                                                    actor = item.actor,
                                                    groupId = item.groupId,
                                                    postId = item.postId,
                                                    sharedRouteId = item.sharedRouteId,
                                                )
                                            )
                                        },
                                        onOpenSettings = {
                                            settingsReturnScreen = "notifications"
                                            currentScreen = "settings"
                                        }
                                    )
                                    "settings" -> SettingsScreen(
                                        viewModel = notificationsViewModel,
                                        hasPassword = myPageViewModel.hasPassword,
                                        onBack = { currentScreen = settingsReturnScreen },
                                        onAccountDeleted = {
                                            authViewModel.logout {
                                                clearUserState()
                                                currentScreen = "route_input"
                                            }
                                            appMessages.show(accountDeletedMessage)
                                        }
                                    )
                                    "running_style" -> RunningStyleScreen(
                                        viewModel = runningStyleViewModel,
                                        onBack = { currentScreen = "MyPage" }
                                    )
                                    "saved_detail" -> selectedRouteId?.let { routeId ->
                                        SavedRouteDetailScreen(
                                            myPageViewModel = myPageViewModel,
                                            routeId = routeId,
                                            onBack = { currentScreen = "MyPage" },
                                            onCreateGroup = { choice -> openCreateGroup(choice, returnTo = "saved_detail") },
                                            onRun = { route ->
                                                runViewModel.start(
                                                    RunTarget(
                                                        lines = route.route,
                                                        distanceMeters = route.distance,
                                                        routeId = route.id,
                                                        startName = route.startName
                                                    )
                                                )
                                                runReturnScreen = "saved_detail"
                                                currentScreen = "run_navigation"
                                            }
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
                        LOGIN_PROMPT_SHARE_ROUTE -> R.string.login_prompt_share_route
                        LOGIN_PROMPT_SAVE_SHARED -> R.string.login_prompt_save_shared
                        LOGIN_PROMPT_REVIEW -> R.string.login_prompt_review
                        LOGIN_PROMPT_GROUP, LOGIN_PROMPT_GROUP_DETAIL, LOGIN_PROMPT_GROUP_SHARED -> R.string.login_prompt_group
                        LOGIN_PROMPT_BOARD, LOGIN_PROMPT_POST -> R.string.login_prompt_board
                        LOGIN_PROMPT_FOLLOW -> R.string.login_prompt_follow
                        else -> R.string.login_prompt_trial_used
                    },
                    onLogIn = {
                        loginPrompt = null
                        when (reason) {
                            LOGIN_PROMPT_SAVE -> openAuth("login", returnTo = "route_result", save = true)
                            LOGIN_PROMPT_SHARE_ROUTE -> openAuth("login", returnTo = "route_result", save = false)
                            LOGIN_PROMPT_SAVE_SHARED, LOGIN_PROMPT_REVIEW, LOGIN_PROMPT_GROUP_SHARED ->
                                openAuth("login", returnTo = "community_detail", save = false)
                            LOGIN_PROMPT_GROUP, LOGIN_PROMPT_BOARD -> openAuth("login", returnTo = "community", save = false)
                            LOGIN_PROMPT_POST -> openAuth("login", returnTo = "post_detail", save = false)
                            LOGIN_PROMPT_FOLLOW -> openAuth("login", returnTo = "user_profile", save = false)
                            LOGIN_PROMPT_GROUP_DETAIL -> openAuth("login", returnTo = "group_detail", save = false)
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
private const val LOGIN_PROMPT_GROUP = "group"
private const val LOGIN_PROMPT_GROUP_DETAIL = "group_detail"
private const val LOGIN_PROMPT_GROUP_SHARED = "group_shared"
private const val LOGIN_PROMPT_BOARD = "board"
private const val LOGIN_PROMPT_POST = "post"
private const val LOGIN_PROMPT_SHARE_ROUTE = "share_route"
private const val LOGIN_PROMPT_FOLLOW = "follow"

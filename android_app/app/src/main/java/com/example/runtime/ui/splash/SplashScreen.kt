package com.example.runtime.ui.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R
import com.example.runtime.ui.auth.AuthViewModel
import kotlinx.coroutines.delay

// 2. Splash Screen (화면 2)
@Composable
fun SplashScreen(authViewModel: AuthViewModel, onNext: (Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000) // 2초 후 자동으로 로그인 화면 이동
        authViewModel.restoreSession(onNext)
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_name), fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator()
    }
}

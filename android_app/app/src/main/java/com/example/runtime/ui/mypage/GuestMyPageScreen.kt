package com.example.runtime.ui.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runtime.R

@Composable
fun GuestMyPageScreen(onLogIn: () -> Unit, onSignUp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_account_circle),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.guest_my_page_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.guest_my_page_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogIn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_log_in))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_sign_up))
        }
    }
}

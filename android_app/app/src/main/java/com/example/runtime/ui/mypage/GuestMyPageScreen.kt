package com.example.runtime.ui.mypage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R
import com.example.runtime.ui.common.AccentButton
import com.example.runtime.ui.common.EmptyState
import com.example.runtime.ui.common.PreferenceButtons
import com.example.runtime.ui.theme.Spacing

@Composable
fun GuestMyPageScreen(onLogIn: () -> Unit, onSignUp: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        EmptyState(
            icon = R.drawable.ic_bookmark,
            title = stringResource(R.string.guest_my_page_title),
            message = stringResource(R.string.guest_my_page_message),
            modifier = Modifier.align(Alignment.Center)
        ) {
            AccentButton(
                text = stringResource(R.string.action_log_in),
                onClick = onLogIn,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.s))
            OutlinedButton(
                onClick = onSignUp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.action_sign_up), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s)) {
            PreferenceButtons()
        }
    }
}

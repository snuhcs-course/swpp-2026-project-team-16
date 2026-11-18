package com.example.runtime.ui.notifications

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.runtime.R

@Composable
fun NotificationBell(unreadCount: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) Badge { Text(unreadCount.coerceAtMost(MAX_BADGE_COUNT).toString()) }
            }
        ) {
            Icon(painterResource(R.drawable.ic_notifications), contentDescription = stringResource(R.string.notifications_title))
        }
    }
}

private const val MAX_BADGE_COUNT = 99

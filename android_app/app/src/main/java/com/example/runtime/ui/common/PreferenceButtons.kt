package com.example.runtime.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.runtime.R
import com.example.runtime.ui.theme.ThemeMenu

@Composable
fun PreferenceButtons() {
    var showLanguageMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    Row {
        Box {
            IconButton(onClick = { showThemeMenu = true }) {
                Icon(painterResource(R.drawable.ic_dark_mode), contentDescription = stringResource(R.string.theme))
            }
            ThemeMenu(expanded = showThemeMenu, onDismiss = { showThemeMenu = false })
        }
        Box {
            IconButton(onClick = { showLanguageMenu = true }) {
                Icon(painterResource(R.drawable.ic_language), contentDescription = stringResource(R.string.language))
            }
            LanguageMenu(expanded = showLanguageMenu, onDismiss = { showLanguageMenu = false })
        }
    }
}

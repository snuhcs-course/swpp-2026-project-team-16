package com.example.runtime.ui.common

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runtime.R

enum class AppLanguage(val tag: String?, @StringRes val label: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    KOREAN("ko", R.string.language_korean),
}

fun Context.currentAppLanguage(): AppLanguage {
    val locales = getSystemService(LocaleManager::class.java).applicationLocales
    if (locales.isEmpty) return AppLanguage.SYSTEM
    return AppLanguage.entries.firstOrNull { it.tag == locales[0].language } ?: AppLanguage.SYSTEM
}

fun Context.setAppLanguage(language: AppLanguage) {
    getSystemService(LocaleManager::class.java).applicationLocales =
        language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
}

@Composable
fun LanguageMenu(expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = context.currentAppLanguage()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.language),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AppLanguage.entries.forEach { language ->
            DropdownMenuItem(
                text = { Text(stringResource(language.label)) },
                leadingIcon = { RadioButton(selected = language == current, onClick = null) },
                onClick = {
                    onDismiss()
                    if (language != current) context.setAppLanguage(language)
                }
            )
        }
    }
}

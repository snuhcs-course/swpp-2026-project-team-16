package com.example.runtime.ui.common

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

fun doneKeyboardOptions(keyboardType: KeyboardType = KeyboardType.Unspecified) =
    KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done)

@Composable
fun hideKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        KeyboardActions(onDone = {
            keyboard?.hide()
            focusManager.clearFocus()
        })
    }
}

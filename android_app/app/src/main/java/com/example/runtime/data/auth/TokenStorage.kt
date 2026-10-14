package com.example.runtime.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth")

class TokenStorage(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")

    suspend fun load(): String? = context.authDataStore.data.first()[tokenKey]

    suspend fun save(token: String) {
        context.authDataStore.edit { it[tokenKey] = token }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.remove(tokenKey) }
    }
}

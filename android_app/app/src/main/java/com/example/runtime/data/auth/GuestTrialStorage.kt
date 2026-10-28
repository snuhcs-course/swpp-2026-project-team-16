package com.example.runtime.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.guestDataStore by preferencesDataStore(name = "guest")

class GuestTrialStorage(private val context: Context) {

    private val trialUsedKey = booleanPreferencesKey("trial_used")

    suspend fun isTrialUsed(): Boolean = context.guestDataStore.data.first()[trialUsedKey] ?: false

    suspend fun markTrialUsed() {
        context.guestDataStore.edit { it[trialUsedKey] = true }
    }
}

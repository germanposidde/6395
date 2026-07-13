package com.kmp.hook.elite.localdata

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kmp.hook.elite.util.tokenToSave
import kotlinx.coroutines.flow.first

private val Context.saveDataStore by preferencesDataStore(name = "ha")

object SaveManager {
    private val KEY_STRING = stringPreferencesKey("nmd")

    suspend fun safeSave(context: Context, value: String) {
        val dataStore = context.saveDataStore

        val existing = dataStore.data.first()[KEY_STRING]
        if (existing != null) {
            throw IllegalStateException("Value already saved: $existing")
        }

        tokenToSave()
        dataStore.edit { prefs ->
            prefs[KEY_STRING] = value
        }
    }

    suspend fun getData(context: Context): String {
        val dataStore = context.saveDataStore
        return dataStore.data.first()[KEY_STRING] ?: ""
    }
}
